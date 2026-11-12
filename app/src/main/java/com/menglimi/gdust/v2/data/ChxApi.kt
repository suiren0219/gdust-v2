package com.menglimi.gdust.v2.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * 学习通只读入口。
 *
 * 目前只做一件事：用 CAS 单点后拿到的 Cookie 探活。
 * 作业/考试清单的接口与字段尚无 HAR 证据，按红线不在这里凭空写端点——
 * 上一个版本的通知监听负责「新作业提醒」，全量清单等抓到作业页请求后再补。
 */
class ChxApi {

    private val ua =
        "Mozilla/5.0 (Linux; Android 13; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"

    private fun client() = OkHttpClient.Builder()
        .followRedirects(true)
        .followSslRedirects(true)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    /**
     * 会话是否仍然有效：拉首页，如果被踢回登录页或响应体是登录表单，判失效。
     * 网络故障一律按「未知」处理，由调用方保留 Cookie，不轻易登出。
     */
    suspend fun probe(cookie: String): Boolean? = withContext(Dispatchers.IO) {
        if (cookie.isBlank()) return@withContext false
        val req = Request.Builder()
            .url(ChxSso.HOME)
            .header("User-Agent", ua)
            .header("Cookie", cookie)
            .get()
            .build()
        runCatching {
            client().newCall(req).execute().use { resp ->
                val finalUrl = resp.request.url.toString().lowercase()
                // 被重定向到 passport/login 一律视为失效
                if (finalUrl.contains("passport") || finalUrl.contains("login")) return@use false
                if (!resp.isSuccessful) return@use null
                val body = resp.body?.string().orEmpty()
                when {
                    body.isBlank() -> null
                    // 未登录时首页会渲染成登录引导页
                    body.contains("用户登录") || body.contains("扫码登录") -> false
                    else -> true
                }
            }
        }.getOrNull()
    }

    // ── 作业同步（端点与结构全部来自真实抓包，见 docs/EXTENDING.md §3.4） ──

    private fun getHtml(cookie: String, url: String, referer: String? = null): String? =
        runCatching {
            val b = Request.Builder().url(url).header("User-Agent", ua).header("Cookie", cookie)
            if (referer != null) b.header("Referer", referer)
            client().newCall(b.get().build()).execute().use { resp ->
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
            client().newCall(req).execute().use { resp ->
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
