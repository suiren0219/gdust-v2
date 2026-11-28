package com.menglimi.study.learning

import com.menglimi.chaoxing.ChxCookieWriteException
import com.menglimi.chaoxing.ChxCourseReadException
import com.menglimi.chaoxing.ChxHttpResult
import com.menglimi.chaoxing.ChxRequestGate
import com.menglimi.chaoxing.ChxResponse
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class LearningCookieWriteTest {
    @Test fun successfulCallbackResumesOnlyAfterWriteCompletes() = runTest {
        var callback: ((Boolean) -> Unit)? = null
        var resumed = false
        val write = launch {
            awaitLearningCookieWrite { callback = it }
            resumed = true
        }
        runCurrent()
        assertFalse(resumed)
        assertFalse(write.isCompleted)
        checkNotNull(callback)(true)
        write.join()
        assertTrue(resumed)
    }

    @Test fun failedCallbackThrowsCookieWriteException() = runTest {
        try {
            awaitLearningCookieWrite { callback -> callback(false) }
            fail("Cookie 写入失败后不应正常恢复")
        } catch (failure: ChxCookieWriteException) {
            assertEquals("会话更新失败，请重试", failure.message)
        }
    }

    @Test fun failedCookieWriteKeepsCombinedPauseAndGateBlocksNextRequest() = runTest {
        val gate = ChxRequestGate(now = { 1_000L }, intervalMillis = 0)
        val received = ChxHttpResult(ChxResponse(429, """{"code":9010,"msg":"请输入验证码"}""",
            retryAfter = "30"), listOf("synthetic=value; Path=/; Secure"))
        var requests = 0
        var flushes = 0
        var writeFailure: ChxCookieWriteException? = null
        try {
            gate.execute("first", {}) {
                requests++
                commitLearningResponseCookies(received, {}, { _, callback -> callback(false) }, { flushes++ }, now = 1_000L)
                received.response
            }
            fail("Cookie 写入失败后不应返回响应")
        } catch (failure: ChxCookieWriteException) {
            writeFailure = failure
        }
        assertTrue(checkNotNull(writeFailure).verificationRequired)
        assertEquals(31_000L, checkNotNull(writeFailure).retryAt)
        assertEquals(1, flushes)
        assertTrue(checkNotNull(gate.pause.value).verificationRequired)
        assertEquals(31_000L, checkNotNull(gate.pause.value).retryAt)
        try {
            gate.execute("next", {}) { requests++; ChxResponse(200, "不应请求") }
            fail("暂停后不应发送下一请求")
        } catch (_: ChxCourseReadException) { }
        assertEquals(1, requests)
    }

    @Test fun thrownWriteAndFlushFailuresKeepResponsePause() = runTest {
        val received = ChxHttpResult(ChxResponse(429, """{"errorCode":"9010"}""", retryAfter = "30"),
            listOf("synthetic=value; Path=/; Secure"))
        for (failOnFlush in listOf(false, true)) {
            val cause = IOException("synthetic cookie failure")
            var flushes = 0
            try {
                commitLearningResponseCookies(received, {}, writeCookie = { _, callback ->
                    if (!failOnFlush) throw cause
                    callback(true)
                }, flush = { flushes++; if (failOnFlush) throw cause }, now = 1_000L)
                fail("提交失败后不应正常恢复")
            } catch (failure: ChxCookieWriteException) {
                assertSame(cause, failure.cause)
                assertTrue(failure.verificationRequired)
                assertEquals(31_000L, failure.retryAt)
            }
            assertEquals(1, flushes)
        }
    }

    @Test fun cancellationAndAccountChangeRemainUnwrappedAndStillFlush() = runTest {
        val received = ChxHttpResult(ChxResponse(429, """{"code":9010}""", retryAfter = "30"),
            listOf("synthetic=value; Path=/; Secure"))
        for (expected in listOf(CancellationException("synthetic cancellation"), LearningAccountChanged())) {
            for (stage in listOf("check", "write", "flush")) {
                var flushes = 0
                try {
                    commitLearningResponseCookies(received, checkSession = {
                        if (stage == "check") throw expected
                    }, writeCookie = { _, callback ->
                        if (stage == "write") throw expected
                        callback(true)
                    }, flush = {
                        flushes++
                        if (stage == "flush") throw expected
                    })
                    fail("会话检查失败后不应继续")
                } catch (failure: Exception) {
                    if (expected is CancellationException) {
                        assertTrue(failure is CancellationException)
                        assertEquals(expected.message, failure.message)
                        assertTrue(generateSequence<Throwable>(failure) { it.cause }.take(8).any { it === expected })
                    } else {
                        assertTrue(failure is LearningAccountChanged)
                        assertEquals(expected.message, failure.message)
                    }
                }
                assertEquals(1, flushes)
            }
        }
    }
}
