package com.menglimi.gdust.v2.data

import com.menglimi.chaoxing.ChxRequestGate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** 全部使用内存响应；不创建 HTTP 客户端、不访问 CookieManager 或网络。 */
class ChxRequestControlTest {
    private val captcha = SessionResponse(200, """{"code":9010,"msg":"验证码"}""", null)

    @Test fun firstPauseStopsQueuedCoursesAndTheirTransportIsNeverCalled() = runBlocking {
        val control = ChxRequestControl(ChxRequestGate(intervalMillis = 0))
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        var sent = 0
        coroutineScope {
            val first = async {
                runCatching {
                    control.read(1, {}) { owner, check ->
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
                    control.read(1, {}) { owner, check ->
                        control.execute(owner, check) {
                            sent++
                            SessionResponse(200, "不应发送", null)
                        }
                    }
                }.exceptionOrNull()
            }
            release.complete(Unit)
            assertTrue((first.await() as ChxCourseReadException).verificationRequired)
            assertTrue((queued.await() as ChxCourseReadException).verificationRequired)
        }
        assertEquals(1, sent)
        assertTrue(control.pause.value!!.verificationRequired)
    }

    @Test fun cancellationDuringCookieCommitKeepsPauseAndOriginalRecoveryTarget() = runBlocking {
        val control = ChxRequestControl(ChxRequestGate(intervalMillis = 0))
        val received = CompletableDeferred<Unit>()
        var detailReads = 0
        var listReads = 0
        var sent = 0
        var cancelled = false
        val first = launch {
            try {
                control.read(1, {}, validateRecovery = { it > 0L }) { owner, check ->
                    detailReads++
                    control.execute(owner, check) {
                        sent++
                        if (detailReads == 1) {
                            control.recordResponse(owner, captcha)
                            received.complete(Unit)
                            awaitCancellation()
                        }
                        SessionResponse(200, "合成详情", null)
                    }
                    123L
                }
            } catch (_: CancellationException) { cancelled = true }
        }
        received.await()
        val queued = async {
            runCatching {
                control.read(1, {}) { owner, check ->
                    control.execute(owner, check) {
                        listReads++; sent++
                        SessionResponse(200, "不应发送", null)
                    }
                }
            }.exceptionOrNull()
        }
        first.cancelAndJoin()
        assertTrue(cancelled)
        assertTrue(queued.await() is ChxCourseReadException)
        assertTrue(control.pause.value!!.verificationRequired)
        assertEquals(1, sent)
        control.recover(1, {}, afterWebVerification = true)
        assertEquals(2, detailReads)
        assertEquals(0, listReads)
        assertEquals(2, sent)
        assertNull(control.pause.value)
    }

    @Test fun responseArrivingAfterCancelledReadKeepsItsRecoveryTarget() = runBlocking {
        val control = ChxRequestControl(ChxRequestGate(intervalMillis = 0))
        val started = CompletableDeferred<Any>()
        var detailReads = 0
        var listReads = 0
        val first = launch {
            control.read<Long>(1, {}, validateRecovery = { it > 0L }) { owner, check ->
                detailReads++
                control.execute(owner, check) {
                    if (detailReads == 1) {
                        started.complete(owner)
                        awaitCancellation()
                    }
                    SessionResponse(200, "合成详情", null)
                }
                123L
            }
        }
        val owner = started.await()
        first.cancelAndJoin()
        assertTrue(first.isCancelled)
        assertNull(control.pause.value)
        // HTTP 回调在业务 finally 完成后才记录已收到的响应。
        control.recordResponse(owner, captcha)
        assertTrue(control.pause.value!!.verificationRequired)
        assertTrue(runCatching {
            control.read(1, {}) { queuedOwner, check ->
                control.execute(queuedOwner, check) { listReads++; SessionResponse(200, "不应发送", null) }
            }
        }.isFailure)
        assertTrue(runCatching { control.recover(2, {}, true) }.isFailure)
        assertEquals(1, detailReads)
        control.recover(1, {}, afterWebVerification = true)
        assertEquals(2, detailReads)
        assertEquals(0, listReads)
        assertNull(control.pause.value)
    }

    @Test fun queuedListCannotReplaceBlockedDetailAndOnlyValidatedDetailResumes() = runBlocking {
        val control = ChxRequestControl(ChxRequestGate(intervalMillis = 0))
        var detailReads = 0
        var listReads = 0
        var validDetail = false
        runCatching {
            control.read(1, {}, validateRecovery = { it > 0L }) { owner, check ->
                detailReads++
                control.execute(owner, check) {
                    if (detailReads == 1) captcha else SessionResponse(200, "详情", null)
                }
                if (validDetail) 123L else 0L
            }
        }
        runCatching {
            control.read(1, {}) { owner, check ->
                control.execute(owner, check) { listReads++; SessionResponse(200, "列表", null) }
            }
        }
        val failure = runCatching { control.recover(1, {}, afterWebVerification = true) }.exceptionOrNull()
        assertTrue(failure is ChxCourseReadException)
        assertEquals(2, detailReads)
        assertEquals(0, listReads)
        assertNotNull(control.pause.value)
        validDetail = true
        control.recover(1, {}, afterWebVerification = true)
        assertEquals(3, detailReads)
        assertEquals(0, listReads)
        assertNull(control.pause.value)
    }

    @Test fun cancellationAndChangedAccountKeepPauseAndDoNotRunOtherBusiness() = runBlocking {
        val control = ChxRequestControl(ChxRequestGate(intervalMillis = 0))
        val cancelled = CancellationException("合成取消")
        var calls = 0
        runCatching {
            control.read(1, {}) { owner, check ->
                if (++calls > 1) throw cancelled
                control.execute(owner, check) { captcha }
            }
        }
        val accountFailure = runCatching { control.recover(2, {}, true) }.exceptionOrNull()
        assertTrue(accountFailure is ChxCourseReadException)
        assertEquals(1, calls)
        assertNotNull(control.pause.value)
        assertSame(cancelled, runCatching { control.recover(1, {}, true) }.exceptionOrNull())
        assertNotNull(control.pause.value)
        assertTrue(runCatching { control.check() }.exceptionOrNull() is ChxCourseReadException)
    }

    @Test fun missingBusinessContextCannotClearEvenAfterManualWebVerification() = runBlocking {
        val gate = ChxRequestGate(intervalMillis = 0)
        gate.record(ChxCourseReadException("合成暂停", verificationRequired = true))
        val control = ChxRequestControl(gate)
        val failure = runCatching { control.recover(1, {}, true) }.exceptionOrNull()
        assertTrue(failure is ChxCourseReadException)
        assertNotNull(control.pause.value)
    }

    @Test fun cooldownAndCaptchaMustBothBeSatisfiedAndTimeAloneNeverResumes() = runBlocking {
        var now = 1_000L
        val control = ChxRequestControl(ChxRequestGate(now = { now }, intervalMillis = 0))
        var sent = 0
        runCatching {
            control.read(1, {}) { owner, check ->
                control.execute(owner, check) {
                    if (++sent == 1) captcha.copy(status = 429, retryAfter = "2")
                    else SessionResponse(200, "成功", null)
                }
            }
        }
        assertTrue(control.pause.value!!.verificationRequired)
        assertEquals(3_000L, control.pause.value!!.retryAt)
        assertTrue(runCatching { control.recover(1, {}, true) }.isFailure)
        assertEquals(1, sent)
        now = 3_001L
        assertTrue(runCatching { control.check() }.isFailure)
        assertTrue(runCatching { control.recover(1, {}, false) }.isFailure)
        assertEquals(1, sent)
        control.recover(1, {}, true)
        assertEquals(2, sent)
        assertNull(control.pause.value)
    }

    @Test fun businessFailureAndCancellationAfterReadBothKeepPause() = runBlocking {
        val control = ChxRequestControl(ChxRequestGate(intervalMillis = 0))
        val failure = ChxCourseReadException("合成结构解析失败")
        var sends = 0
        var failParsing = true
        var readCompleted = false
        runCatching {
            control.read(1, {}) { owner, check ->
                control.execute(owner, check) {
                    if (++sends == 1) captcha else SessionResponse(200, "未知结构", null)
                }
                if (failParsing) throw failure
                readCompleted = true
                true
            }
        }
        assertSame(failure, runCatching { control.recover(1, {}, true) }.exceptionOrNull())
        assertNotNull(control.pause.value)
        failParsing = false
        val cancelled = CancellationException("合成会话变更")
        assertSame(cancelled, runCatching {
            control.recover(1, { if (readCompleted) throw cancelled }, true)
        }.exceptionOrNull())
        assertTrue(readCompleted)
        assertNotNull(control.pause.value)
    }
}
