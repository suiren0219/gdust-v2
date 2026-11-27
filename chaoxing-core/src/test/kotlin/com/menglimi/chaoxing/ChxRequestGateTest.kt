package com.menglimi.chaoxing

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ChxRequestGateTest {
    @Test fun retryAfterSupportsSecondsHttpDatesAndMissingHeaders() {
        val now = 1_000_000L
        assertEquals(now + 120_000, ChxResponse(429, "", retryAfter = "120").requestPause(now)!!.retryAt)
        assertEquals(now + 90_000, ChxResponse(429, "", retryAfter = "Tue, 17 Nov 2026 10:01:30 GMT",
            serverDate = "Tue, 17 Nov 2026 10:00:00 GMT").requestPause(now)!!.retryAt)
        for (value in listOf(null, "bad", "-1")) {
            assertEquals(now + 60_000, ChxResponse(429, "private", retryAfter = value).requestPause(now)!!.retryAt)
        }
        assertEquals(Long.MAX_VALUE, ChxResponse(429, "", retryAfter = Long.MAX_VALUE.toString()).requestPause(now)!!.retryAt)
        assertNull(ChxResponse(403, "拒绝访问").requestPause(now))
        assertFalse(ChxResponse(429, "private").requestPause(now)!!.exception().message!!.contains("private"))
    }

    @Test fun rateLimitAndVerificationPauseCanBeCombined() {
        val pause = ChxResponse(429, """{"errorCode":"9010"}""", retryAfter = "30")
            .requestPause(now = 1_000L)!!
        assertTrue(pause.verificationRequired)
        assertEquals(31_000L, pause.retryAt)
    }

    @Test fun cookieWriteFailurePreservesResponsePause() {
        val cause = IllegalStateException("synthetic")
        val failure = ChxCookieWriteException(verificationRequired = true, retryAt = 31_000L, cause = cause)
        assertTrue(failure.verificationRequired)
        assertEquals(31_000L, failure.retryAt)
        assertEquals(cause, failure.cause)
        val gate = ChxRequestGate(intervalMillis = 0)
        gate.record(failure, "account")
        assertEquals(ChxRequestPause(true, 31_000L), gate.pause.value)
    }

    @Test fun verifyPromptPageVariantsPauseReads() {
        // processVerifyPng 提示页：输入框名为 ucode，正文只有"操作异常，请输入图片中的验证码"。
        val prompt = ChxResponse(200, """<html><body><h3>提示页面</h3>
            <p>【9010】操作异常，请输入图片中的验证码</p>
            <img src="processVerifyPng"><form action="verify"><input type="text" id="ucode" name="ucode">
            <input type="submit" value="提交"></form></body></html>""")
        assertTrue(prompt.requestPause()!!.verificationRequired)
        // 无标准输入字段的正文变体：9010 与验证码文案同时出现。
        val flagged = ChxResponse(200, "<html><body><h3>提示页面</h3><p>【9010】操作异常，请输入图片中的验证码</p>"
            + """<img src="processVerifyPng"><input type="submit" value="提交"></body></html>""")
        assertTrue(flagged.requestPause()!!.verificationRequired)
        // 普通页面提到验证码但没有对应输入框或 9010 组合，不能当成风控。
        val normal = ChxResponse(200, "<html><body><p>课程讨论：如何找回验证码？</p></body></html>")
        assertNull(normal.requestPause())
    }

    @Test fun responsesPauseQueuedRequestsBeforeAnyMoreNetworkCalls() = runBlocking {
        val gate = ChxRequestGate(intervalMillis = 0)
        val started = CompletableDeferred<Unit>()
        val response = CompletableDeferred<ChxResponse>()
        var calls = 0
        val first = async { runCatching { gate.execute("work", {}) { calls++; started.complete(Unit); response.await() } } }
        started.await()
        val queued = async { runCatching { gate.execute("progress", {}) { calls++; ChxResponse(200, "") } } }
        yield()
        response.complete(ChxResponse(200, """{"code":9010}"""))
        assertTrue((first.await().exceptionOrNull() as ChxCourseReadException).verificationRequired)
        assertTrue((queued.await().exceptionOrNull() as ChxCourseReadException).verificationRequired)
        assertEquals(1, calls)
        assertTrue(gate.causedPause("work"))
        assertFalse(gate.causedPause("progress"))
    }

    @Test fun cooldownRequiresManualRecoveryAndFailedCheckKeepsThePause() = runBlocking {
        var now = 10_000L
        val gate = ChxRequestGate({ now }, intervalMillis = 0)
        runCatching { gate.execute("first", {}) { ChxResponse(429, "", retryAfter = "2") } }
        assertThrows(ChxCourseReadException::class.java) { gate.beginRecovery("early", true) }
        now += 2_000
        assertThrows(ChxCourseReadException::class.java) { gate.check("normal") }
        gate.beginRecovery("recheck", false)
        assertThrows(ChxCourseReadException::class.java) { gate.check("other") }
        gate.execute("recheck", {}) { ChxResponse(200, "") }
        gate.finishRecovery("recheck", false)
        assertNotNull(gate.pause.value)
        gate.beginRecovery("again", false)
        gate.execute("again", {}) { ChxResponse(200, "") }
        gate.finishRecovery("again", true)
        assertNull(gate.pause.value)
    }

    @Test fun verificationCannotBeClearedByRefreshOrAFailedVerification() = runBlocking {
        val gate = ChxRequestGate(intervalMillis = 0)
        gate.restore(ChxRequestPause(true))
        assertThrows(ChxCourseReadException::class.java) { gate.beginRecovery("refresh", false) }
        gate.beginRecovery("verified", true)
        runCatching { gate.execute("verified", {}) { ChxResponse(200, """{"errorCode":"9010"}""") } }
        gate.finishRecovery("verified", true)
        assertTrue(gate.pause.value!!.verificationRequired)
    }

    @Test fun requestsAreSpacedAndCancellationNeverReleasesAPause() = runBlocking {
        var now = 10_000L
        val waits = mutableListOf<Long>()
        val gate = ChxRequestGate({ now }, wait = { waits += it; now += it })
        gate.execute("one", {}) { ChxResponse(200, "") }
        gate.execute("two", {}) { ChxResponse(200, "") }
        assertEquals(listOf(1_000L), waits)
        gate.restore(ChxRequestPause(true))
        gate.beginRecovery("cancelled", true)
        val failure = runCatching { gate.execute("cancelled", {}) { throw CancellationException("cancel") } }.exceptionOrNull()
        assertTrue(failure is CancellationException)
        gate.finishRecovery("cancelled", false)
        assertTrue(gate.pause.value!!.verificationRequired)
        assertThrows(ChxCourseReadException::class.java) { gate.check() }
        Unit
    }
}
