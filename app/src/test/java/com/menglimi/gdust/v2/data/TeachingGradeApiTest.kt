package com.menglimi.gdust.v2.data

import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import okhttp3.FormBody
import okhttp3.Request
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class TeachingGradeApiTest {
    @Test
    fun loadsEveryPageUsingTopLevelPaginationAndTheCapturedFormContract() = runBlocking {
        val requests = mutableListOf<Request>()
        var clock = 1_000L
        val loader = TeachingGradeLoader("https://teaching.example.test/proxy/existing-prefix/", execute = { request ->
            requests += request
            if (request.method == "GET") response(gradeFormFixture("runtime&amp;+"))
            else {
                val number = request.form()["queryModel.currentPage"]!!.toInt()
                val start = (number - 1) * 15
                val items = (start until minOf(start + 15, 32)).map { gradeFixture("C$it") }
                response(gradePageFixture(items, number, total = 32))
            }
        }, now = { clock++ })

        val report = loader.load("2026", "3")
        assertEquals(32, report.total)
        assertEquals((0 until 32).map { "C$it" }, report.entries.map { it.courseCode })
        assertEquals(4, requests.size)
        assertEquals("/proxy/existing-prefix/cjcx/cjcx_cxDgXscj.html", requests.first().url.encodedPath)
        assertEquals("N305005", requests.first().url.queryParameter("gnmkdm"))
        assertEquals("default", requests.first().url.queryParameter("layout"))
        requests.drop(1).forEachIndexed { index, request ->
            assertEquals("/proxy/existing-prefix/cjcx/cjcx_cxXsgrcj.html", request.url.encodedPath)
            assertEquals("query", request.url.queryParameter("doType"))
            assertEquals("N305005", request.url.queryParameter("gnmkdm"))
            assertEquals(requests.first().url.toString(), request.header("Referer"))
            assertEquals(mapOf(
                "xnm" to "2026", "xqm" to "3", "sfzgcj" to "", "kcbj" to "", "pkey" to "runtime&+",
                "_search" to "false", "nd" to (1_000L + index).toString(), "queryModel.showCount" to "15",
                "queryModel.currentPage" to (index + 1).toString(), "queryModel.sortName" to "+",
                "queryModel.sortOrder" to "asc", "time" to (index + 1).toString(),
            ), request.form())
            val body = request.body as FormBody
            val sortIndex = (0 until body.size).single { body.name(it) == "queryModel.sortName" }
            assertEquals("%2B", body.encodedValue(sortIndex))
        }
    }

    @Test
    fun eachRefreshReadsANewPkeyAndQueryCounterKeepsIncreasing() = runBlocking {
        var pageLoads = 0
        val keys = mutableListOf<String>()
        val counts = mutableListOf<String>()
        val loader = TeachingGradeLoader("https://teaching.example.test/prefix", execute = { request ->
            if (request.method == "GET") {
                pageLoads++
                response(gradeFormFixture(if (pageLoads == 1) "" else "changed-key"))
            } else {
                keys += request.form().getValue("pkey")
                counts += request.form().getValue("time")
                response(gradePageFixture(emptyList()))
            }
        })
        loader.load("2026", "3")
        loader.load("2026", "3")
        assertEquals(2, pageLoads)
        assertEquals(listOf("", "changed-key"), keys)
        assertEquals(listOf("1", "2"), counts)
    }

    @Test
    fun validEmptyResponsesRequireConsistentPagination() = runBlocking<Unit> {
        for (pages in 0..1) {
            val loader = queuedLoader(listOf(response(gradeFormFixture()),
                response(gradePageFixture(emptyList(), totalPages = pages))))
            assertEquals(GradeReport(emptyList(), 0), loader.load("2026", "3"))
        }
        assertFailure(listOf(gradePageFixture(emptyList(), currentPage = 0)))
        assertFailure(listOf(gradePageFixture(emptyList(), totalPages = 2)))
        assertFailure(listOf(gradePageFixture(emptyList(), total = 1)))
        assertFailure(listOf(gradePageFixture(listOf(gradeFixture()), total = 0)))
    }

    @Test
    fun wrongPageNumberMissingRowsAndChangedTotalsNeverReturnPartialSuccess() {
        val first = gradePageFixture((0 until 15).map { gradeFixture("C$it") }, total = 16)
        assertFailure(listOf(first, gradePageFixture(listOf(gradeFixture("C15")), currentPage = 1, total = 16)))
        assertFailure(listOf(first, gradePageFixture(emptyList(), currentPage = 2, total = 16)))
        assertFailure(listOf(first, gradePageFixture(listOf(gradeFixture("C15")), currentPage = 2, total = 17)))
        assertFailure(listOf(first, gradePageFixture(listOf(gradeFixture("C15")), currentPage = 2, total = 16, totalPages = 3)))
        assertFailure(listOf(gradePageFixture((0 until 14).map { gradeFixture("C$it") }, total = 16)))
        assertFailure(listOf(gradePageFixture((0 until 15).map { gradeFixture("C$it") }, total = 16, totalPages = 1)))
    }

    @Test
    fun repeatedPageIsDetectedEvenWhenTheServerChangesOrderAndPageNumber() {
        val items = (0 until 15).map { gradeFixture("C$it") }
        assertFailure(listOf(
            gradePageFixture(items, total = 30),
            gradePageFixture(items.reversed(), currentPage = 2, total = 30),
        ))
    }

    @Test
    fun requestLimitAndWrongSemesterFailBeforeReturningAReport() {
        val error = assertFailure(listOf(gradePageFixture((0 until 15).map { gradeFixture("C$it") }, total = 1501)))
        assertEquals(GradeFailure.LIMIT, error.reason)
        val mismatch = assertFailure(listOf(gradePageFixture(listOf(gradeFixture(semester = "12")))))
        assertEquals(GradeFailure.TERM_MISMATCH, mismatch.reason)
    }

    @Test
    fun selectedValuesMustExistInTheRuntimePage() {
        var calls = 0
        val loader = TeachingGradeLoader("https://teaching.example.test", execute = {
            calls++
            response(gradeFormFixture().replace("value='2026'", "value='2025'"))
        })
        val failure = assertThrows(GradeLoadException::class.java) { runBlocking { loader.load("2026", "3") } }
        assertEquals(GradeFailure.TERM_UNAVAILABLE, failure.reason)
        assertEquals(1, calls)
    }

    @Test
    fun explicitAuthResponsesAndLoginPagesAreAuthFailed() {
        for (reply in listOf(
            SessionResponse(401, "", null), SessionResponse(901, "", null),
            response("""{"code":"901","message":"private-value"}"""),
            response("""{"status":401}"""), response("901"),
            SessionResponse(302, "", "/xtgl/login_slogin.html"),
            SessionResponse(302, "", "https://cas.example.test/cas/?service=fixture"),
            response("<html><form><input type='password'></form></html>"),
            response("<html><form action='/login'></form></html>"),
            response("""<script>top.location.href='/xtgl/login_slogin.html';</script>"""),
            response("<html><title>统一身份认证平台</title><div id='app'></div></html>"),
            response("""<meta http-equiv='Refresh' content='0; URL = /xtgl/login_slogin.html'>"""),
        )) {
            val loader = queuedLoader(listOf(reply))
            val failure = assertThrows(AuthFailed::class.java) { runBlocking { loader.load("2026", "3") } }
            assertTrue(failure.code == 401 || failure.code == 901)
            assertTrue(!failure.message.orEmpty().contains("private-value"))
        }
        assertThrows(AuthFailed::class.java) {
            runBlocking {
                queuedLoader(listOf(response(gradeFormFixture()), response("""{"code":901}""")))
                    .load("2026", "3")
            }
        }
    }

    @Test
    fun unknownHtmlAndNonLoginRedirectsAreStructureOrResponseFailures() {
        for (reply in listOf(
            response("<html><h1>维护中，请稍后登录</h1></html>"),
            SessionResponse(302, "", "/maintenance"),
            SessionResponse(500, "private-value", null),
        )) {
            val failure = assertThrows(GradeLoadException::class.java) {
                runBlocking { queuedLoader(listOf(reply)).load("2026", "3") }
            }
            assertTrue(!failure.message.orEmpty().contains("private-value"))
        }
        assertFailure(listOf("<html><h1>系统维护</h1></html>"))
    }

    @Test
    fun laterPageFailureAndCancellationPropagateWithoutPartialResults() {
        val failure = IOException("test network unavailable")
        val cancelled = CancellationException("test cancelled")
        for (expected in listOf(failure, cancelled)) {
            var calls = 0
            val loader = TeachingGradeLoader("https://teaching.example.test", execute = {
                when (++calls) {
                    1 -> response(gradeFormFixture())
                    2 -> response(gradePageFixture((0 until 15).map { gradeFixture("C$it") }, total = 16))
                    else -> throw expected
                }
            })
            val actual = assertThrows(expected.javaClass) { runBlocking { loader.load("2026", "3") } }
            assertSame(expected, actual)
            assertEquals(3, calls)
        }
    }

    private fun assertFailure(pages: List<String>): GradeLoadException =
        assertThrows(GradeLoadException::class.java) {
            runBlocking {
                queuedLoader(listOf(response(gradeFormFixture())) + pages.map { response(it) }).load("2026", "3")
            }
        }

    private fun queuedLoader(responses: List<SessionResponse>): TeachingGradeLoader {
        var index = 0
        return TeachingGradeLoader("https://teaching.example.test", execute = {
            assertTrue("Unexpected additional page request", index < responses.size)
            responses[index++]
        })
    }

    private fun response(body: String) = SessionResponse(200, body, null)
    private fun Request.form(): Map<String, String> = (body as FormBody).let { form ->
        (0 until form.size).associate { form.name(it) to form.value(it) }
    }
}
