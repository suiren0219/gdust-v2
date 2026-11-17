package com.menglimi.chaoxing

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.Request
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Collections
import java.util.concurrent.atomic.AtomicInteger

class ChxWorkReaderTest {
    private val now = LocalDateTime.of(2026, 11, 16, 10, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    private val homepage = """
        <input type='hidden' id='stuPageHeader' value='1'>
        <input type='hidden' id='loadStuJobInfo' value='1'>
        <input type='hidden' id='checkUserToken' value='0123456789abcdef0123456789abcdef'>
    """.trimIndent()

    @Test fun discoversCoursesAndKeepsSuccessfulWorkWhenOtherCoursesFail() = runBlocking {
        val courses = (1L..3L).map(::course)
        val requests = Collections.synchronizedList(mutableListOf<Request>())
        val api = ChxApi { request ->
            requests += request
            when (request.url.encodedPath) {
                "/mooc2-ans/visit/interaction" -> ChxResponse(200, homepage)
                "/mooc2-ans/visit/courselistdata" -> ChxResponse(200, courseList(courses))
                "/visit/stucoursemiddle" -> {
                    val course = courses.single { it.courseId.toString() == request.url.queryParameter("courseid") }
                    if (course.courseId == 1L) throw IOException("private-network-details")
                    ChxResponse(200, tabsHtml(course))
                }
                "/mooc2/work/list" -> {
                    val course = courses.single { it.courseId.toString() == request.url.queryParameter("courseId") }
                    if (course.courseId == 2L) ChxResponse(401, "")
                    else ChxResponse(200, workList(course))
                }
                "/mooc2/work/task" -> when (request.url.queryParameter("workId")) {
                    "401" -> ChxResponse(200, "Time:<em>11-01 10:00</em>to<em>11-22 23:59</em>")
                    "402" -> throw IOException("private-detail")
                    "403" -> ChxResponse(200, "<p>没有截止时间</p>")
                    else -> error("已完成作业不应请求详情")
                }
                else -> error("不得读取任务点来跳过作业")
            }
        }
        val results = ChxWorkReader(api, nowMillis = { now }).read()
        assertEquals(courses, results.map { it.course })
        val network = results[0] as ChxCourseWorkResult.Failure
        assertTrue(network.error is ChxCourseReadException)
        assertFalse(network.error.message.orEmpty().contains("private"))
        assertTrue((results[1] as ChxCourseWorkResult.Failure).error is ChxAuthFailed)
        val work = (results[2] as ChxCourseWorkResult.Success).items
        assertEquals(listOf(401L, 402L, 403L, 404L), work.map { it.item.workId })
        assertEquals(ChxWorkParser.parseMonthDayTime("2026-11-22 23:59", now), work[0].dueAt)
        assertEquals(now + 5 * 60_000L, work[1].dueAt)
        assertEquals(0L, work[2].dueAt)
        assertEquals(0L, work[3].dueAt)
        assertTrue(requests.none { it.url.encodedPath.contains("stu-job-info") })
        assertTrue(requests.all { it.header("Cookie") == null })
    }

    @Test fun detailAuthenticationFailureInvalidatesOnlyItsCourse() = runBlocking {
        val courses = listOf(course(1), course(2))
        val api = ChxApi { request ->
            val current = course(request.url.queryParameter("courseid")?.toLong()
                ?: request.url.queryParameter("courseId")!!.toLong())
            when (request.url.encodedPath) {
                "/visit/stucoursemiddle" -> ChxResponse(200, tabsHtml(current))
                "/mooc2/work/list" -> ChxResponse(200, workList(current))
                else -> if (current.courseId == 1L) ChxResponse(401, "") else ChxResponse(200, "<p>没有截止时间</p>")
            }
        }
        val results = ChxWorkReader(api, nowMillis = { now }).read(courses)
        assertTrue((results[0] as ChxCourseWorkResult.Failure).error is ChxAuthFailed)
        assertEquals(4, (results[1] as ChxCourseWorkResult.Success).items.size)
    }

    @Test fun eachCourseKeepsItsOwnMenuSignaturesAndReferer() = runBlocking {
        val courses = listOf(course(1), course(2))
        val api = ChxApi { request ->
            val selected = courses.single { it.courseId.toString() ==
                (request.url.queryParameter("courseid") ?: request.url.queryParameter("courseId")) }
            when (request.url.encodedPath) {
                "/visit/stucoursemiddle" -> ChxResponse(200, tabsHtml(selected)
                    .replace("synthetic-stu", "stu-${selected.courseId}")
                    .replace("synthetic-work", "work-${selected.courseId}") +
                    "<a data-url='https://mooc1.chaoxing.com/mooc2/work/list?enc=work-${selected.courseId}'>作业</a>")
                "/mooc2/work/list" -> {
                    assertEquals("stu-${selected.courseId}", request.url.queryParameter("stuenc"))
                    assertEquals(listOf("work-${selected.courseId}"), request.url.queryParameterValues("enc"))
                    assertEquals(selected.url, request.header("Referer"))
                    ChxResponse(200, workList(selected, completedOnly = true))
                }
                else -> error("不能把进度或其他课程入口用作作业")
            }
        }
        assertTrue(ChxWorkReader(api).read(courses).all { it is ChxCourseWorkResult.Success })
    }

    @Test fun detailVerificationIsReportedAndTheNextReadFetchesFreshCourseParameters() = runBlocking {
        var blocked = true
        var pages = 0
        val selected = course(1)
        val api = ChxApi { request ->
            when (request.url.encodedPath) {
                "/visit/stucoursemiddle" -> { pages++; ChxResponse(200, tabsHtml(selected)) }
                "/mooc2/work/list" -> ChxResponse(200, workList(selected))
                else -> ChxResponse(200, if (blocked) """{"code":9010}""" else "<p>没有截止时间</p>")
            }
        }
        val reader = ChxWorkReader(api)
        val failure = reader.read(listOf(selected)).single() as ChxCourseWorkResult.Failure
        assertTrue((failure.error as ChxCourseReadException).verificationRequired)
        blocked = false
        assertTrue(reader.read(listOf(selected)).single() is ChxCourseWorkResult.Success)
        assertEquals(2, pages)
    }

    @Test fun cancellationIsNeverConvertedToAPartialCourseFailure() {
        for (cancelPath in listOf("/visit/stucoursemiddle", "/mooc2/work/list", "/mooc2/work/task")) {
            val cancelled = CancellationException("合成取消")
            val api = ChxApi { request ->
                if (request.url.encodedPath == cancelPath) throw cancelled
                ChxResponse(200, if (request.url.encodedPath == "/visit/stucoursemiddle") tabsHtml(course(1)) else workList(course(1)))
            }
            val failure = assertThrows(CancellationException::class.java) {
                runBlocking { ChxWorkReader(api).read(listOf(course(1))) }
            }
            assertTrue(generateSequence<Throwable>(failure) { it.cause }.any { it === cancelled })
        }
    }

    @Test fun externalCacheIsOptionalAndUsesTheCompleteCourseIdentity() = runBlocking {
        val first = course(1)
        val second = first.copy(cpi = 999, url = first.url.replace("cpi=201", "cpi=999"))
        val stored = mutableMapOf<ChxWorkCourse, ChxWorkTabs>()
        val lookups = mutableListOf<ChxWorkCourse>()
        val cache = object : ChxWorkTabsCache {
            override suspend fun get(course: ChxWorkCourse): ChxWorkTabs? {
                lookups += course
                return stored[course]
            }
            override suspend fun put(course: ChxWorkCourse, tabs: ChxWorkTabs) { stored[course] = tabs }
        }
        var pages = 0
        val api = ChxApi { request ->
            val selected = if ((request.url.queryParameter("cpi")) == "999") second else first
            when (request.url.encodedPath) {
                "/visit/stucoursemiddle" -> { pages++; ChxResponse(200, tabsHtml(selected)) }
                "/mooc2/work/list" -> ChxResponse(200, workList(selected, completedOnly = true))
                else -> error("已完成作业无需详情")
            }
        }
        val reader = ChxWorkReader(api, tabsCache = cache, parallelism = 1)
        assertTrue(reader.read(listOf(first, second)).all { it is ChxCourseWorkResult.Success })
        assertTrue(reader.read(listOf(first, second)).all { it is ChxCourseWorkResult.Success })
        assertEquals(2, pages)
        assertEquals(setOf(first, second), stored.keys)
        assertEquals(listOf(first, second, first, second), lookups)
    }

    @Test fun sessionChangeStopsResultsAndPreventsStaleCacheWrites() {
        var active = true
        var writes = 0
        val api = ChxApi {
            active = false
            ChxResponse(200, tabsHtml(course(1)))
        }
        val cache = object : ChxWorkTabsCache {
            override suspend fun get(course: ChxWorkCourse): ChxWorkTabs? = null
            override suspend fun put(course: ChxWorkCourse, tabs: ChxWorkTabs) { writes++ }
        }
        assertThrows(CancellationException::class.java) { runBlocking {
            ChxWorkReader(api, tabsCache = cache, checkSession = {
                if (!active) throw CancellationException("会话变更")
            }).read(listOf(course(1)))
        } }
        assertEquals(0, writes)
    }

    @Test fun parallelReadsAreBoundedAndReturnInputOrder() = runBlocking {
        val active = AtomicInteger()
        val peak = AtomicInteger()
        val twoStarted = CompletableDeferred<Unit>()
        val api = ChxApi { request ->
            val selected = course(request.url.queryParameter("courseid")?.toLong()
                ?: request.url.queryParameter("courseId")!!.toLong())
            if (request.url.encodedPath == "/visit/stucoursemiddle") {
                val count = active.incrementAndGet()
                peak.accumulateAndGet(count, ::maxOf)
                if (count == 2) twoStarted.complete(Unit)
                try {
                    withTimeout(5_000) { twoStarted.await() }
                    delay(5)
                    ChxResponse(200, tabsHtml(selected))
                } finally { active.decrementAndGet() }
            } else ChxResponse(200, workList(selected, completedOnly = true))
        }
        val courses = (1L..5L).map(::course)
        val results = ChxWorkReader(api, parallelism = 2).read(courses)
        assertEquals(2, peak.get())
        assertEquals(courses, results.map { it.course })
        assertTrue(results.all { it is ChxCourseWorkResult.Success })
    }

    private fun course(id: Long) = ChxWorkCourse(id, id + 100, id + 200, "合成课程$id",
        "https://mooc1.chaoxing.com/visit/stucoursemiddle?courseid=$id&clazzid=${id + 100}&cpi=${id + 200}&ismooc2=1&v=2")

    private fun courseList(courses: List<ChxWorkCourse>) = "<div id='courseList'>" + courses.joinToString("") {
        """<li class='course learnCourse' info='${it.classId}_${it.cpi}'>
        <input type='hidden' class='clazzId' value='${it.classId}'>
        <input type='hidden' class='courseId' value='${it.courseId}'>
        <input type='hidden' class='curPersonId' value='${it.cpi}'>
        <input type='hidden' class='role' value='0'>
        <a href='${it.url}'><div class='course-info'><span class='course-name' title='${it.name}'></span></div></a></li>"""
    } + "</div>"

    private fun tabsHtml(course: ChxWorkCourse) = """
        <input type='hidden' id='courseId' value='${course.courseId}'>
        <input type='hidden' id='classId' value='${course.classId}'>
        <input type='hidden' id='cpi' value='${course.cpi}'>
        <input type='hidden' id='enc' value='synthetic-stu'>
        <input type='hidden' id='workEnc' value='synthetic-work'>
        <input type='hidden' id='t' value='1700000000000'>
        <input type='hidden' id='moocDomainName' value='https://mooc1.chaoxing.com'>
    """.trimIndent()

    private fun workList(course: ChxWorkCourse, completedOnly: Boolean = false): String =
        "<div id='workList'><ul>" + (if (completedOnly) listOf(404) else (401..404).toList()).joinToString("") { id ->
            """<li data='https://mooc1.chaoxing.com/mooc2/work/task?courseId=${course.courseId}&classId=${course.classId}&cpi=${course.cpi}&workId=$id&answerId=${id + 100}&enc=synthetic'>
            <p class='overHidden2'>合成作业$id</p><p class='status'>${if (id == 404) "已完成" else "未交"}</p>
            <div class='time'>${if (id == 403) "" else "剩余5分钟"}</div></li>"""
        } + "</ul></div>"
}
