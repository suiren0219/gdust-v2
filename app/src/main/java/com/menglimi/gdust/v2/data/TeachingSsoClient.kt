package com.menglimi.gdust.v2.data

import com.google.gson.JsonParser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.CookieJar
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URI
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * 统一身份认证 ticket → 正方教务会话（JSESSIONID）→ 整学期 kbList。
 * 全程 OkHttp，不依赖 WebView。
 *
 * Cookie 走外部传入的 CookieJar（通常是 PersistentCookieJar）：
 * 登录一次后会话落盘，之后同步、续租、定时任务都用同一份会话。
 */
class TeachingSsoClient {

    private val ua =
        "Mozilla/5.0 (Linux; Android 13; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"

    /** 依次尝试：校园网内网 → 新教务公网 → WebVPN（后者在校园网 IP 会被拒）。 */
    private val bases = listOf(
        "http://172.16.254.1",
        "http://gkjx.gdust.edu.cn",
        "https://webvpn.gdust.edu.cn/http/77726476706e69737468656265737421a1a013d2766626022b5cc7fd",
    )

    private fun client(
        jar: CookieJar,
        followRedirects: Boolean = true,
        connectSec: Long = 15,
        readSec: Long = 30,
    ) = OkHttpClient.Builder()
        .cookieJar(jar)
        .followRedirects(followRedirects)
        .followSslRedirects(followRedirects)
        .connectTimeout(connectSec, TimeUnit.SECONDS)
        .readTimeout(readSec, TimeUnit.SECONDS)
        .build()

    /**
     * 只用 ticket 建立教务会话（不拉课表）。
     * 必须在换门户 TOKEN **之前** 调用：CAS ticket 一次性，先给教务留着更容易打通。
     * 返回成功建立会话的入口 base；全部失败返回 null。
     */
    suspend fun openSession(ticket: String, jar: CookieJar): String? = withContext(Dispatchers.IO) {
        if (ticket.isBlank()) return@withContext null
        for (base in bases) {
            val result = runCatching { exchangeTicket(ticket, base, jar, connectSec = 8, readSec = 12) }
                .onFailure { if (it is CancellationException) throw it }
            if (result.isSuccess) return@withContext base
        }
        null
    }

    /** 用 CAS ticket 换会话并拉课表；成功后 Cookie 已写入 jar，可持久复用。 */
    suspend fun loginAndFetch(
        ticket: String,
        calendar: CalendarInfo,
        jar: CookieJar,
    ): List<CourseBlock> = withContext(Dispatchers.IO) {
        require(ticket.isNotBlank()) { "登录票据为空，请重新登录" }
        var lastError: Throwable? = null
        for (base in bases) {
            val r = runCatching { exchangeTicket(ticket, base, jar) }
                .onFailure { if (it is CancellationException) throw it }
            if (r.isSuccess) {
                val fetched = runCatching { fetchKbList(base, calendar, jar) }
                    .onFailure { if (it is CancellationException) throw it }
                if (fetched.isSuccess) return@withContext fetched.getOrThrow()
                lastError = fetched.exceptionOrNull()
            } else {
                lastError = r.exceptionOrNull()
            }
        }
        throw IllegalStateException(
            "学校未用登录票据放行教务会话（${lastError?.message?.take(80) ?: "无响应"}）。" +
                "这不是账号密码错误；可稍后重试。",
            lastError,
        )
    }

    /** 复用已持久化的 Cookie 直接拉学期课表（不重登）。 */
    suspend fun fetchWithCookies(calendar: CalendarInfo, jar: CookieJar): List<CourseBlock> =
        withContext(Dispatchers.IO) {
            var lastError: Throwable? = null
            for (base in bases) {
                val host = hostOf(base) ?: continue
                if (!hasCookiesFor(jar, host)) continue
                val r = runCatching { fetchKbList(base, calendar, jar) }
                    .onFailure { if (it is CancellationException) throw it }
                if (r.isSuccess) return@withContext r.getOrThrow()
                lastError = r.exceptionOrNull()
            }
            throw IllegalStateException(
                "教务课表读取失败：${lastError?.message?.take(80) ?: "本机无可用会话"}",
                lastError,
            )
        }

    /** 用网页登录（WebView）留下的 Cookie 拉课表；Cookie 只发给当初登录的那个域名。 */
    suspend fun fetchWithCookieHeader(
        calendar: CalendarInfo,
        pageUrl: String,
        cookieHeader: String,
    ): List<CourseBlock> = withContext(Dispatchers.IO) {
        require(cookieHeader.isNotBlank()) { "网页登录会话为空" }
        val pageHost = hostOf(pageUrl) ?: error("网页登录地址无效")
        // 只选与登录页同域的入口，避免把 Cookie 发给其他主机
        val base = bases.firstOrNull { hostOf(it) == pageHost }
            ?: if (pageHost == "webvpn.gdust.edu.cn") bases.last() else null
            ?: error("不认识的教务域名：$pageHost")
        runCatching { postKbList(base, calendar, cookieHeader) }
            .getOrElse { e ->
                if (e is CancellationException) throw e
                throw IllegalStateException(
                    "网页登录会话已失效（${e.message?.take(80) ?: "无响应"}），请重新网页登录一次。",
                    e,
                )
            }
    }

    private fun postKbList(base: String, calendar: CalendarInfo, cookieHeader: String): List<CourseBlock> {
        val endpoint = "$base/kbcx/xskbcx_cxXsgrkb.html?gnmkdm=N2151"
        val form = FormBody.Builder()
            .add("xnm", calendar.year).add("xqm", calendar.semester)
            .add("kzlx", "ck").add("xsdm", "").add("kclbdm", "")
            .build()
        val req = Request.Builder().url(endpoint)
            .header("User-Agent", ua)
            .header("Cookie", cookieHeader)
            .header("X-Requested-With", "XMLHttpRequest")
            .post(form).build()
        OkHttpClient.Builder()
            .followRedirects(false).followSslRedirects(false)
            .connectTimeout(15, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS)
            .build().newCall(req).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            if (resp.code in 300..399 || resp.code == 401 || resp.code == 901) {
                error("网页登录会话失效 HTTP ${resp.code} @ ${resp.request.url.host}")
            }
            if (!resp.isSuccessful) error("教务 HTTP ${resp.code} @ ${resp.request.url.host}")
            val root = runCatching { JsonParser.parseString(body) }.getOrElse {
                error("教务未返回课表 JSON @ ${resp.request.url.host}")
            }
            if (!root.isJsonObject || !root.asJsonObject.has("kbList")) {
                error("教务响应缺少 kbList @ ${resp.request.url.host}")
            }
            return ZfScheduleParser.parse(root.toString(), calendar)
        }
    }

    /**
     * 轻量探活：判断会话是否仍有效，供续租与定时任务决定是否要重登。
     * 任一入口有效即算有效；只有全部入口都被明确拒绝（302/401/901）才判失效，
     * 网络/维护等未知状态保持会话（不误清）。
     */
    suspend fun probe(jar: CookieJar): Boolean = withContext(Dispatchers.IO) {
        var invalid = false
        for (base in bases) {
            val host = hostOf(base) ?: continue
            if (!hasCookiesFor(jar, host)) continue
            val code = runCatching {
                val req = Request.Builder()
                    .url("$base/kbcx/xskbcx_cxXsgrkb.html?gnmkdm=N2151")
                    .header("User-Agent", ua).get().build()
                client(jar, followRedirects = false).newCall(req).execute().use { resp -> resp.code }
            }.getOrElse { null }
            when (code) {
                null -> continue                          // 网络故障：换入口，不下结论
                200, 400, 405 -> return@withContext true  // 会话有效
                302, 401, 901 -> invalid = true           // 正方明确拒绝
                else -> continue                          // 维护页等异常响应，不下结论
            }
        }
        if (invalid) false else true
    }

    private fun exchangeTicket(
        ticket: String,
        base: String,
        jar: CookieJar,
        connectSec: Long = 15,
        readSec: Long = 30,
    ) {
        val ssoUrl = "$base/sso/lyiotlogin?ticket=${URLEncoder.encode(ticket, "UTF-8")}" +
            "&service=${URLEncoder.encode("$base/", "UTF-8")}"
        val req = Request.Builder().url(ssoUrl).header("User-Agent", ua).get().build()
        client(jar, connectSec = connectSec, readSec = readSec).newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) error("SSO HTTP ${resp.code} @ ${resp.request.url.host}")
            val finalUrl = resp.request.url.toString()
            if (finalUrl.contains("login_slogin") || finalUrl.contains("/cas/login") ||
                finalUrl.contains("lyuapServer/login")
            ) {
                error("票据未被教务接受 @ ${resp.request.url.host}")
            }
            val body = resp.body?.string().orEmpty()
            if (body.contains("拒绝登录") || body.contains("校园网")) {
                error("该入口拒绝校园网 IP @ ${resp.request.url.host}")
            }
        }
    }

    private fun fetchKbList(base: String, calendar: CalendarInfo, jar: CookieJar): List<CourseBlock> {
        val endpoint = "$base/kbcx/xskbcx_cxXsgrkb.html?gnmkdm=N2151"
        val form = FormBody.Builder()
            .add("xnm", calendar.year).add("xqm", calendar.semester)
            .add("kzlx", "ck").add("xsdm", "").add("kclbdm", "")
            .build()
        val req = Request.Builder().url(endpoint)
            .header("User-Agent", ua)
            .header("X-Requested-With", "XMLHttpRequest")
            .post(form).build()
        client(jar, followRedirects = false).newCall(req).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            if (resp.code in 300..399 || resp.code == 401 || resp.code == 901) {
                error("教务会话失效 HTTP ${resp.code} @ ${resp.request.url.host}")
            }
            if (!resp.isSuccessful) error("教务 HTTP ${resp.code} @ ${resp.request.url.host}")
            val trimmed = body.trimStart()
            fun htmlHint(): String? = when {
                body.contains("拒绝登录") || body.contains("校园网") -> "该入口拒绝校园网 IP"
                trimmed.startsWith("<") -> "该入口返回的是网页而不是课表（未放行教务接口）"
                else -> null
            }
            val root = runCatching { JsonParser.parseString(body) }.getOrElse {
                error("${htmlHint() ?: "教务未返回课表 JSON"} @ ${resp.request.url.host}")
            }
            if (!root.isJsonObject || !root.asJsonObject.has("kbList")) {
                error("${htmlHint() ?: "教务响应缺少 kbList"} @ ${resp.request.url.host}")
            }
            return ZfScheduleParser.parse(root.toString(), calendar)
        }
    }

    private fun hostOf(base: String): String? = runCatching { URI.create(base).host }.getOrNull()

    private fun hasCookiesFor(jar: CookieJar, host: String): Boolean {
        if (jar is PersistentCookieJar) return jar.hasSessionFor(host)
        return listOf("https", "http").any { scheme ->
            jar.loadForRequest(okhttp3.HttpUrl.Builder().scheme(scheme).host(host)
                .addPathSegments("kbcx/xskbcx_cxXsgrkb.html").build()).isNotEmpty()
        }
    }
}
