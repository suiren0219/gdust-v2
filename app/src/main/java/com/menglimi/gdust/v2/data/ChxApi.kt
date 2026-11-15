package com.menglimi.gdust.v2.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request
import java.net.URI

/** 学习通作业只读请求。每个 URL 的实时 Cookie、响应 Cookie 与会话隔离由 Repository 提供。 */
class ChxApi internal constructor(private val execute: suspend (Request) -> SessionResponse) {
    internal constructor(http: SessionHttpClient) : this(http::execute)

    /** 与课程展示使用相同的发现与 HTML 校验；任务点统计不能证明没有作业。 */
    suspend fun courses(): List<ChxWorkCourse> = withContext(Dispatchers.IO) {
        ChxCourseApi.discoverWith(execute).map {
            ChxWorkCourse(it.courseId.toLong(), it.clazzId.toLong(), it.curPersonId.toLong(), it.name, it.url)
        }
    }

    suspend fun courseTabs(course: ChxWorkCourse): ChxWorkTabs = withContext(Dispatchers.IO) {
        val trusted = ChxCourseApi.trustedCourseUrl(course.url, course.classId.toString(),
            course.courseId.toString(), course.cpi.toString())
            ?: throw ChxCourseReadException("课程入口无法识别，请在学习通网页查看。")
        val html = readPage(trusted, ChxSso.HOME, "课程页", ::isCoursePage)
        ChxWorkParser.readCourseTabs(html, course)
    }

    suspend fun workList(course: ChxWorkCourse, tabs: ChxWorkTabs): List<ChxWorkItem> = withContext(Dispatchers.IO) {
        val requestUrl = workListUrl(course, tabs)
        val html = readPage(requestUrl.toString(), COURSE_PAGE, "作业列表", ::isWorkList)
        ChxWorkParser.parseWorkList(html, course)
    }

    suspend fun workDetailHtml(detailUrl: String): String = withContext(Dispatchers.IO) {
        val url = trustedUrl(detailUrl)?.takeIf(::isWorkDetail)
            ?: throw ChxCourseReadException("作业详情地址无法识别，请在学习通网页查看。")
        readPage(url.toString(), null, "作业详情", ::isWorkDetail)
    }

    /** 不启用全局跟随：逐跳校验目标，再由 SessionHttpClient 为新 URL 读取 Cookie。 */
    private suspend fun readPage(
        initialUrl: String,
        referer: String?,
        stage: String,
        allowedPage: (HttpUrl) -> Boolean,
    ): String {
        var url = trustedUrl(initialUrl)?.takeIf(allowedPage)
            ?: throw ChxCourseReadException("$stage 地址无法识别，请在学习通网页查看。")
        val visited = mutableSetOf<String>()
        repeat(5) {
            currentCoroutineContext().ensureActive()
            if (!visited.add(url.toString())) throw ChxCourseReadException("$stage 出现循环跳转，请在网页查看。")
            val request = Request.Builder().url(url).header("User-Agent", ChxCourseApi.USER_AGENT)
                .apply { if (referer != null) header("Referer", referer) }.get().build()
            val response = ChxCourseApi.executeChecked(execute, request, "$stage 读取失败")
            if (response.status in setOf(301, 302, 303, 307, 308)) {
                if (ChxCourseApi.isTrustedLoginRedirect(url, response.location)) {
                    throw AuthFailed(401, "$stage 要求重新登录学习通，请在网页完成认证。")
                }
                val next = redirectTarget(url, response.location)?.takeIf(allowedPage)
                    ?: throw ChxCourseReadException("$stage 暂未支持该跳转（HTTP ${response.status}；目标 ${ChxReadDiagnostics.route(url, response.location)}），请在网页查看。")
                url = next
            } else {
                return ChxCourseApi.requireBody(response, url, "$stage 读取失败")
            }
        }
        throw ChxCourseReadException("$stage 跳转次数过多，请在网页查看。")
    }

    companion object {
        private const val COURSE_PAGE = "https://mooc2-ans.chaoxing.com/mooc2-ans/mycourse/stu"

        internal fun isAllowedUrl(url: HttpUrl): Boolean = ChxCourseApi.isAllowedUrl(url) ||
            (url.scheme == "https" && url.port == 443 && url.username.isEmpty() && url.password.isEmpty() &&
                url.fragment == null && (isCoursePage(url) || isWorkList(url) || isWorkDetail(url)))

        private fun isCoursePage(url: HttpUrl): Boolean =
            (url.host == "mooc1.chaoxing.com" && url.encodedPath == "/visit/stucoursemiddle") ||
                (url.host == "mooc2-ans.chaoxing.com" && url.encodedPath == "/mooc2-ans/mycourse/stu")

        private fun isWorkList(url: HttpUrl): Boolean = url.host == "mooc1.chaoxing.com" &&
            url.encodedPath in setOf("/mooc2/work/list", "/mooc-ans/mooc2/work/list")

        private fun isWorkDetail(url: HttpUrl): Boolean = url.host == "mooc1.chaoxing.com" &&
            url.encodedPath == "/mooc-ans/mooc2/work/task"

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
            // 只升级已经支持且有 HTTPS 落地证据的课程/作业地址，不向 HTTP 发送 Cookie。
            if (resolved.scheme == "http") {
                if (resolved.port !in setOf(-1, 80) || resolved.userInfo != null || resolved.rawFragment != null) return null
                val parsed = resolved.toString().toHttpUrlOrNull() ?: return null
                if (parsed.encodedPath != resolved.rawPath) return null
                val secure = parsed.newBuilder().scheme("https").port(443).build()
                if (!isCoursePage(secure) && !isWorkList(secure) && !isWorkDetail(secure)) return null
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
