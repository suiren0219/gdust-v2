package com.menglimi.chaoxing

import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.*
import org.junit.Test

/** 页面壳的合成响应回归；不声称已经回放用户当前课程的真实响应。 */
class ChxCourseNavigationTest {
    private val course = ChxWorkCourse(201, 101, 301, "合成课程",
        "https://mooc1.chaoxing.com/visit/stucoursemiddle?courseid=201&clazzid=101&cpi=301&ismooc2=1&v=2")
    private val landing = "https://mooc2-ans.chaoxing.com/mooc2-ans/mycourse/stu?courseid=201&clazzid=101&cpi=301"
    private val body = """
        <input type="hidden" id="courseid" value="201">
        <input type="hidden" id="clazzid" value="101">
        <input type="hidden" id="cpi" value="301">
        <input type="hidden" id="enc" value="synthetic-stu">
        <input type="hidden" id="workEnc" value="synthetic-work">
        <input type="hidden" id="t" value="1700000000000">
        <input type="hidden" id="moocDomainName" value="https://mooc1.chaoxing.com">
    """.trimIndent()

    @Test fun staticRedirectShellsAreFollowedWithoutExecutingScripts() = runBlocking {
        val shells = listOf(
            "<script>window.location.href = '$landing';</script>",
            "<script>top.location.replace('$landing');</script>",
            "<script>location.assign('$landing')</script>",
            "<meta http-equiv='refresh' content='0;url=$landing'>",
            "<iframe src='$landing'></iframe>",
        )
        for (shell in shells) {
            val paths = mutableListOf<String>()
            val api = ChxApi { request ->
                paths += request.url.toString()
                assertNull(request.header("Cookie"))
                if (paths.size == 1) ChxResponse(200, shell) else ChxResponse(200, body)
            }
            assertEquals("synthetic-work", api.courseTabs(course).workEnc)
            assertEquals(listOf(course.url, landing), paths)
        }
    }

    @Test fun expressionsTemplatesAndConditionalCodeAreNotExecuted() {
        val shells = listOf(
            "<script>location.href = '$landing' + '&enc=synthetic';</script>",
            "<script>if (false) { location.href = '$landing'; }</script>",
            "<template><iframe src='$landing'></iframe></template>",
            "<!-- <script>location.href = '$landing';</script> -->",
            "<script>// location.href = '$landing';</script>",
        )
        for (shell in shells) assertNull(ChxCourseNavigation.next(shell, course.url.toHttpUrl(), course))
    }

    @Test fun foreignConflictingAndCrossCourseTargetsCannotIssueRequests() {
        val shells = listOf(
            "<script>location.href='https://unknown.example/mycourse/stu?enc=private';</script>",
            "<script>location.href='${landing.replace("courseid=201", "courseid=999")}';</script>",
            "<meta http-equiv='refresh' content='0;url=$landing'><script>location.href='${landing.replace("stu?", "studentcourse?")}';</script>",
        )
        for (shell in shells) {
            var requests = 0
            val failure = assertThrows(ChxCourseReadException::class.java) { runBlocking {
                ChxApi { requests++; ChxResponse(200, shell) }.courseTabs(course)
            } }
            assertEquals(1, requests)
            assertFalse(failure.message.orEmpty().contains("private"))
            assertFalse(failure.message.orEmpty().contains("999"))
        }
    }

    @Test fun conflictingBodyIdentityCannotBeHiddenBehindAnotherFrame() {
        var requests = 0
        assertThrows(ChxCourseReadException::class.java) { runBlocking {
            ChxApi {
                requests++
                ChxResponse(200, body.replace("value=\"201\"", "value=\"999\"") + "<iframe src='$landing'></iframe>")
            }.courseTabs(course)
        } }
        assertEquals(1, requests)
    }

    @Test fun loopsAndUnknownPageDiagnosticsRemainBoundedAndRedacted() {
        var requests = 0
        assertThrows(ChxCourseReadException::class.java) { runBlocking {
            ChxApi { requests++; ChxResponse(200, "<script>location.href='${course.url}';</script>") }.courseTabs(course)
        } }
        assertEquals(1, requests)
        val message = assertThrows(ChxCourseReadException::class.java) { runBlocking {
            ChxApi { ChxResponse(200, "<p>private-name</p>") }.courseTabs(course)
        } }.message.orEmpty()
        assertTrue(message.contains("落地 https://mooc1.chaoxing.com/visit/stucoursemiddle"))
        assertTrue(message.contains("courseid=无"))
        assertTrue(message.contains("workEnc=无"))
        assertFalse(message.contains("201"))
        assertFalse(message.contains("private-name"))
    }
}
