package com.menglimi.gdust.v2.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.net.SocketTimeoutException

class ChxWorkApiTest {
    private val course = ChxWorkCourse(201, 101, 301, "示例课程",
        "https://mooc1.chaoxing.com/visit/stucoursemiddle?courseid=201&clazzid=101&cpi=301&ismooc2=1&v=2")
    private val landing = "https://mooc2-ans.chaoxing.com/mooc2-ans/mycourse/stu?courseid=201&clazzid=101&cpi=301"
    private val tabs = ChxWorkTabs("synthetic-stu", "synthetic-work", "", "1700000000000", "", "https://mooc1.chaoxing.com")
    private val landingReferer = "https://mooc1.chaoxing.com/visit/stucoursemiddle?courseid=201&clazzid=101&cpi=301&ismooc2=1&v=2"
    private val tabsHtml = """
        <input type='hidden' id='courseId' value='201'>
        <input type='hidden' id='classId' value='101'>
        <input type='hidden' id='cpi' value='301'>
        <input type='hidden' value='synthetic-stu' id='enc'>
        <input type='hidden' value='synthetic-work' id='workEnc'>
        <input type='hidden' id='t' value='1700000000000'>
        <input type='hidden' id='moocDomainName' value='https://mooc1.chaoxing.com'>
    """.trimIndent()
    private val homepage = """
        <input type='hidden' id='stuPageHeader' value='1'>
        <input type='hidden' id='loadStuJobInfo' value='1'>
        <input type='hidden' id='checkUserToken' value='0123456789abcdef0123456789abcdef'>
    """.trimIndent()
    private val coursesHtml = """
        <div id='courseList'><li class='course learnCourse' info='101_301'>
        <input type='hidden' class='clazzId' value='101'><input type='hidden' class='courseId' value='201'>
        <input type='hidden' class='curPersonId' value='301'><input type='hidden' class='role' value='0'>
        <a href='${course.url}'><div class='course-info'><span class='course-name' title='示例课程'>示例课程</span></div></a>
        </li></div>
    """.trimIndent()
    private val workHtml = """
        <title>作业列表</title><div class='main task-list'><div class='bottomList' aria-label='作业列表'><ul>
        <li onclick='goTask(this);' data='https://mooc1.chaoxing.com/mooc-ans/mooc2/work/task?courseId=201&classId=101&cpi=301&workId=401&answerId=501&enc=synthetic'>
        <p class='overHidden2'>示例作业</p><p class='status'>未交</p><div class='time'>剩余5分钟</div>
        </li></ul></div></div>
    """.trimIndent()

    @Test fun followsTheVerifiedCourseRedirectWithSeparateCookieFreeRequests() = runBlocking {
        val requests = mutableListOf<Request>()
        val api = ChxApi { request ->
            requests += request
            if (requests.size == 1) SessionResponse(302, "", landing) else SessionResponse(200, tabsHtml, null)
        }
        val parsed = api.courseTabs(course)
        assertEquals(tabs.copy(coursePageUrl = landing), parsed)
        assertEquals(listOf(course.url, landing), requests.map { it.url.toString() })
        assertTrue(requests.all { it.header("Cookie") == null && ChxApi.isAllowedUrl(it.url) })
        assertTrue(requests.all { it.header("User-Agent") == ChxApi.USER_AGENT })
        assertTrue(ChxApi.USER_AGENT.contains("Windows NT"))
        assertFalse(ChxApi.USER_AGENT.contains("Mobile"))
        assertTrue(requests.all { it.header("Referer") == ChxSso.HOME })
    }

    @Test fun knownHttpCourseRedirectIsUpgradedBeforeAnyAuthenticatedRequest() = runBlocking {
        val requests = mutableListOf<Request>()
        val page = javaClass.getResource("/chaoxing/course-tabs-lowercase.html")!!.readText()
        val api = ChxApi { request ->
            requests += request
            if (requests.size == 1) SessionResponse(302, "", landing.replace("https:", "http:"))
            else SessionResponse(200, page, null)
        }
        assertEquals("synthetic-work", api.courseTabs(course).workEnc)
        assertEquals(landing, requests.last().url.toString())
        assertTrue(requests.all { it.url.scheme == "https" && it.header("Cookie") == null })
        assertNull(ChxApi.redirectTarget(course.url.toHttpUrl(), "http://unknown.example/mooc2-ans/mycourse/stu"))
        assertNull(ChxApi.redirectTarget(course.url.toHttpUrl(), landing.replace("https://", "http://user:secret@")))
        assertNull(ChxApi.redirectTarget(course.url.toHttpUrl(), landing.replace("https://", "http://").replace(".com/", ".com:8080/")))
    }

    @Test fun followsNewCourseLandingPathsWithoutExpandingTheTrustedHosts() = runBlocking {
        // 路径变体为合成回归输入，并非 issue #6 的抓包回放。
        for (path in listOf("/mycourse/studentcourse", "/mooc-ans/mycourse/stu.html", "/mooc-ans/mycourse/studentcourse")) {
            for (scheme in listOf("https", "http")) {
                val target = "$scheme://mooc1.chaoxing.com$path?courseid=201&clazzid=101&cpi=301&enc=synthetic"
                val requests = mutableListOf<Request>()
                val api = ChxApi { request ->
                    requests += request
                    if (requests.size == 1) SessionResponse(302, "", target) else SessionResponse(200, tabsHtml, null)
                }
                val parsed = api.courseTabs(course)
                assertEquals(tabs.copy(coursePageUrl = target.replace("http:", "https:")), parsed)
                assertEquals(listOf(course.url, target.replace("http:", "https:")), requests.map { it.url.toString() })
                assertTrue(requests.all { it.url.scheme == "https" && ChxApi.isAllowedUrl(it.url) })
                assertTrue(requests.all { it.header("Cookie") == null && it.header("Referer") == ChxSso.HOME })
            }
        }
    }

    @Test fun newLandingPagesStillRequireMatchingIdentityAndRequiredFields() {
        for ((html, field) in listOf(
            tabsHtml.replace("id='courseId' value='201'", "id='courseId' value='999'") to "courseid",
            tabsHtml.replace("id='workEnc'", "id='other'") to "workEnc",
            "<title>系统维护</title>" to "courseid",
        )) {
            var calls = 0
            val error = assertThrows(ChxCourseReadException::class.java) { runBlocking {
                ChxApi {
                    calls++
                    if (calls == 1) SessionResponse(302, "", "/mooc-ans/mycourse/studentcourse?enc=synthetic")
                    else SessionResponse(200, html, null)
                }.courseTabs(course)
            } }
            assertEquals(2, calls)
            assertTrue(error.message.orEmpty().contains(field))
            assertFalse(error.message.orEmpty().contains("synthetic"))
        }
    }

    @Test fun discoveryUsesTheSameCourseParserAndNeverUsesTaskPointCountsToSkipWork() = runBlocking {
        val requests = mutableListOf<Request>()
        val api = ChxApi { request ->
            requests += request
            when (request.url.toString()) {
                ChxSso.HOME -> SessionResponse(200, homepage, null)
                ChxCourseApi.LIST_URL -> SessionResponse(200, coursesHtml, null)
                else -> error("Task-point filtering must not be requested")
            }
        }
        assertEquals(listOf(course), api.courses())
        assertEquals(listOf("GET", "POST"), requests.map { it.method })
        assertTrue(requests.all { it.header("Cookie") == null })
    }

    @Test fun workListUsesTheActualCourseRefererAndSignedMenuEndpoint() = runBlocking {
        val endpoint = "https://mooc1.chaoxing.com/mooc-ans/mooc2/work/list.html"
        val signed = tabs.copy(workListEndpoint = "$endpoint?courseId=201&classId=101&cpi=301&ut=s&t=1700000000000&stuenc=synthetic-stu&enc=synthetic-work",
            coursePageUrl = landingReferer)
        val requests = mutableListOf<Request>()
        val api = ChxApi { request -> requests += request; SessionResponse(200, workHtml, null) }
        assertEquals("示例作业", api.workList(course, signed).single().title)
        assertEquals(endpoint, requests.single().url.newBuilder().query(null).build().toString())
        assertEquals(landingReferer, requests.single().header("Referer"))
        assertEquals(1, requests.single().url.queryParameterValues("enc").size)
        assertTrue(ChxApi.isWorkList(requests.single().url))
    }

    @Test fun conflictingSignedMenuParametersOrCrossCourseRefererAreRejectedBeforeTransport() = runBlocking {
        val signedConflict = tabs.copy(workListEndpoint = "https://mooc1.chaoxing.com/mooc-ans/mooc2/work/list.html?enc=wrong")
        var calls = 0
        assertThrows(ChxCourseReadException::class.java) { runBlocking {
            ChxApi { calls++; SessionResponse(200, workHtml, null) }.workList(course, signedConflict)
        } }
        assertEquals(0, calls)
        val wrongReferer = tabs.copy(coursePageUrl = landingReferer.replace("courseid=201", "courseid=999"))
        assertThrows(ChxCourseReadException::class.java) { runBlocking {
            ChxApi { calls++; SessionResponse(200, workHtml, null) }.workList(course, wrongReferer)
        } }
        assertEquals(0, calls)
    }

    @Test fun workListHandlesTheKnownPrefixRedirectAndEncodesSignaturesExactlyOnce() = runBlocking {
        val special = tabs.copy(enc = "test+a/b=&", workEnc = "work+a/b=&")
        val requests = mutableListOf<Request>()
        val api = ChxApi { request ->
            requests += request
            if (requests.size == 1) SessionResponse(302, "", "/mooc-ans" + request.url.encodedPath + "?" + request.url.encodedQuery)
            else SessionResponse(200, workHtml, null)
        }
        assertEquals("示例作业", api.workList(course, special).single().title)
        assertEquals(2, requests.size)
        assertEquals("/mooc-ans/mooc2/work/list", requests.last().url.encodedPath)
        requests.forEach {
            assertEquals(special.enc, it.url.queryParameter("stuenc"))
            assertEquals(special.workEnc, it.url.queryParameter("enc"))
            assertNull(it.header("Cookie"))
        }
    }

    @Test fun knownRelativeWorkTaskResolvesFromTheWorkListModuleRoot() {
        val list = "https://mooc1.chaoxing.com/mooc-ans/mooc2/work/list.html?courseId=201&classId=101&cpi=301"
        val parsed = ChxApi.workDetailTarget(list.toHttpUrl(),
            "work/task?courseId=201&classId=101&cpi=301&workId=401&answerId=0&enc=synthetic")
        assertEquals("/mooc-ans/mooc2/work/task", parsed?.encodedPath)
        assertEquals("401", parsed?.queryParameter("workId"))
        assertNull(ChxApi.workDetailTarget(list.toHttpUrl(), "work/task?courseId=201"))
        assertNull(ChxApi.workDetailTarget(list.toHttpUrl(), "../other/task?workId=401&answerId=1&enc=x"))
        assertNull(ChxApi.workDetailTarget("https://mooc1.chaoxing.com/other/list".toHttpUrl(),
            "work/task?workId=401&answerId=1&enc=x"))
    }

    @Test fun newListLandingResolvesRelativeDetailsFromTheFinalUrlWithoutTrustingHtmlBase() = runBlocking {
        val requests = mutableListOf<Request>()
        val html = javaClass.getResource("/chaoxing/work-list-variants-synthetic.html")!!.readText()
            .replace("<head>", "<head><base href='https://unknown.example/'>")
        val api = ChxApi { request ->
            requests += request
            when (requests.size) {
                1 -> SessionResponse(302, "", "/mooc-ans/mooc2/work/list.html?enc=synthetic-query")
                2 -> SessionResponse(200, html, null)
                else -> SessionResponse(200, "<div class='infoHead'>合成详情</div>", null)
            }
        }
        val items = api.workList(course, tabs)
        assertEquals(listOf(401L, 402L), items.map { it.workId })
        assertEquals(listOf("未交", "已完成"), items.map { it.status })
        assertEquals("/mooc-ans/mooc2/work/task", items.first().detailUrl.toHttpUrl().encodedPath)
        assertTrue(items.all { it.detailUrl.toHttpUrl().host == "mooc1.chaoxing.com" })
        api.workDetailHtml(items.last().detailUrl)
        assertEquals("/mooc2/work/task", requests.last().url.encodedPath)
        assertTrue(requests.all { it.header("Cookie") == null })
        assertTrue(requests.mapNotNull { it.header("Referer") }.all { !it.contains("synthetic-query") })
    }

    @Test fun loginRedirectIsAuthenticationFailureButOtherRedirectsAreNot() {
        for (target in listOf("https://passport2.chaoxing.com/login?refer=synthetic", "https://cas.gdust.edu.cn/cas/",
            "/login?enc=synthetic", "http://mooc1.chaoxing.com/login?enc=synthetic")) {
            var calls = 0
            assertThrows(AuthFailed::class.java) { runBlocking {
                ChxApi { calls++; SessionResponse(302, "", target) }.courseTabs(course)
            } }
            assertEquals(1, calls)
        }
        for (target in listOf(null, "https://example.org/private?signature=synthetic", "http://unknown.example/mycourse/stu",
            "http://mooc1.chaoxing.com.evil.example/mycourse/studentcourse", "https://unknown.chaoxing.com/work/list",
            landing.replace(".com/", ".com:8443/"), landing.replace("https://", "https://user:synthetic@"),
            landing.replace("/mycourse/stu", "/other/../mycourse/stu"), "$landing#synthetic")) {
            var calls = 0
            val error = assertThrows(ChxCourseReadException::class.java) { runBlocking {
                ChxApi { calls++; SessionResponse(302, "", target) }.courseTabs(course)
            } }
            assertEquals(1, calls)
            assertTrue(error.message.orEmpty().contains("HTTP 302"))
            assertFalse(error.message.orEmpty().contains("synthetic"))
        }
    }

    @Test fun trustedLandingCannotRelayCookiesToAnUntrustedNextHop() {
        for (target in listOf("https://unknown.example/work/list?enc=synthetic", "http://127.0.0.1/private",
            "http://unknown.example/work/list")) {
            val requests = mutableListOf<Request>()
            val error = assertThrows(ChxCourseReadException::class.java) { runBlocking {
                ChxApi { request ->
                    requests += request
                    SessionResponse(302, "", if (requests.size == 1) "/mooc-ans/mycourse/studentcourse" else target)
                }.courseTabs(course)
            } }
            assertEquals(2, requests.size)
            assertTrue(requests.all { it.url.scheme == "https" && it.url.host == "mooc1.chaoxing.com" })
            assertTrue(requests.all { it.header("Cookie") == null })
            assertFalse(error.message.orEmpty().contains("synthetic"))
        }
    }

    @Test fun redirectCyclesAndFailedResponsesDoNotBecomeNullOrEmptySuccess() {
        var calls = 0
        val loop = assertThrows(ChxCourseReadException::class.java) { runBlocking {
            ChxApi { calls++; SessionResponse(302, "", course.url) }.courseTabs(course)
        } }
        assertEquals(1, calls)
        assertTrue(loop.message.orEmpty().contains("循环跳转"))
        calls = 0
        val tooMany = assertThrows(ChxCourseReadException::class.java) { runBlocking {
            ChxApi { calls++; SessionResponse(302, "", "/mooc-ans/mycourse/studentcourse?hop=$calls&enc=synthetic") }
                .courseTabs(course)
        } }
        assertEquals(5, calls)
        assertTrue(tooMany.message.orEmpty().contains("跳转次数过多"))
        assertFalse(tooMany.message.orEmpty().contains("synthetic"))
        for (status in listOf(403, 429, 500, 503)) {
            val error = assertThrows(ChxCourseReadException::class.java) { runBlocking {
                ChxApi { SessionResponse(status, "private response body", null) }.courseTabs(course)
            } }
            assertTrue(error.message.orEmpty().contains("HTTP $status"))
            assertFalse(error.message.orEmpty().contains("private"))
        }
        assertThrows(ChxCourseReadException::class.java) { runBlocking {
            ChxApi { SessionResponse(200, "<title>作业列表</title><p>系统维护</p>", null) }.workList(course, tabs)
        } }
    }

    @Test fun redirectedListsRejectLoginBlankAndErrorPagesWithSanitizedDiagnostics() {
        val login = "<form><input type='password'><button>用户登录</button></form>"
        for (response in listOf(
            SessionResponse(401, "", null), SessionResponse(200, login, null),
            SessionResponse(200, "", null), SessionResponse(204, "", null),
            SessionResponse(503, workHtml, null), SessionResponse(200, "<p>系统维护</p>", null),
            SessionResponse(200, "<title>作业列表</title><div id='workList'>暂无作业</div>", null),
            SessionResponse(200, workHtml.replace("workId=401", "workId=0"), null),
        )) {
            var calls = 0
            val expected = if (response.status == 401 || response.body == login) AuthFailed::class.java
                else ChxCourseReadException::class.java
            val error = assertThrows(expected) { runBlocking {
                ChxApi {
                    calls++
                    if (calls == 1) SessionResponse(302, "",
                        "/mooc-ans/mooc2/work/list.html?enc=private-enc&cookie=private-cookie&account=private-account")
                    else response
                }.workList(course, tabs)
            } }
            assertEquals(2, calls)
            assertFalse(error.message.orEmpty().contains("private"))
            assertFalse(error.message.orEmpty().contains("synthetic"))
        }
    }

    @Test fun unsupportedRedirectDiagnosticsShowOnlySanitizedRoutes() {
        val error = assertThrows(ChxCourseReadException::class.java) { runBlocking {
            ChxApi { SessionResponse(302, "", "https://mooc1.chaoxing.com:8443/mooc-ans/mycourse/private-account/stu" +
                "?enc=private-enc&cookie=private-cookie&account=private-account") }.courseTabs(course)
        } }
        assertTrue(error.message.orEmpty().contains("mooc1.chaoxing.com/mooc-ans/mycourse/…/stu"))
        assertFalse(error.message.orEmpty().contains("private"))
        assertFalse(error.message.orEmpty().contains("?"))
    }

    @Test fun networkFailuresKeepUsefulCategoriesWithoutLeakingExceptionText() {
        for ((exception, category) in listOf(SocketTimeoutException("private signature") to "请求超时",
            IOException("private signature") to "网络传输失败")) {
            val error = assertThrows(ChxCourseReadException::class.java) { runBlocking {
                ChxApi { throw exception }.courseTabs(course)
            } }
            assertTrue(error.message.orEmpty().contains(category))
            assertFalse(error.message.orEmpty().contains("private"))
        }
        val cancelled = CancellationException("test")
        val actual = assertThrows(CancellationException::class.java) { runBlocking {
            ChxApi { throw cancelled }.courseTabs(course)
        } }
        // 开启协程堆栈恢复时异常会复制，但取消原因必须保留且不能被包装成读取失败。
        assertTrue(generateSequence<Throwable>(actual) { it.cause }.any { it === cancelled })
        assertEquals(cancelled.message, actual.message)
    }

    @Test fun untrustedEndpointsAreRejectedBeforeTransport() {
        for (url in listOf("https://unknown.example/task", "http://mooc1.chaoxing.com/mooc-ans/mooc2/work/task",
            "https://mooc1.chaoxing.com:8443/mooc-ans/mooc2/work/task",
            "https://user:secret@mooc1.chaoxing.com/mooc-ans/mooc2/work/task",
            "https://mooc1.chaoxing.com/other/../mooc-ans/mooc2/work/task",
            "https://mooc1.chaoxing.com/mooc-ans/mooc2/work/%2e%2e/task")) {
            assertNull(ChxApi.trustedUrl(url))
            var calls = 0
            assertThrows(ChxCourseReadException::class.java) { runBlocking {
                ChxApi { calls++; SessionResponse(200, "", null) }.workDetailHtml(url)
            } }
            assertEquals(0, calls)
        }
        for (url in listOf("https://mooc1.chaoxing.com/unknown", "https://mooc1.chaoxing.com/mooc-ans/mooc2/work/%74ask")) {
            var calls = 0
            assertThrows(ChxCourseReadException::class.java) { runBlocking {
                ChxApi { calls++; SessionResponse(200, "", null) }.workDetailHtml(url)
            } }
            assertEquals(0, calls)
        }
        for (url in listOf("https://unknown.chaoxing.com/work/list", "https://mooc1.chaoxing.com.evil.example/work/list",
            "http://mooc1.chaoxing.com/work/list", "https://127.0.0.1/work/list")) {
            assertFalse(ChxApi.isAllowedUrl(url.toHttpUrl()))
        }
        assertThrows(ChxCourseReadException::class.java) { ChxApi.workListUrl(course, tabs.copy(moocDomain = "https://unknown.example")) }
    }
}
