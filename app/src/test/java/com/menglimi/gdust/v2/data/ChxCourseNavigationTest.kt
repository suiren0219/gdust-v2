package com.menglimi.gdust.v2.data

import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** 合成壳页响应回归，不访问学习通，也不代表真实课程响应已完成回放。 */
class ChxCourseNavigationTest {
    private val course = ChxWorkCourse(201, 101, 301, "合成课程",
        "https://mooc1.chaoxing.com/visit/stucoursemiddle?courseid=201&clazzid=101&cpi=301&ismooc2=1&v=2")
    private val landing = "https://mooc1.chaoxing.com/mooc-ans/mycourse/studentcourse?courseid=201&clazzid=101&cpi=301"
    private val body = """
        <input type="hidden" id="courseid" value="201">
        <input type="hidden" id="clazzid" value="101">
        <input type="hidden" id="cpi" value="301">
        <input type="hidden" id="enc" value="synthetic-stu">
        <input type="hidden" id="workEnc" value="synthetic-work">
        <input type="hidden" id="t" value="1700000000000">
        <input type="hidden" id="moocDomainName" value="https://mooc1.chaoxing.com">
    """.trimIndent()

    @Test fun staticRedirectMetaAndFrameShellsUseDesktopCookieFreeRequests() = runBlocking {
        for (shell in listOf(
            "<script>window.location.href = '$landing';</script>",
            "<script>top.location.replace('$landing');</script>",
            "<script>location.assign('$landing')</script>",
            "<meta http-equiv='refresh' content='0;url=$landing'>",
            "<iframe src='$landing'></iframe>",
        )) {
            val requests = mutableListOf<Request>()
            val api = ChxApi { request ->
                requests += request
                SessionResponse(200, if (requests.size == 1) shell else body, null)
            }
            val tabs = api.courseTabs(course)
            assertEquals("synthetic-stu", tabs.enc)
            assertEquals("synthetic-work", tabs.workEnc)
            assertEquals(landing, tabs.coursePageUrl)
            assertEquals(listOf(course.url, landing), requests.map { it.url.toString() })
            assertTrue(requests.all { it.header("User-Agent") == ChxApi.USER_AGENT })
            assertTrue(requests.all { it.header("Cookie") == null && it.header("Referer") == ChxSso.HOME })
        }
    }

    @Test fun missingWorkSignatureFollowsBodyWithoutReusingThePageSignature() = runBlocking {
        val requests = mutableListOf<Request>()
        val shell = body.replace("id=\"workEnc\"", "id=\"other\"") + "<iframe src='$landing'></iframe>"
        val api = ChxApi { request ->
            requests += request
            when (requests.size) {
                1 -> SessionResponse(200, shell, null)
                2 -> SessionResponse(200, body, null)
                else -> SessionResponse(200, "<div id='workList'><li data='/mooc2/work/task?courseId=201&amp;classId=101&amp;cpi=301&amp;workId=401&amp;answerId=0&amp;enc=synthetic-detail'>" +
                    "<p class='overHidden2'>合成作业</p><p class='status'>未交</p></li></div>", null)
            }
        }
        val tabs = api.courseTabs(course)
        assertEquals("合成作业", api.workList(course, tabs).single().title)
        assertEquals("synthetic-stu", requests.last().url.queryParameter("stuenc"))
        assertEquals("synthetic-work", requests.last().url.queryParameter("enc"))
        assertEquals(landing, requests.last().header("Referer"))
    }

    @Test fun expressionsTemplatesCommentsAndExternalScriptsCannotTriggerNavigation() {
        for (shell in listOf(
            "<script>location.href = '$landing' + '&enc=synthetic';</script>",
            "<script>if (false) { location.href = '$landing'; }</script>",
            "<script>location.href = `$landing`;</script>",
            "<template><iframe src='$landing'></iframe></template>",
            "<noscript><iframe src='$landing'></iframe></noscript>",
            "<!-- <script>location.href = '$landing';</script> -->",
            "<script>// location.href = '$landing';</script>",
            "<script>/* location.href = '$landing'; */</script>",
            "<script src='/navigation.js'>location.href = '$landing';</script>",
        )) assertNull(ChxCourseNavigation.next(shell, course.url.toHttpUrl(), course))
    }

    @Test fun relativeFramesIgnoreHtmlBaseAndKnownHttpTargetsUpgradeToHttps() {
        val relative = "/mooc-ans/mycourse/studentcourse?courseid=201&clazzid=101&cpi=301"
        assertEquals(landing, ChxCourseNavigation.next(
            "<base href='https://unknown.example/'><iframe src='$relative'></iframe>", course.url.toHttpUrl(), course)?.toString())
        assertEquals(landing, ChxCourseNavigation.next(
            "<script>location.href='${landing.replace("https:", "http:")}';</script>", course.url.toHttpUrl(), course)?.toString())
    }

    @Test fun untrustedCrossCourseDuplicateAndConflictingTargetsStopBeforeAnotherRequest() {
        for (shell in listOf(
            "<script>location.href='https://unknown.example/mycourse/stu?enc=private';</script>",
            "<script>location.href='${landing.replace("courseid=201", "courseid=999")}';</script>",
            "<script>location.href='${landing.replace("clazzid=101", "clazzid=999")}';</script>",
            "<script>location.href='${landing.replace("cpi=301", "cpi=999")}';</script>",
            "<script>location.href='$landing&courseid=201';</script>",
            "<script>location.href='$landing&classId=999';</script>",
            "<script>location.href='${landing.replace(".com/", ".com:8443/")}';</script>",
            "<script>location.href='${landing.replace("/mooc-ans/", "/other/../mooc-ans/")}';</script>",
            "<script>location.href='$landing#private';</script>",
            "<script>location.href='https://mooc1.chaoxing.com/unknown?enc=private';</script>",
            "<meta http-equiv='refresh' content='0;url=$landing'><script>location.href='${landing.replace("studentcourse?", "stu?")}';</script>",
            "<iframe src='$landing'></iframe><iframe src='${landing.replace("studentcourse?", "stu?")}'></iframe>",
        )) {
            var requests = 0
            val failure = assertThrows(ChxCourseReadException::class.java) { runBlocking {
                ChxApi { requests++; SessionResponse(200, shell, null) }.courseTabs(course)
            } }
            assertEquals(1, requests)
            assertFalse(failure.message.orEmpty().contains("private"))
            assertFalse(failure.message.orEmpty().contains("999"))
        }
    }

    @Test fun missingFieldsCannotMaskIdentityOrSignatureConflictsBehindAFrame() {
        for (invalid in listOf(
            body.replace("id=\"courseid\"", "id=\"other\"").replace("value=\"101\"", "value=\"999\""),
            body.replace("id=\"enc\"", "id=\"other\"") + "<input type='hidden' id='workEnc' value='private'>",
            body.replace("id=\"moocDomainName\"", "id=\"other\"").replace("value=\"synthetic-work\"", "value=\"\""),
        )) {
            var requests = 0
            val failure = assertThrows(ChxCourseReadException::class.java) { runBlocking {
                ChxApi {
                    requests++
                    SessionResponse(200, invalid + "<iframe src='$landing'></iframe>", null)
                }.courseTabs(course)
            } }
            assertEquals(1, requests)
            assertNull(ChxWorkParser.missingField(failure))
        }
    }

    @Test fun finalLandingIdentityStillRejectsCrossCourseRedirects() {
        var requests = 0
        val failure = assertThrows(ChxCourseReadException::class.java) { runBlocking {
            ChxApi {
                requests++
                when (requests) {
                    1 -> SessionResponse(200, "<iframe src='$landing'></iframe>", null)
                    2 -> SessionResponse(302, "", landing.replace("courseid=201", "courseid=999"))
                    else -> SessionResponse(200, body, null)
                }
            }.courseTabs(course)
        } }
        assertEquals(3, requests)
        assertTrue(failure.message.orEmpty().contains("courseid"))
        assertFalse(failure.message.orEmpty().contains("999"))
    }

    @Test fun bodyLoopsAndNavigationDepthRemainBounded() {
        var requests = 0
        val loop = assertThrows(ChxCourseReadException::class.java) { runBlocking {
            ChxApi { requests++; SessionResponse(200, "<script>location.href='${course.url}';</script>", null) }
                .courseTabs(course)
        } }
        assertEquals(1, requests)
        assertTrue(loop.message.orEmpty().contains("循环跳转"))
        requests = 0
        val tooMany = assertThrows(ChxCourseReadException::class.java) { runBlocking {
            ChxApi {
                requests++
                SessionResponse(200, "<iframe src='$landing&hop=$requests'></iframe>", null)
            }.courseTabs(course)
        } }
        assertEquals(4, requests)
        assertTrue(tooMany.message.orEmpty().contains("跳转次数过多"))
    }

    @Test fun unknownShellDiagnosticsContainOnlyRouteAndFixedStructureFlags() {
        val message = assertThrows(ChxCourseReadException::class.java) { runBlocking {
            ChxApi { SessionResponse(200, "<p>private-name</p>", null) }.courseTabs(course)
        } }.message.orEmpty()
        assertTrue(message.contains("落地 https://mooc1.chaoxing.com/visit/stucoursemiddle"))
        assertTrue(message.contains("courseid=无"))
        assertTrue(message.contains("enc=无"))
        assertTrue(message.contains("workEnc=无"))
        assertFalse(message.contains("201"))
        assertFalse(message.contains("private-name"))
    }
}
