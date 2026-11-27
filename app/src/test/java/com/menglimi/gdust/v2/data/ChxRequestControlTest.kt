package com.menglimi.gdust.v2.data

import com.menglimi.chaoxing.ChxCourseReadException
import com.menglimi.chaoxing.ChxRequestGate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 全内存合成响应，不访问 CookieManager、网络或账户。 */
class ChxRequestControlTest {
    private val captcha = SessionResponse(200, """{"code":9010,"msg":"验证码"}""", null)

    @Test fun firstPauseStopsQueuedReadsBeforeTransport() = runBlocking {
        val control = ChxRequestControl(ChxRequestGate(intervalMillis = 0))
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        var sent = 0
        val first = async {
            runCatching {
                control.read<Unit>(1, {}) { owner, check ->
                    control.execute(owner, check) {
                        sent++
                        started.complete(Unit)
                        release.await()
                        captcha
                    }
                }
            }.exceptionOrNull()
        }
        started.await()
        val queued = async {
            runCatching {
                control.read<Unit>(1, {}) { owner, check ->
                    control.execute(owner, check) {
                        sent++
                        SessionResponse(200, "不应发送", null)
                    }
                }
            }.exceptionOrNull()
        }
        yield()
        release.complete(Unit)
        val firstFailure = first.await()
        val queuedFailure = queued.await()
        assertTrue("first=$firstFailure queued=$queuedFailure sent=$sent pause=${control.pause.value}",
            firstFailure is ChxCourseReadException)
        assertTrue("first=$firstFailure queued=$queuedFailure sent=$sent pause=${control.pause.value}",
            queuedFailure is ChxCourseReadException)
        assertEquals(1, sent)
        assertTrue(control.pause.value!!.verificationRequired)
    }

    @Test fun lateResponseAfterCancelledReadKeepsOriginalRecoveryTarget() = runBlocking {
        val control = ChxRequestControl(ChxRequestGate(intervalMillis = 0))
        val started = CompletableDeferred<Any>()
        var reads = 0
        val first = launch {
            try {
                control.read<Long>(1, {}) { owner, check ->
                    reads++
                    control.execute(owner, check) {
                        if (reads == 1) {
                            started.complete(owner)
                            awaitCancellation()
                        }
                        SessionResponse(200, "合成详情", null)
                    }
                    1L
                }
            } catch (_: CancellationException) { }
        }
        val owner = started.await()
        first.cancelAndJoin()
        assertNull(control.pause.value)
        // 真实 HTTP 回调可能在业务 finally 退出后才到达。
        control.recordResponse(owner, captcha)
        assertTrue(control.pause.value!!.verificationRequired)
        control.recover(1, {}, afterWebVerification = true)
        assertEquals(2, reads)
        assertNull(control.pause.value)
    }

    @Test fun failedRecoveryValidationNeverClearsPause() = runBlocking {
        val control = ChxRequestControl(ChxRequestGate(intervalMillis = 0))
        var loads = 0
        assertTrue(runCatching {
            control.read<Int>(1, {}, validateRecovery = { it > 0 }) { owner, check ->
                loads++
                control.execute(owner, check) { captcha }
                0
            }
        }.isFailure)
        assertNotNull(control.pause.value)
        assertTrue(runCatching { control.recover(1, {}, true) }.isFailure)
        assertNotNull(control.pause.value)
        assertTrue(runCatching { control.recover(1, {}, true) }.isFailure)
        assertNotNull(control.pause.value)
        assertEquals(3, loads)
    }

    @Test fun resetDropsRecoveryTargetAndPause() = runBlocking {
        val control = ChxRequestControl(ChxRequestGate(intervalMillis = 0))
        assertTrue(runCatching {
            control.read<Long>(1, {}) { owner, check ->
                control.execute(owner, check) { captcha }
                1L
            }
        }.isFailure)
        assertNotNull(control.pause.value)
        control.reset()
        assertNull(control.pause.value)
        // reset 后即使重新收到暂停，也不能恢复已丢弃的旧恢复目标。
        control.recordResponse(Any(), captcha)
        assertNotNull(control.pause.value)
        assertTrue(runCatching { control.recover(1, {}, true) }.isFailure)
        control.reset()
        assertNull(control.pause.value)
    }

    @Test fun manualRecoverySuccessClearsPauseAndResetClearsRecovery() = runBlocking {
        val control = ChxRequestControl(ChxRequestGate(intervalMillis = 0))
        var reads = 0
        assertTrue(runCatching {
            control.read<Long>(1, {}, validateRecovery = { it > 0 }) { owner, check ->
                reads++
                control.execute(owner, check) {
                    if (reads == 1) captcha else SessionResponse(200, "合成详情", null)
                }
                1L
            }
        }.isFailure)
        control.recover(1, {}, true)
        assertNull(control.pause.value)
        assertEquals(2, reads)
    }
}
