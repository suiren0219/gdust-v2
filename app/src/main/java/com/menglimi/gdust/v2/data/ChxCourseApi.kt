package com.menglimi.gdust.v2.data

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import java.net.URI

/** 只读取已核实的默认课程页与任务点统计；Cookie 和会话隔离由 Repository 提供。 */
class ChxCourseApi internal constructor(private val http: SessionHttpClient) {
    suspend fun load(): ChxCourseOverview = withContext(Dispatchers.IO) {
        loadWith(http::execute)
    }

    companion object {
        internal const val LIST_URL = "https://mooc2-ans.chaoxing.com/mooc2-ans/visit/courselistdata"
        internal const val PROGRESS_URL = "https://mooc2-ans.chaoxing.com/mooc2-ans/mycourse/stu-job-info"
        private const val COURSE_HOST = "mooc1.chaoxing.com"
        private const val COURSE_PATH = "/visit/stucoursemiddle"
        private const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 13; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
        private const val UNKNOWN_LIST = "未能识别当前课程页，请稍后重试或在学习通网页查看。"
        private const val UNKNOWN_PROGRESS = "任务点进度暂时无法读取，课程列表已保留。"

        internal fun isAllowedUrl(url: HttpUrl): Boolean =
            url.scheme == "https" && url.host == "mooc2-ans.chaoxing.com" && url.port == 443 &&
                url.username.isEmpty() && url.password.isEmpty() && url.fragment == null &&
                url.encodedPath in setOf(
                    "/mooc2-ans/visit/interaction",
                    "/mooc2-ans/visit/courselistdata",
                    "/mooc2-ans/mycourse/stu-job-info",
                )

        /** 同一编排用于真实客户端与虚构响应测试，测试不需要 Cookie、WebView 或网络。 */
        internal suspend fun loadWith(execute: suspend (Request) -> SessionResponse): ChxCourseOverview {
            val home = request(ChxSso.HOME).get().build()
            val homepage = executeChecked(execute, home, "学习通首页暂时无法读取，请稍后重试。")
            val homeBody = requireBody(homepage, home.url, "学习通首页暂时无法读取，请稍后重试。")
            when (ChxApi.sessionEvidence(homepage.status, ChxSso.HOME, homeBody, homepage.location)) {
                false -> throw AuthFailed(401, "学习通会话已失效，请重新登录。")
                null -> throw ChxCourseReadException("暂时无法确认学习通登录状态，请在学习通网页查看。")
                true -> Unit
            }
            val listRequest = courseListRequest()
            val listResponse = executeChecked(execute, listRequest, "课程页暂时无法读取，请稍后重试。")
            val courses = parseCourses(requireBody(listResponse, listRequest.url, "课程页暂时无法读取，请稍后重试。"))
            currentCoroutineContext().ensureActive()
            return try {
                val progressRequest = progressRequest(courses)
                val response = executeChecked(execute, progressRequest, UNKNOWN_PROGRESS)
                attachProgress(courses, requireBody(response, progressRequest.url, UNKNOWN_PROGRESS))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: AuthFailed) {
                currentCoroutineContext().ensureActive()
                ChxCourseOverview(courses, "学习通会话已失效，任务点进度未知，请重新登录。", progressAuthRequired = true)
            } catch (_: Exception) {
                currentCoroutineContext().ensureActive()
                ChxCourseOverview(courses, UNKNOWN_PROGRESS)
            }
        }

        internal fun courseListRequest(): Request = request(LIST_URL)
            .post(FormBody.Builder()
                .add("courseType", "1")
                .add("courseFolderId", "0")
                .add("query", "")
                .add("pageHeader", "-1")
                .add("single", "0")
                .add("superstarClass", "0")
                .add("isFirefly", "0")
                .add("courseLevelLabelId", "0")
                .add("fid", "")
                .add("from", "")
                .build())
            .build()

        internal fun progressRequest(courses: List<ChxCourse>): Request = request(
            PROGRESS_URL.toHttpUrl().newBuilder()
                .addQueryParameter("clazzPersonStr", courses.distinctBy { it.info }.joinToString(",") { it.info })
                .build().toString(),
        ).get().build()

        private fun request(url: String): Request.Builder = Request.Builder().url(url)
            .header("User-Agent", USER_AGENT)
            .header("Referer", ChxSso.HOME)

        private suspend fun executeChecked(
            execute: suspend (Request) -> SessionResponse,
            request: Request,
            error: String,
        ): SessionResponse {
            currentCoroutineContext().ensureActive()
            val response = try {
                execute(request)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (auth: AuthFailed) {
                throw auth
            } catch (_: Exception) {
                currentCoroutineContext().ensureActive()
                throw ChxCourseReadException(error)
            }
            currentCoroutineContext().ensureActive()
            return response
        }

        internal fun requireBody(response: SessionResponse, url: HttpUrl, error: String): String {
            if (response.status == 401) throw AuthFailed(401, "学习通会话已失效，请重新登录。")
            if (response.status in 300..399) {
                if (isTrustedLoginRedirect(url, response.location)) {
                    throw AuthFailed(401, "学习通会话已失效，请重新登录。")
                }
                throw ChxCourseReadException(error)
            }
            if (response.status !in 200..299 || response.body.isBlank()) throw ChxCourseReadException(error)
            // 仅真实表单中的密码框与登录文案共同出现才是登录证据；普通正文不触发失效。
            val document = Jsoup.parse(response.body)
            val login = document.select("form").any { form ->
                form.select("input[type=password]").isNotEmpty() &&
                    listOf("用户登录", "扫码登录", "统一身份认证").any { it in form.text() }
            }
            if (login) throw AuthFailed(401, "学习通会话已失效，请重新登录。")
            return response.body
        }

        private fun isTrustedLoginRedirect(base: HttpUrl, location: String?): Boolean {
            if (location.isNullOrBlank() || location.any { it.code <= 32 || it.code == 127 } || '\\' in location) return false
            val target = try { URI(base.toString()).resolve(URI(location)) } catch (_: Exception) { return false }
            if (target.scheme != "https" || target.userInfo != null || target.port !in setOf(-1, 443)) return false
            val parsed = target.toString().toHttpUrlOrNull() ?: return false
            if (parsed.host !in setOf("mooc2-ans.chaoxing.com", "passport.chaoxing.com", "passport2.chaoxing.com", "cas.gdust.edu.cn")) return false
            return ChxSso.isLoginUrl(parsed.toString())
        }

        internal fun parseCourses(html: String): List<ChxCourse> {
            val document = Jsoup.parse(html)
            val containers = document.select("#courseList")
            if (containers.size != 1) throw ChxCourseReadException(UNKNOWN_LIST)
            val rows = containers.single().select(".course.learnCourse")
            // 页面含隐藏的“暂无课程”模板，不能用文案或 HTTP 200 判成功空列表。
            if (rows.isEmpty()) throw ChxCourseReadException(UNKNOWN_LIST)
            val courses = linkedMapOf<String, ChxCourse>()
            for (row in rows) {
                val clazzId = hidden(row, "clazzId").takeIf(::isId) ?: throw ChxCourseReadException(UNKNOWN_LIST)
                val courseId = hidden(row, "courseId").takeIf(::isId) ?: throw ChxCourseReadException(UNKNOWN_LIST)
                val curPersonId = hidden(row, "curPersonId").takeIf(::isId) ?: throw ChxCourseReadException(UNKNOWN_LIST)
                if (hidden(row, "role") != "0" || row.attr("info") != "${clazzId}_${curPersonId}") {
                    throw ChxCourseReadException(UNKNOWN_LIST)
                }
                val name = row.selectFirst(".course-info .course-name[title]")?.attr("title")?.trim().orEmpty()
                if (name.isEmpty()) throw ChxCourseReadException(UNKNOWN_LIST)
                val description = listOf("p.margint10.line2.color2[title]", "p.line2.color3[title]")
                    .mapNotNull { selector -> row.selectFirst(selector)?.attr("title")?.trim()?.takeIf { it.isNotEmpty() } }
                    .joinToString("\n")
                // 不运行 onclick，不接受备用域名、额外 query 或与 hidden 不符的标识。
                val links = row.select("a[href]").mapNotNull {
                    trustedCourseUrl(it.attr("href"), clazzId, courseId, curPersonId)
                }.distinct()
                if (links.size != 1) throw ChxCourseReadException(UNKNOWN_LIST)
                val course = ChxCourse(clazzId, courseId, curPersonId, name, description, links.single())
                val existing = courses[course.info]
                if (existing != null && (existing.courseId != course.courseId || existing.url != course.url)) {
                    throw ChxCourseReadException(UNKNOWN_LIST)
                }
                courses.putIfAbsent(course.info, course)
            }
            return courses.values.toList()
        }

        private fun hidden(row: Element, name: String): String {
            val fields = row.select("input[type=hidden].$name")
            return if (fields.size == 1) fields.single().attr("value") else ""
        }

        private fun isId(value: String): Boolean = value.isNotEmpty() && value.all { it in '0'..'9' } &&
            value.toLongOrNull()?.let { it > 0L } == true

        internal fun trustedCourseUrl(href: String, clazzId: String, courseId: String, curPersonId: String): String? {
            if (listOf(clazzId, courseId, curPersonId).any { !isId(it) } ||
                href.any { it.code <= 32 || it.code == 127 } || '\\' in href
            ) return null
            val uri = try { URI(href) } catch (_: Exception) { return null }
            if (uri.scheme != "https" || !uri.host.equals(COURSE_HOST, ignoreCase = true) ||
                uri.port !in setOf(-1, 443) || uri.userInfo != null || uri.rawPath != COURSE_PATH || uri.rawFragment != null
            ) return null
            val url = href.toHttpUrlOrNull() ?: return null
            if (url.scheme != "https" || url.host != COURSE_HOST || url.port != 443 ||
                url.username.isNotEmpty() || url.password.isNotEmpty() || url.encodedPath != COURSE_PATH
            ) return null
            val expected = mapOf("courseid" to courseId, "clazzid" to clazzId, "cpi" to curPersonId, "ismooc2" to "1", "v" to "2")
            if (url.queryParameterNames != expected.keys || expected.any { (name, value) ->
                    url.queryParameterValues(name) != listOf(value)
                }
            ) return null
            return url.toString()
        }

        internal fun attachProgress(courses: List<ChxCourse>, json: String): ChxCourseOverview {
            val root = try { JsonParser.parseString(json) } catch (_: Exception) { throw ChxCourseReadException(UNKNOWN_PROGRESS) }
            if (!root.isJsonObject) throw ChxCourseReadException(UNKNOWN_PROGRESS)
            val objectValue = root.asJsonObject
            val status = objectValue.get("status")
            val rows = objectValue.get("jobArray")
            if (status == null || !status.isJsonPrimitive || !status.asJsonPrimitive.isBoolean || !status.asBoolean ||
                rows == null || !rows.isJsonArray
            ) throw ChxCourseReadException(UNKNOWN_PROGRESS)
            val known = courses.map { it.clazzId.toLong() }.toSet()
            val progress = mutableMapOf<Long, ChxCourseProgress>()
            val seen = mutableSetOf<Long>()
            var partial = false
            for (entry in rows.asJsonArray) {
                if (!entry.isJsonObject) { partial = true; continue }
                val row = entry.asJsonObject
                val clazzId = integer(row, "clazzId")?.toLong()?.takeIf { it > 0 }
                if (clazzId == null) { partial = true; continue }
                if (clazzId !in known) continue
                if (!seen.add(clazzId)) {
                    progress.remove(clazzId)
                    partial = true
                    continue
                }
                val count = integer(row, "jobCount")
                val finished = integer(row, "jobFinishCount")
                val rate = number(row, "jobRate")
                if (count == null || finished == null || rate == null || count < 0 || finished !in 0..count || rate !in 0.0..100.0) {
                    partial = true
                    continue
                }
                progress[clazzId] = ChxCourseProgress(count, finished, rate)
            }
            val attached = courses.map { it.copy(progress = progress[it.clazzId.toLong()]) }
            return ChxCourseOverview(
                attached,
                if (partial || attached.any { it.progress == null }) "部分课程的任务点进度未知，可稍后刷新。" else null,
            )
        }

        private fun integer(row: JsonObject, name: String): Int? {
            val value = row.get(name) ?: return null
            if (!value.isJsonPrimitive || !value.asJsonPrimitive.isNumber) return null
            return try { value.asBigDecimal.intValueExact() } catch (_: Exception) { null }
        }

        private fun number(row: JsonObject, name: String): Double? {
            val value = row.get(name) ?: return null
            if (!value.isJsonPrimitive || !value.asJsonPrimitive.isNumber) return null
            return try { value.asDouble.takeIf { it.isFinite() } } catch (_: Exception) { null }
        }
    }
}

/** 只包含固定、可展示的错误文案，不携带响应正文、请求头或账户数据。 */
internal class ChxCourseReadException(message: String) : Exception(message)
