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
        assertEquals(tabs, api.courseTabs(course))
        assertEquals(listOf(course.url, landing), requests.map { it.url.toString() })
        assertTrue(requests.all { it.header("Cookie") == null && ChxApi.isAllowedUrl(it.url) })
        assertTrue(requests.all { it.header("User-Agent") == ChxCourseApi.USER_AGENT })
        assertTrue(requests.all { it.header("Referer") == ChxSso.HOME })
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

    @Test fun loginRedirectIsAuthenticationFailureButOtherRedirectsAreNot() {
        for (target in listOf("https://passport2.chaoxing.com/login?refer=synthetic", "https://cas.gdust.edu.cn/cas/")) {
            var calls = 0
            assertThrows(AuthFailed::class.java) { runBlocking {
                ChxApi { calls++; SessionResponse(302, "", target) }.courseTabs(course)
            } }
            assertEquals(1, calls)
        }
        for (target in listOf(null, "https://example.org/private?signature=synthetic", landing.replace("https:", "http:"),
            landing.replace(".com/", ".com:8443/"), landing.replace("https://", "https://user:synthetic@"),
            landing.replace("/mycourse/stu", "/unknown"), "https://mooc1.chaoxing.com/mooc2/work/list")) {
            var calls = 0
            val error = assertThrows(ChxCourseReadException::class.java) { runBlocking {
                ChxApi { calls++; SessionResponse(302, "", target) }.courseTabs(course)
            } }
            assertEquals(1, calls)
            assertTrue(error.message.orEmpty().contains("HTTP 302"))
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
            "https://mooc1.chaoxing.com/mooc-ans/mooc2/work/%74ask")) {
            assertNull(ChxApi.trustedUrl(url))
            var calls = 0
            assertThrows(ChxCourseReadException::class.java) { runBlocking {
                ChxApi { calls++; SessionResponse(200, "", null) }.workDetailHtml(url)
            } }
            assertEquals(0, calls)
        }
        assertFalse(ChxApi.isAllowedUrl("https://mooc1.chaoxing.com/unknown".toHttpUrl()))
        assertThrows(ChxCourseReadException::class.java) { ChxApi.workListUrl(course, tabs.copy(moocDomain = "https://unknown.example")) }
    }
}
