package com.menglimi.chaoxing

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class ChxCourseApiTest {
    // 全部为人工构造的标识与内容，不读取 HAR 或真实用户数据。
    private val homepage = """
        <input type="hidden" id="stuPageHeader" value="1">
        <input type="hidden" id="loadStuJobInfo" value="1">
        <input type="hidden" id="checkUserToken" value="0123456789abcdef0123456789abcdef">
    """.trimIndent()
    private val firstUrl = courseUrl()
    private val zeroProgress = """{"status":true,"jobArray":[{"clazzId":101,"jobCount":0,"jobFinishCount":0,"jobRate":0}]}"""

    @Test fun readsOnlyTheCurrentCoursePageAndUsesTheVerifiedFormAndProgressQuery() = runBlocking {
        val requests = mutableListOf<Request>()
        val duplicate = courseRow()
        val overview = ChxCourseApi.loadWith { request ->
            requests += request
            when (request.url.encodedPath) {
                "/mooc2-ans/visit/interaction" -> ChxResponse(200, homepage, null)
                "/mooc2-ans/visit/courselistdata" -> ChxResponse(200,
                    coursePage(duplicate + duplicate + courseRow("102", "202", "302", "另一门示例课程")), null)
                else -> ChxResponse(200, """{"status":true,"jobArray":[
                    {"clazzId":102,"jobCount":4,"jobFinishCount":1,"jobRate":25},
                    {"clazzId":101,"jobCount":0,"jobFinishCount":0,"jobRate":0}
                ]}""", null)
            }
        }
        assertEquals(3, requests.size)
        assertEquals(listOf("GET", "POST", "GET"), requests.map { it.method })
        assertTrue(requests.all { ChxCourseApi.isAllowedUrl(it.url) })
        assertTrue(requests.all { it.header("Cookie") == null })
        val form = requests[1].body as FormBody
        assertEquals(listOf(
            "courseType" to "1", "courseFolderId" to "0", "query" to "", "pageHeader" to "-1",
            "single" to "0", "superstarClass" to "0", "isFirefly" to "0", "courseLevelLabelId" to "0",
            "fid" to "", "from" to "",
        ), (0 until form.size).map { form.name(it) to form.value(it) })
        assertEquals("101_301,102_302", requests[2].url.queryParameter("clazzPersonStr"))
        assertEquals(listOf("101_301", "102_302"), overview.courses.map { it.info })
        assertEquals(0.0, overview.courses[0].progress!!.jobRate, 0.0)
        assertEquals(25.0, overview.courses[1].progress!!.jobRate, 0.0)
        assertEquals("示例团队 & 项目\n示例课程说明", overview.courses[0].description)
        assertEquals("示例课程 & 实践", overview.courses[0].name)
        assertNull(overview.progressError)
        assertFalse(overview.progressAuthRequired)
    }

    @Test fun hiddenEmptyTemplateDoesNotHideValidCoursesOrProveAnEmptyList() {
        val template = "<div hidden>暂无课程</div>"
        assertEquals(1, ChxCourseApi.parseCourses(coursePage(courseRow(), template)).size)
        listOf(
            "", "<html>暂无课程</html>", coursePage("", template),
            "<html>系统维护</html>", "<script>const sample = `${coursePage(courseRow())}`;</script>",
            "<!-- ${coursePage(courseRow())} -->", courseRow(),
        ).forEach { html ->
            assertThrows(ChxCourseReadException::class.java) { ChxCourseApi.parseCourses(html) }
        }
    }

    @Test fun malformedIdentityMissingTitleAndNonStudentRowsAreExplicitFailures() {
        listOf(
            courseRow().replace("info=\"101_301\"", "info=\"101_999\""),
            courseRow().replace("class=\"role\" value=\"0\"", "class=\"role\" value=\"1\""),
            courseRow().replace("class=\"clazzId\" value=\"101\"", "class=\"clazzId\" value=\"\""),
            courseRow().replace("class=\"courseId\" value=\"201\"", "class=\"courseId\" value=\"-1\""),
            courseRow().replace("class=\"course-name\" title=\"示例课程 &amp; 实践\"", "class=\"course-name\""),
            courseRow() + courseRow(courseId = "299"),
        ).forEach { row ->
            assertThrows(ChxCourseReadException::class.java) { ChxCourseApi.parseCourses(coursePage(row)) }
        }
    }

    @Test fun trustedCourseLinkMustMatchTheExactOriginPathAndHiddenIds() {
        assertEquals(firstUrl, ChxCourseApi.trustedCourseUrl(firstUrl, "101", "201", "301"))
        assertNotNull(ChxCourseApi.trustedCourseUrl(firstUrl.replace(".com/", ".com:443/"), "101", "201", "301"))
        val untrusted = listOf(
            firstUrl.replace("https:", "http:"),
            firstUrl.replace("mooc1.chaoxing.com", "mooc1.chaoxing.com.evil.example"),
            firstUrl.replace("mooc1.chaoxing.com", "evil.example"),
            firstUrl.replace("mooc1.chaoxing.com", "mooc1.chaoxing.com."),
            firstUrl.replace("mooc1.chaoxing.com", "mooc1.chaoxing.com:8443"),
            firstUrl.replace("https://", "https://user:secret@"),
            firstUrl.replace("https://", "https://@"),
            firstUrl.replace("/visit/stucoursemiddle", "/visit/stucoursemiddle/extra"),
            firstUrl.replace("/visit/stucoursemiddle", "/visit/%73tucoursemiddle"),
            firstUrl.replace("/visit/stucoursemiddle", "/other/../visit/stucoursemiddle"),
            firstUrl.replace("courseid=201", "courseid=999"),
            firstUrl.replace("clazzid=101", "clazzid=999"),
            firstUrl.replace("cpi=301", "cpi=999"),
            firstUrl.replace("ismooc2=1", "ismooc2=0"),
            firstUrl.replace("v=2", "v=1"),
            "$firstUrl&courseid=201", "$firstUrl&redirect=https%3A%2F%2Fevil.example", "$firstUrl#fragment",
            firstUrl.removePrefix("https:"), "/visit/stucoursemiddle?courseid=201", "javascript:openCourse()",
            firstUrl.replace("https://", "https:\\\\"), "$firstUrl\n",
        )
        untrusted.forEach { url ->
            assertNull(url, ChxCourseApi.trustedCourseUrl(url, "101", "201", "301"))
            assertThrows(ChxCourseReadException::class.java) { ChxCourseApi.parseCourses(coursePage(courseRow(href = url))) }
        }
        assertEquals(firstUrl, ChxCourseApi.parseCourses(coursePage(courseRow())).single().url)
    }

    @Test fun authenticationRequires401TrustedRedirectOrAnActualLoginForm() {
        val base = ChxCourseApi.LIST_URL.toHttpUrl()
        assertThrows(ChxAuthFailed::class.java) { ChxCourseApi.requireBody(ChxResponse(401, "", null), base, "失败") }
        listOf(
            "https://passport2.chaoxing.com/login?refer=anything", "/login",
            "https://login.example.org/cas/login?service=anything",
        ).forEach { target ->
            assertThrows(ChxAuthFailed::class.java) {
                ChxCourseApi.requireBody(ChxResponse(302, "", target), base, "失败") {
                    it.toHttpUrl().host == "login.example.org"
                }
            }
        }
        listOf(
            null, "/maintenance", "/home?refer=login", "https://evil.example/login",
            "https://passport2.chaoxing.com.evil.example/login", "http://passport2.chaoxing.com/login",
            "https://user@passport2.chaoxing.com/login", "https://@passport2.chaoxing.com/login",
            "https://passport2.chaoxing.com:8443/login", "https://unknown.chaoxing.com/login",
        ).forEach { target ->
            assertThrows(ChxCourseReadException::class.java) {
                ChxCourseApi.requireBody(ChxResponse(302, "", target), base, "失败")
            }
        }
        val login = "<form><input type='password'><button>用户登录</button></form>"
        assertThrows(ChxAuthFailed::class.java) { ChxCourseApi.requireBody(ChxResponse(200, login, null), base, "失败") }
        listOf("<p>用户登录后可查看课程</p>", "<script>const example = `$login`</script>", "<!-- $login -->").forEach {
            assertEquals(it, ChxCourseApi.requireBody(ChxResponse(200, it, null), base, "失败"))
        }
        listOf(403, 429, 500).forEach { status ->
            assertThrows(ChxCourseReadException::class.java) {
                ChxCourseApi.requireBody(ChxResponse(status, "server details", null), base, "固定错误")
            }
        }
    }

    @Test fun openingDatesAndEndedMarkAreParsedAsOptionalMetadata() {
        val opening = courseRow().replace(
            "<p class=\"line2 color3\" title=\"示例课程说明\">省略说明</p>",
            "<p class=\"line2 color3\" title=\"示例课程说明\">省略说明</p><p>开课时间：2026-09-15～2028-09-15</p>")
        val openEnded = courseRow("102", "202", "302").replace(
            "<p class=\"line2 color3\" title=\"示例课程说明\">省略说明</p>",
            "<p>开课时间：2026-02-09～</p>")
        val endedRow = courseRow("103", "203", "303").replace(
            "<a href=", "<a class=\"not-open-tip\" href=")
        val byKey = ChxCourseApi.parseCourses(coursePage(opening + openEnded + endedRow))
            .associateBy { it.info }
        assertEquals("2026-09-15", byKey.getValue("101_301").openingStart)
        assertEquals("2028-09-15", byKey.getValue("101_301").openingEnd)
        assertEquals(false, byKey.getValue("101_301").ended)
        assertEquals("2026-02-09", byKey.getValue("102_302").openingStart)
        assertEquals(null, byKey.getValue("102_302").openingEnd)
        assertEquals(true, byKey.getValue("103_303").ended)
        assertTrue(byKey.getValue("101_301").closedAsOf("2026-11-17") == false)
        assertTrue(byKey.getValue("103_303").closedAsOf("2026-11-17") == true)
        assertEquals(null, byKey.getValue("102_302").closedAsOf("2026-11-17"))
        val plain = ChxCourseApi.parseCourses(coursePage(courseRow())).single()
        assertNull(plain.openingStart)
        assertNull(plain.openingEnd)
        assertFalse(plain.ended)
        assertNull(plain.closedAsOf("2026-11-17"))
    }

    @Test fun verificationResponsesAreNotMisreportedAsMissingCourseFieldsOrExpiredLogin() {
        val base = ChxCourseApi.LIST_URL.toHttpUrl()
        for (body in listOf("""{"code":9010,"msg":"private"}""",
            """{"errorCode":"9010"}""",
            "<form><input name='verifyCode'><button>输入验证码</button></form>")) {
            val failure = assertThrows(ChxCourseReadException::class.java) {
                ChxCourseApi.requireBody(ChxResponse(200, body), base, "失败")
            }
            assertTrue(failure.verificationRequired)
            assertFalse(failure.message.orEmpty().contains("private"))
        }
        for (body in listOf("<p>课程9010介绍验证码</p>", """{"courseId":9010}""",
            "<script>const sample = `<form><input name='captcha'>验证码</form>`;</script>")) {
            assertEquals(body, ChxCourseApi.requireBody(ChxResponse(200, body), base, "失败"))
        }
    }

    @Test fun unknownHomepageDoesNotFetchCoursesOrClaimSuccess() {
        var calls = 0
        val error = assertThrows(ChxCourseReadException::class.java) {
            runBlocking { ChxCourseApi.loadWith { calls++; ChxResponse(200, "<html>欢迎使用</html>", null) } }
        }
        assertEquals(1, calls)
        assertFalse(error.message.orEmpty().contains("欢迎使用"))
    }

    @Test fun unknownListRemainsAnErrorEvenAfterVerifiedHomepage() {
        var calls = 0
        assertThrows(ChxCourseReadException::class.java) {
            runBlocking {
                ChxCourseApi.loadWith {
                    calls++
                    ChxResponse(200, if (calls == 1) homepage else coursePage("", "<div hidden>暂无课程</div>"), null)
                }
            }
        }
        assertEquals(2, calls)
    }

    @Test fun progressIsJoinedByClassAndMissingProgressStaysUnknown() {
        val courses = ChxCourseApi.parseCourses(coursePage(courseRow() + courseRow("102", "202", "302")))
        val result = ChxCourseApi.attachProgress(courses, zeroProgress)
        assertEquals(0.0, result.courses[0].progress!!.jobRate, 0.0)
        assertNull(result.courses[1].progress)
        assertNotNull(result.progressError)
        assertEquals(courses.map { it.info }, result.courses.map { it.info })
    }

    @Test fun invalidPercentagesCountsAndDuplicateProgressNeverBecomeZero() {
        val courses = ChxCourseApi.parseCourses(coursePage(courseRow()))
        listOf("-1", "100.1", "1e999", "\"NaN\"", "\"25\"", "null").forEach { value ->
            val result = ChxCourseApi.attachProgress(courses, """{"status":true,"jobArray":[
                {"clazzId":101,"jobCount":4,"jobFinishCount":1,"jobRate":$value}
            ]}""")
            assertNull(result.courses.single().progress)
            assertNotNull(result.progressError)
        }
        listOf(
            "{\"clazzId\":101,\"jobCount\":-1,\"jobFinishCount\":0,\"jobRate\":0}",
            "{\"clazzId\":101,\"jobCount\":4,\"jobFinishCount\":5,\"jobRate\":100}",
            "{\"clazzId\":101,\"jobCount\":4.1,\"jobFinishCount\":1,\"jobRate\":25}",
            "{\"clazzId\":101,\"jobCount\":4,\"jobFinishCount\":-1,\"jobRate\":0}",
            "{\"clazzId\":\"101\",\"jobCount\":4,\"jobFinishCount\":1,\"jobRate\":25}",
            "{\"clazzId\":101,\"jobCount\":2147483648,\"jobFinishCount\":1,\"jobRate\":0}",
            "{\"clazzId\":101,\"jobCount\":4,\"jobFinishCount\":1,\"jobRate\":25}," +
                "{\"clazzId\":101,\"jobCount\":4,\"jobFinishCount\":2,\"jobRate\":50}",
        ).forEach { rows ->
            val result = ChxCourseApi.attachProgress(courses, """{"status":true,"jobArray":[$rows]}""")
            assertNull(result.courses.single().progress)
            assertNotNull(result.progressError)
        }
    }

    @Test fun progressFailuresKeepCoursesAndAuthenticationShowsLoginAction() = runBlocking {
        listOf(
            ChxResponse(503, "private server body", null),
            ChxResponse(200, "<html>维护</html>", null),
            ChxResponse(200, """{"status":false,"jobArray":[]}""", null),
            ChxResponse(200, """{"status":"true","jobArray":[]}""", null),
            ChxResponse(401, "", null),
            ChxResponse(302, "", "https://passport2.chaoxing.com/login"),
        ).forEach { progress ->
            val result = ChxCourseApi.loadWith { request ->
                when (request.url.toString()) {
                    ChxSso.HOME -> ChxResponse(200, homepage, null)
                    ChxCourseApi.LIST_URL -> ChxResponse(200, coursePage(courseRow()), null)
                    else -> progress
                }
            }
            assertEquals(1, result.courses.size)
            assertNull(result.courses.single().progress)
            assertNotNull(result.progressError)
            assertEquals(progress.status in setOf(401, 302), result.progressAuthRequired)
            assertFalse(result.progressError.orEmpty().contains("private server body"))
        }
        val result = ChxCourseApi.loadWith { request ->
            when (request.url.toString()) {
                ChxSso.HOME -> ChxResponse(200, homepage, null)
                ChxCourseApi.LIST_URL -> ChxResponse(200, coursePage(courseRow()), null)
                else -> throw IOException("private transport details")
            }
        }
        assertEquals(1, result.courses.size)
        assertNotNull(result.progressError)
        assertFalse(result.progressError.orEmpty().contains("private transport details"))
    }

    @Test fun cookieCommitFailureAtEveryRequestNeverBecomesPartialSuccess() {
        for (failAt in 1..3) {
            val failure = ChxCookieWriteException(verificationRequired = true, retryAt = 30_000L)
            var calls = 0
            val result = assertThrows(ChxCookieWriteException::class.java) {
                runBlocking {
                    ChxCourseApi.loadWith {
                        calls++
                        if (calls == failAt) throw failure
                        ChxResponse(200, if (calls == 1) homepage else coursePage(courseRow()))
                    }
                }
            }
            assertSame(failure, result)
            assertEquals(failAt, calls)
            assertTrue(result.verificationRequired)
            assertEquals(30_000L, result.retryAt)
        }
    }

    @Test fun cancellationAtEveryRequestIsRethrown() {
        for (cancelAt in 1..3) {
            val cancelled = CancellationException("测试取消")
            var calls = 0
            val result = assertThrows(CancellationException::class.java) {
                runBlocking {
                    ChxCourseApi.loadWith {
                        calls++
                        if (calls == cancelAt) throw cancelled
                        ChxResponse(200, if (calls == 1) homepage else coursePage(courseRow()), null)
                    }
                }
            }
            assertSame(cancelled, result)
        }
    }

    @Test fun requestAllowlistRejectsDifferentOriginAndUnverifiedEndpoints() {
        listOf(ChxSso.HOME, ChxCourseApi.LIST_URL, ChxCourseApi.PROGRESS_URL).forEach {
            assertTrue(ChxCourseApi.isAllowedUrl(it.toHttpUrl()))
        }
        listOf(
            ChxCourseApi.LIST_URL.replace("https:", "http:"),
            ChxCourseApi.LIST_URL.replace(".com/", ".com:8443/"),
            ChxCourseApi.LIST_URL.replace("https://", "https://user@"),
            ChxCourseApi.LIST_URL.replace("mooc2-ans.chaoxing.com", "mooc2-ans.chaoxing.com.evil.example"),
            "https://mooc2-ans.chaoxing.com/mooc2-ans/unknown", "${ChxCourseApi.LIST_URL}#fragment",
        ).forEach { assertFalse(ChxCourseApi.isAllowedUrl(it.toHttpUrl())) }
    }

    private fun coursePage(rows: String, extra: String = "") = "<div id='courseList'>$rows</div>$extra"

    private fun courseUrl(clazzId: String = "101", courseId: String = "201", cpi: String = "301") =
        "https://mooc1.chaoxing.com/visit/stucoursemiddle?courseid=$courseId&clazzid=$clazzId&cpi=$cpi&ismooc2=1&v=2"

    private fun courseRow(
        clazzId: String = "101",
        courseId: String = "201",
        cpi: String = "301",
        name: String = "示例课程 &amp; 实践",
        href: String = courseUrl(clazzId, courseId, cpi),
    ): String = """
        <li class="course learnCourse" info="${clazzId}_${cpi}">
            <input type="hidden" class="clazzId" value="$clazzId">
            <input type="hidden" class="courseId" value="$courseId">
            <input type="hidden" class="role" value="0">
            <input type="hidden" class="curPersonId" value="$cpi">
            <a href="${href.replace("&", "&amp;").replace("\"", "&quot;")}" onclick="location.href='https://evil.example/'">
                <div class="course-info">
                    <span class="course-name" title="$name">省略标题</span>
                    <p class="margint10 line2 color2" title="示例团队 &amp; 项目">省略说明</p>
                    <p class="line2 color3" title="示例课程说明">省略说明</p>
                </div>
            </a>
        </li>
    """.trimIndent()
}
