package com.menglimi.gdust.v2.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** 学习通只读探活；不扩展尚无协议证据的作业/考试端点。 */
class ChxApi {
    private val ua =
        "Mozilla/5.0 (Linux; Android 13; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"

    // 不把手工 Cookie 请求头跟随跳转发给其它主机；已知登录跳转直接判失效，其余保留未知。
    private val client = OkHttpClient.Builder()
        .followRedirects(false)
        .followSslRedirects(false)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .callTimeout(30, TimeUnit.SECONDS)
        .build()

    /** true = 已有肯定证据；false = 明确未登录；null = 无法确认，不应清理候选会话。 */
    suspend fun probe(cookie: String): Boolean? = withContext(Dispatchers.IO) {
        if (!ChxSso.hasSessionCookie(cookie)) return@withContext false
        try {
            val request = Request.Builder()
                .url(ChxSso.HOME)
                .header("User-Agent", ua)
                .header("Cookie", cookie)
                .get()
                .build()
            client.newCall(request).readProbe()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            currentCoroutineContext().ensureActive()
            null
        }
    }

    // 读取响应也在 OkHttp 回调线程上完成，取消时关闭请求（包括正在读取的 body）。
    private suspend fun Call.readProbe(): Boolean? = suspendCancellableCoroutine { continuation ->
        continuation.invokeOnCancellation { cancel() }
        enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (continuation.isActive) continuation.resumeWithException(e)
            }

            override fun onResponse(call: Call, response: Response) {
                try {
                    val result = response.use {
                        val body = if (it.isSuccessful) it.peekBody(512L * 1024).string() else null
                        sessionEvidence(it.code, it.request.url.toString(), body, it.header("Location"))
                    }
                    if (continuation.isActive) continuation.resume(result)
                } catch (e: Exception) {
                    if (continuation.isActive) continuation.resumeWithException(e)
                }
            }
        })
    }

    companion object {
        /** 纯判定便于回归：200/非登录 HTML 本身不是有效会话证据。 */
        internal fun sessionEvidence(status: Int, url: String, body: String?, location: String? = null): Boolean? {
            val parsed = url.toHttpUrlOrNull() ?: return null
            val home = ChxSso.HOME.toHttpUrlOrNull() ?: return null
            if (parsed.scheme != "https" || parsed.host != home.host || parsed.port != 443 ||
                parsed.encodedPath != home.encodedPath
            ) return null
            if (status == 401) return false
            if (status in 300..399) {
                val target = location?.let(parsed::resolve)?.toString() ?: return null
                return if (ChxSso.isLoginUrl(target)) false else null
            }
            if (status !in 200..299 || body.isNullOrBlank()) return null
            val markup = body.replace(Regex("<!--.*?-->", RegexOption.DOT_MATCHES_ALL), "")
                .replace(Regex("<(script|style)\\b[^>]*>.*?</\\1\\s*>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)), "")
            val inputs = Regex("<input\\b[^>]*>", RegexOption.IGNORE_CASE).findAll(markup)
                .map { attributes(it.value) }.toList()
            // 明确的交互式登录表单才判失效，正文提到“登录”不够。
            val loginForm = inputs.any { it["type"].equals("password", ignoreCase = true) } &&
                (markup.contains("用户登录") || markup.contains("扫码登录") || markup.contains("统一身份认证"))
            if (loginForm) return false
            // 本地已有 HAR 的 interaction 页：服务端生成的 checkUserToken + 学生页结构。
            // 不解析/记录身份值；模板占位、空 token、维护页和未知页面全部保持未知。
            val token = inputs.firstOrNull { it["id"] == "checkUserToken" }?.get("value").orEmpty()
            val positive = Regex("[a-fA-F0-9]{32}").matches(token) && token.any { it != '0' } &&
                inputs.any { it["id"] == "stuPageHeader" } &&
                inputs.any { it["id"] == "loadStuJobInfo" }
            return if (positive) true else null
        }

        private fun attributes(tag: String): Map<String, String> =
            Regex("([A-Za-z][A-Za-z0-9_-]*)\\s*=\\s*(?:\"([^\"]*)\"|'([^']*)'|([^\\s>]+))")
                .findAll(tag).associate { match ->
                    match.groupValues[1].lowercase(java.util.Locale.ROOT) to
                        match.groups.drop(2).firstNotNullOf { it?.value }
                }
    }

    // ── 作业同步（端点与结构全部来自真实抓包，见 docs/EXTENDING.md §3.4） ──

    private fun getHtml(cookie: String, url: String, referer: String? = null): String? =
        runCatching {
            val b = Request.Builder().url(url).header("User-Agent", ua).header("Cookie", cookie)
            if (referer != null) b.header("Referer", referer)
            client.newCall(b.get().build()).execute().use { resp ->
                if (resp.isSuccessful) resp.body?.string() else null
            }
        }.getOrNull()

    /**
     * 学校课程列表（POST，表单字段为固定值，来自抓包；Referer 为学习通首页）。
     * 返回的 HTML 里每门课一个 `visit/stucoursemiddle?courseid&clazzid&cpi` 链接。
     */
    suspend fun courseListHtml(cookie: String): String? = withContext(Dispatchers.IO) {
        val form = FormBody.Builder()
            .add("courseType", "1").add("courseFolderId", "0").add("query", "")
            .add("pageHeader", "-1").add("single", "0").add("superstarClass", "0")
            .add("isFirefly", "0").add("courseLevelLabelId", "0").add("fid", "").add("from", "")
            .build()
        val req = Request.Builder()
            .url("https://mooc2-ans.chaoxing.com/mooc2-ans/visit/courselistdata")
            .header("User-Agent", ua)
            .header("Cookie", cookie)
            .header("Referer", "https://mooc2-ans.chaoxing.com/mooc2-ans/visit/interaction")
            .post(form)
            .build()
        runCatching {
            client.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) resp.body?.string() else null
            }
        }.getOrNull()
    }

    /**
     * 批量作业计数：一次请求带全部 `clazzId_cpi` 对（逗号 %2C 分隔），
     * 返回每班的 jobCount/jobFinishCount/jobRate——用来预过滤，避免全量课程逐个拉作业列表。
     */
    suspend fun jobInfoJson(cookie: String, clazzPersonStr: String): String? =
        withContext(Dispatchers.IO) {
            getHtml(
                cookie,
                "https://mooc2-ans.chaoxing.com/mooc2-ans/mycourse/stu-job-info?clazzPersonStr=$clazzPersonStr",
            )
        }

    /**
     * 课程页：跟随 stucoursemiddle 的 302 落到 mycourse/stu，
     * 隐藏域里带 enc / workEnc / examEnc / t / userId / moocDomainName。
     */
    suspend fun courseTabsHtml(cookie: String, courseId: Long, classId: Long, cpi: Long): String? =
        withContext(Dispatchers.IO) {
            getHtml(
                cookie,
                "https://mooc1.chaoxing.com/visit/stucoursemiddle" +
                    "?courseid=$courseId&clazzid=$classId&cpi=$cpi&ismooc2=1&v=2",
            )
        }

    /** 作业详情页（列表里每条 li[data] 就是它的地址）：用于读绝对截止时间。 */
    suspend fun workDetailHtml(cookie: String, detailUrl: String): String? =
        withContext(Dispatchers.IO) { getHtml(cookie, detailUrl) }

    /** 作业列表页：参数全部来自课程页隐藏域（ut=s、t 为页面时间戳）。 */
    suspend fun workListHtml(
        cookie: String,
        moocDomain: String,
        courseId: Long,
        classId: Long,
        cpi: Long,
        enc: String,
        workEnc: String,
        t: String,
    ): String? = withContext(Dispatchers.IO) {
        getHtml(
            cookie,
            "$moocDomain/mooc2/work/list" +
                "?courseId=$courseId&classId=$classId&cpi=$cpi&ut=s&t=$t&stuenc=$enc&enc=$workEnc",
            "https://mooc2-ans.chaoxing.com/mooc2-ans/mycourse/stu",
        )
    }
}
