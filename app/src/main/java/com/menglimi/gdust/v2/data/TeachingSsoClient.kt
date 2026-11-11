package com.menglimi.gdust.v2.data

import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * 统一身份认证 ticket → 正方教务会话（JSESSIONID）→ 整学期 kbList。
 * 全程 OkHttp，不依赖 WebView；ticket 换会话这一跳若学校未放行会明确抛错，不静默降级。
 */
class TeachingSsoClient {

    private data class Session(val endpoint: String, val cookie: String)

    suspend fun loginAndFetch(ticket: String, calendar: CalendarInfo): List<CourseBlock> = withContext(Dispatchers.IO) {
        require(ticket.isNotBlank()) { "登录票据为空，请重新登录" }
        val jar = SimpleCookieJar()
        val http = OkHttpClient.Builder()
            .cookieJar(jar)
            .followRedirects(true)
            .followSslRedirects(true)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()

        // 依次试三条 SSO 入口：校园网内网 / 新教务公网 / WebVPN（后者在校园网 IP 会被拒）
        val ssoBases = listOf(
            "http://172.16.254.1",
            "http://gkjx.gdust.edu.cn",
            "https://webvpn.gdust.edu.cn/http/77726476706e69737468656265737421a1a013d2766626022b5cc7fd",
        )
        var lastError: Throwable? = null
        for (base in ssoBases) {
            val result = runCatching { exchange(http, jar, base, ticket, calendar) }
            if (result.isSuccess) return@withContext result.getOrThrow()
            lastError = result.exceptionOrNull()
        }
        throw IllegalStateException(
            "学校未用登录票据放行教务会话（${lastError?.message?.take(80) ?: "无响应"}）。" +
                "这不是账号密码错误；可稍后重试，或使用 App 内教务网页登录一次。",
            lastError,
        )
    }

    private fun exchange(
        http: OkHttpClient,
        jar: SimpleCookieJar,
        base: String,
        ticket: String,
        calendar: CalendarInfo,
    ): List<CourseBlock> {
        val service = "$base/"
        val ssoUrl = "$base/sso/lyiotlogin?ticket=${URLEncoder.encode(ticket, "UTF-8")}" +
            "&service=${URLEncoder.encode(service, "UTF-8")}"
        val ssoReq = Request.Builder().url(ssoUrl)
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 13)")
            .get().build()
        http.newCall(ssoReq).execute().use { resp ->
            if (!resp.isSuccessful) error("SSO HTTP ${resp.code} @ ${resp.request.url.host}")
            // 跳回登录页 = ticket 没被这一套教务系统接受
            val finalUrl = resp.request.url.toString()
            if (finalUrl.contains("login_slogin") || finalUrl.contains("/cas/login") || finalUrl.contains("lyuapServer/login")) {
                error("票据未被教务接受 @ ${resp.request.url.host}")
            }
        }
        val cookie = jar.all()
            .filter { it.name.equals("JSESSIONID", true) }
            .joinToString("; ") { "${it.name}=${it.value}" }
        if (cookie.isBlank()) error("未取得 JSESSIONID @ ${java.net.URI.create(base).host}")

        val endpoint = "$base/kbcx/xskbcx_cxXsgrkb.html?gnmkdm=N2151"
        val form = FormBody.Builder()
            .add("xnm", calendar.year).add("xqm", calendar.semester)
            .add("kzlx", "ck").add("xsdm", "").add("kclbdm", "")
            .build()
        val req = Request.Builder().url(endpoint)
            .header("Cookie", cookie)
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 13)")
            .header("X-Requested-With", "XMLHttpRequest")
            .post(form).build()
        // 课表请求不跟随跳转：跳登录页就是会话没建立，明确报错
        val noRedirect = http.newBuilder().followRedirects(false).build()
        noRedirect.newCall(req).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            if (resp.code in 300..399 || resp.code == 401 || resp.code == 901) {
                error("教务会话失效 HTTP ${resp.code} @ ${resp.request.url.host}")
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
}

/** 仅本次 SSO 流程用的内存 CookieJar，不落盘、不跨请求泄漏。 */
private class SimpleCookieJar : CookieJar {
    private val store = mutableMapOf<String, MutableList<Cookie>>()
    @Synchronized override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        val list = store.getOrPut(url.host) { mutableListOf() }
        cookies.forEach { c -> list.removeAll { it.name == c.name && it.path == c.path }; list.add(c) }
    }
    @Synchronized override fun loadForRequest(url: HttpUrl): List<Cookie> =
        store[url.host].orEmpty().filter { it.expiresAt >= System.currentTimeMillis() }
    @Synchronized fun all(): List<Cookie> = store.values.flatten()
}
