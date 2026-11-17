package com.menglimi.chaoxing

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request
import java.net.URI

/** 学习通作业只读请求。传输层负责逐 URL Cookie 与会话隔离，并须关闭自动重定向。 */
class ChxApi(
    private val isAdditionalLoginUrl: (String) -> Boolean = { false },
    private val execute: suspend (Request) -> ChxResponse,
) {

    /** 与课程展示使用相同的发现与 HTML 校验；任务点统计不能证明没有作业。 */
    suspend fun courses(): List<ChxWorkCourse> = withContext(Dispatchers.IO) {
        ChxCourseApi.discoverWith(isAdditionalLoginUrl, execute).map {
            ChxWorkCourse(it.courseId.toLong(), it.clazzId.toLong(), it.curPersonId.toLong(), it.name, it.url)
        }
    }

    suspend fun courseTabs(course: ChxWorkCourse): ChxWorkTabs = withContext(Dispatchers.IO) {
        val trusted = ChxCourseApi.trustedCourseUrl(course.url, course.classId.toString(),
            course.courseId.toString(), course.cpi.toString())
            ?: throw ChxCourseReadException("课程入口无法识别，请在学习通网页查看。")
        val page = readPage(trusted, ChxSso.HOME, "课程页", ::isCoursePage)
        ChxWorkParser.readCourseTabs(page.body, course, page.url.toString())
    }

    suspend fun workList(course: ChxWorkCourse, tabs: ChxWorkTabs): List<ChxWorkItem> = withContext(Dispatchers.IO) {
        val requestUrl = workListUrl(course, tabs)
        val page = readPage(requestUrl.toString(), COURSE_PAGE, "作业列表", ::isWorkList)
        ChxWorkParser.parseWorkList(page.body, course, page.url.toString())
    }

    suspend fun workDetailHtml(detailUrl: String): String = withContext(Dispatchers.IO) {
        val url = trustedUrl(detailUrl)?.takeIf(::isWorkDetail)
            ?: throw ChxCourseReadException("作业详情地址无法识别，请在学习通网页查看。")
        readPage(url.toString(), null, "作业详情", ::isWorkDetail).body
    }

    private data class Page(val url: HttpUrl, val body: String)

    /** 不启用全局跟随：逐跳校验目标，再由传输层为新 URL 读取 Cookie。 */
    private suspend fun readPage(
        initialUrl: String,
        referer: String?,
        stage: String,
        initialPage: (HttpUrl) -> Boolean,
    ): Page {
        var url = trustedUrl(initialUrl)?.takeIf(initialPage)
            ?: throw ChxCourseReadException("$stage 地址无法识别，请在学习通网页查看。")
        val visited = mutableSetOf<String>()
        repeat(5) {
            currentCoroutineContext().ensureActive()
            if (!visited.add(url.toString())) throw ChxCourseReadException("$stage 出现循环跳转，请在网页查看。")
            val request = Request.Builder().url(url).header("User-Agent", ChxCourseApi.USER_AGENT)
                .apply { if (referer != null) header("Referer", referer) }.get().build()
            val response = ChxCourseApi.executeChecked(execute, request, "$stage 读取失败")
            if (response.status in setOf(301, 302, 303, 307, 308)) {
                if (ChxCourseApi.isTrustedLoginRedirect(url, response.location, isAdditionalLoginUrl)) {
                    throw ChxAuthFailed(401, "$stage 要求重新登录学习通，请在网页完成认证。")
                }
                // 入口路径可固定，服务端落地路径会变；逐跳限制主机，正文再校验课程身份和结构。
                val next = redirectTarget(url, response.location)
                    ?: throw ChxCourseReadException("$stage 暂未支持该跳转（HTTP ${response.status}；目标 ${ChxReadDiagnostics.route(url, response.location)}），请在网页查看。")
                if (ChxCourseApi.isTrustedLoginRedirect(next, next.toString(), isAdditionalLoginUrl)) {
                    throw ChxAuthFailed(401, "$stage 要求重新登录学习通，请在网页完成认证。")
                }
                url = next
            } else {
                return Page(url, ChxCourseApi.requireBody(response, url, "$stage 读取失败", isAdditionalLoginUrl))
            }
        }
        throw ChxCourseReadException("$stage 跳转次数过多，请在网页查看。")
    }

    companion object {
        private const val COURSE_PAGE = "https://mooc2-ans.chaoxing.com/mooc2-ans/mycourse/stu"

        /** 仅课程服务的两个精确主机；不放行任意超星子域，Cookie 仍由会话层逐 URL 读取。 */
        fun isAllowedUrl(url: HttpUrl): Boolean =
            url.scheme == "https" && url.port == 443 && url.username.isEmpty() && url.password.isEmpty() &&
                url.fragment == null && url.host in setOf("mooc1.chaoxing.com", "mooc2-ans.chaoxing.com")

        private fun isCoursePage(url: HttpUrl): Boolean =
            (url.host == "mooc1.chaoxing.com" && url.encodedPath == "/visit/stucoursemiddle") ||
                (url.host == "mooc2-ans.chaoxing.com" && url.encodedPath == "/mooc2-ans/mycourse/stu")

        private fun isWorkList(url: HttpUrl): Boolean = url.host == "mooc1.chaoxing.com" &&
            url.encodedPath in setOf("/mooc2/work/list", "/mooc-ans/mooc2/work/list")

        internal fun isWorkDetail(url: HttpUrl): Boolean = url.host == "mooc1.chaoxing.com" &&
            url.encodedPath in setOf("/mooc2/work/task", "/mooc-ans/mooc2/work/task")

        /** goTask 使用作业模块相对路径 work/task，不能再拼到当前 work/ 目录下。 */
        internal fun workDetailTarget(base: HttpUrl, value: String): HttpUrl? {
            val reference = runCatching { URI(value) }.getOrNull() ?: return null
            val target = if (reference.scheme == null && reference.rawAuthority == null &&
                reference.rawPath == "work/task" && base.host == "mooc1.chaoxing.com" &&
                base.encodedPath in setOf("/mooc2/work/list", "/mooc-ans/mooc2/work/list",
                    "/mooc2/work/list.html", "/mooc-ans/mooc2/work/list.html")) {
                base.resolve("../") ?: return null
            } else base
            return redirectTarget(target, value)?.takeIf(::isWorkDetail)
        }

        /** URI 检查避免 HttpUrl 规范化掩盖用户信息、反斜线、路径穿越等非协议地址。 */
        internal fun trustedUrl(value: String): HttpUrl? {
            if (value.any { it.code <= 32 || it.code == 127 } || '\\' in value) return null
            val uri = try { URI(value) } catch (_: Exception) { return null }
            if (uri.scheme != "https" || uri.userInfo != null || uri.port !in setOf(-1, 443) ||
                uri.rawFragment != null || uri.normalize().rawPath != uri.rawPath) return null
            val parsed = value.toHttpUrlOrNull() ?: return null
            return parsed.takeIf { it.encodedPath == uri.rawPath && isAllowedUrl(it) }
        }

        internal fun redirectTarget(base: HttpUrl, location: String?): HttpUrl? {
            if (location.isNullOrBlank() || location.any { it.code <= 32 || it.code == 127 } || '\\' in location) return null
            val target = try { URI(location) } catch (_: Exception) { return null }
            if (target.normalize().rawPath != target.rawPath) return null
            val resolved = try { URI(base.toString()).resolve(target) } catch (_: Exception) { return null }
            // 可信课程服务的 HTTP Location 只尝试 HTTPS；不向 HTTP 发 Cookie，也不降级重试。
            if (resolved.scheme == "http") {
                if (resolved.port !in setOf(-1, 80) || resolved.userInfo != null || resolved.rawFragment != null) return null
                val parsed = resolved.toString().toHttpUrlOrNull() ?: return null
                if (parsed.encodedPath != resolved.rawPath) return null
                val secure = parsed.newBuilder().scheme("https").port(443).build()
                return trustedUrl(secure.toString())
            }
            return trustedUrl(resolved.toString())
        }

        internal fun workListUrl(course: ChxWorkCourse, tabs: ChxWorkTabs): HttpUrl {
            if (course.courseId <= 0 || course.classId <= 0 || course.cpi <= 0 ||
                tabs.enc.isBlank() || tabs.workEnc.isBlank() || tabs.t.isBlank()) {
                throw ChxCourseReadException("课程页缺少作业查询参数，请在学习通网页查看。")
            }
            val base = tabs.moocDomain.toHttpUrlOrNull()
            if (base == null || base.scheme != "https" || base.host != "mooc1.chaoxing.com" || base.port != 443 ||
                base.username.isNotEmpty() || base.password.isNotEmpty() || base.query != null || base.fragment != null ||
                base.encodedPath !in setOf("/", "/mooc-ans", "/mooc-ans/")) {
                throw ChxCourseReadException("课程页的作业服务地址无法识别，请在学习通网页查看。")
            }
            val raw = tabs.moocDomain.trimEnd('/') + "/mooc2/work/list"
            val url = trustedUrl(raw)?.takeIf(::isWorkList)
                ?: throw ChxCourseReadException("课程页的作业服务地址无法识别，请在学习通网页查看。")
            return url.newBuilder()
                .addQueryParameter("courseId", course.courseId.toString())
                .addQueryParameter("classId", course.classId.toString())
                .addQueryParameter("cpi", course.cpi.toString())
                .addQueryParameter("ut", "s").addQueryParameter("t", tabs.t)
                .addQueryParameter("stuenc", tabs.enc).addQueryParameter("enc", tabs.workEnc)
                .build()
        }

        /** 纯判定便于回归：200/非登录 HTML 本身不是有效会话证据。 */
        fun sessionEvidence(
            status: Int,
            url: String,
            body: String?,
            location: String? = null,
            isAdditionalLoginUrl: (String) -> Boolean = { false },
        ): Boolean? {
            val parsed = url.toHttpUrlOrNull() ?: return null
            val home = ChxSso.HOME.toHttpUrlOrNull() ?: return null
            if (parsed.scheme != "https" || parsed.host != home.host || parsed.port != 443 ||
                parsed.encodedPath != home.encodedPath
            ) return null
            if (status == 401) return false
            if (status in 300..399) {
                return if (ChxCourseApi.isTrustedLoginRedirect(parsed, location, isAdditionalLoginUrl)) false else null
            }
            if (status !in 200..299 || body.isNullOrBlank()) return null
            val markup = body.replace(Regex("<!--.*?-->", RegexOption.DOT_MATCHES_ALL), "")
                .replace(Regex("<(script|style)\\b[^>]*>.*?</\\1\\s*>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)), "")
            val inputs = Regex("<input\\b[^>]*>", RegexOption.IGNORE_CASE).findAll(markup)
                .map { attributes(it.value) }.toList()
            val loginForm = inputs.any { it["type"].equals("password", ignoreCase = true) } &&
                (markup.contains("用户登录") || markup.contains("扫码登录") || markup.contains("统一身份认证"))
            if (loginForm) return false
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
}
