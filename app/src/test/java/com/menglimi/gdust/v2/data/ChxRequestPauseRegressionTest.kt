package com.menglimi.gdust.v2.data

import com.menglimi.chaoxing.ChxCookieWriteException
import com.menglimi.chaoxing.ChxRequestGate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import okhttp3.Request
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

/** 仅人工构造响应与错误，不读取 HAR、凭据、Cookie 或网络。 */
class ChxRequestPauseRegressionTest {
    private val homepage = """
        <input type="hidden" id="stuPageHeader" value="1">
        <input type="hidden" id="loadStuJobInfo" value="1">
        <input type="hidden" id="checkUserToken" value="0123456789abcdef0123456789abcdef">
    """.trimIndent()
    private val courses = """
        <div id="courseList"><div class="course learnCourse" info="101_301">
          <input type="hidden" class="clazzId" value="101">
          <input type="hidden" class="courseId" value="201">
          <input type="hidden" class="curPersonId" value="301">
          <input type="hidden" class="role" value="0">
          <div class="course-info"><span class="course-name" title="合成课程">合成课程</span></div>
          <a href="https://mooc1.chaoxing.com/visit/stucoursemiddle?courseid=201&clazzid=101&cpi=301&ismooc2=1&v=2">进入</a>
        </div></div>
    """.trimIndent()

    @Test fun executeCheckedPreservesTheSameTypedPauseAndItsCause() = runBlocking {
        val cause = IOException("合成失败")
        val failure = ChxCourseReadException("合成暂停", verificationRequired = true, retryAt = 123L)
            .apply { initCause(cause) }
        val actual = runCatching {
            ChxCourseApi.executeChecked({ throw failure }, Request.Builder().url(ChxSso.HOME).build(), "固定文案")
        }.exceptionOrNull()
        assertSame(failure, actual)
        assertSame(cause, actual!!.cause)
        assertTrue((actual as ChxCourseReadException).verificationRequired)
        assertEquals(123L, actual.retryAt)
    }

    @Test fun progressCaptchaAnd429NeverBecomeSuccessfulPartialOverview() = runBlocking {
        listOf(
            SessionResponse(200, """{"code":9010,"msg":"验证码"}""", null),
            SessionResponse(429, "请稍后", null, retryAfter = "1"),
        ).forEach { progress ->
            val failure = runCatching { ChxCourseApi.loadWith { request -> response(request, progress) } }.exceptionOrNull()
            assertTrue(failure is ChxCourseReadException)
            assertTrue((failure as ChxCourseReadException).pausesRequests)
        }
    }

    @Test fun cookieFailureInProgressNeverBecomesPartialOverview() = runBlocking {
        val failure = ChxCookieWriteException(cause = IOException("合成 Cookie 写入失败"))
        val actual = runCatching {
            ChxCourseApi.loadWith { request ->
                if (request.url.encodedPath.endsWith("stu-job-info")) throw failure
                response(request, SessionResponse(200, "", null))
            }
        }.exceptionOrNull()
        assertSame(failure, actual)
    }

    @Test fun cookieWriteFailureKeepsBothPauseSignalsAndTheGateRecordsThem() = runBlocking {
        val gate = ChxRequestGate(intervalMillis = 0)
        val control = ChxRequestControl(gate)
        val response = SessionResponse(429, """{"code":9010,"msg":"验证码"}""", null, "60")
        val cause = IOException("合成拒绝写入")
        val failure = runCatching {
            control.read(1, {}) { owner, check ->
                control.execute(owner, check) {
                    saveSessionResponseCookies(response, chx = true) { throw cause }
                    response
                }
            }
        }.exceptionOrNull()
        assertTrue(failure is ChxCookieWriteException)
        assertSame(cause, failure!!.cause)
        assertTrue((failure as ChxCookieWriteException).verificationRequired)
        assertTrue(failure.retryAt > 0)
        assertEquals("会话更新失败，请重试", failure.message)
        assertNotNull(gate.pause.value)
        assertTrue(gate.pause.value!!.verificationRequired)
        assertEquals(failure.retryAt, gate.pause.value!!.retryAt)
    }

    @Test fun cookieHelperPreservesOtherServicesAndExternalCancellation() = runBlocking {
        val response = SessionResponse(200, "合成响应", null)
        val ordinary = IOException("合成失败")
        assertSame(ordinary, runCatching {
            saveSessionResponseCookies(response, chx = false) { throw ordinary }
        }.exceptionOrNull())
        val cancelled = CancellationException("合成取消")
        assertSame(cancelled, runCatching {
            saveSessionResponseCookies(response, chx = true) { throw cancelled }
        }.exceptionOrNull())
    }

    private fun response(request: Request, progress: SessionResponse): SessionResponse = when {
        request.url.encodedPath.endsWith("interaction") -> SessionResponse(200, homepage, null)
        request.url.encodedPath.endsWith("courselistdata") -> SessionResponse(200, courses, null)
        else -> progress
    }
}
