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
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
        val now = { 1_000L }
        val control = ChxRequestControl(ChxRequestGate(now = now, intervalMillis = 0), now = now)
        control.bindIdentity("A")
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
        val clock = { now }
        val control = ChxRequestControl(ChxRequestGate(now = clock, intervalMillis = 0), now = clock)
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

    @Test fun oldIdentityResponseAndReadFinallyCannotReplaceNewIdentityTarget() = runBlocking {
        val now = { 1_000L }
        val control = ChxRequestControl(ChxRequestGate(now = now, intervalMillis = 0), now = now)
        val identityA = control.bindIdentity("A")
        val oldStarted = CompletableDeferred<Any>()
        val finishOld = CompletableDeferred<Unit>()
        val oldRead = async {
            runCatching {
                control.read(1, {}, identity = identityA) { owner, _ ->
                    control.recordResponse(owner, captcha)
                    oldStarted.complete(owner)
                    finishOld.await()
                    true
                }
            }.exceptionOrNull()
        }
        val oldOwner = oldStarted.await()
        assertTrue(control.pause.value!!.verificationRequired)
        val identityB = control.bindIdentity("B")
        var bReads = 0
        runCatching {
            control.read(2, {}, identity = identityB) { owner, check ->
                bReads++
                control.execute(owner, check) {
                    if (bReads == 1) captcha else SessionResponse(200, "B 新请求", null)
                }
            }
        }
        control.recordResponse(oldOwner, captcha.copy(status = 429, retryAfter = "30"))
        assertEquals(0L, control.pause.value!!.retryAt)
        finishOld.complete(Unit)
        assertTrue(oldRead.await() is CancellationException)
        assertTrue(control.pause.value!!.verificationRequired)
        control.recover(2, {}, afterWebVerification = true, identity = identityB)
        assertEquals(2, bReads)
        assertNull(control.pause.value)
    }

    @Test fun identityChangeDuringRecoveryCannotClearNewIdentityPause() = runBlocking {
        val now = { 1_000L }
        val control = ChxRequestControl(ChxRequestGate(now = now, intervalMillis = 0), now = now)
        val identityA = control.bindIdentity("A")
        val recoveryStarted = CompletableDeferred<Unit>()
        val finishRecovery = CompletableDeferred<Unit>()
        var aReads = 0
        runCatching {
            control.read(1, {}, identity = identityA) { owner, check ->
                aReads++
                if (aReads == 1) control.execute(owner, check) { captcha }
                else {
                    recoveryStarted.complete(Unit)
                    finishRecovery.await()
                }
                true
            }
        }
        val oldRecovery = async {
            runCatching {
                control.recover(1, {}, afterWebVerification = true, identity = identityA)
            }.exceptionOrNull()
        }
        recoveryStarted.await()
        val identityB = control.bindIdentity("B")
        var bReads = 0
        runCatching {
            control.read(2, {}, identity = identityB) { owner, check ->
                bReads++
                control.execute(owner, check) {
                    if (bReads == 1) captcha else SessionResponse(200, "B 新请求", null)
                }
            }
        }
        finishRecovery.complete(Unit)
        assertTrue(oldRecovery.await() is CancellationException)
        assertTrue(control.pause.value!!.verificationRequired)
        control.recover(2, {}, afterWebVerification = true, identity = identityB)
        assertEquals(2, bReads)
        assertNull(control.pause.value)
    }

    @Test fun invalidatedIdentityCannotReplayRecoveryAfterSameUidRebind() = runBlocking {
        val now = { 1_000L }
        val control = ChxRequestControl(ChxRequestGate(now = now, intervalMillis = 0), now = now)
        val oldIdentity = control.bindIdentity("A")
        var oldReads = 0
        var newReads = 0
        runCatching {
            control.read(7, {}, identity = oldIdentity) { owner, check ->
                oldReads++
                control.execute(owner, check) { captcha }
            }
        }
        control.invalidateIdentity()
        val reboundIdentity = control.bindIdentity("A")
        assertTrue(oldIdentity != reboundIdentity)
        runCatching {
            control.read(7, {}, identity = reboundIdentity) { owner, check ->
                newReads++
                control.execute(owner, check) {
                    if (newReads == 1) captcha else SessionResponse(200, "重新绑定详情", null)
                }
            }
        }
        control.recover(7, {}, afterWebVerification = true, identity = reboundIdentity)
        assertEquals(1, oldReads)
        assertEquals(2, newReads)
        assertNull(control.pause.value)
    }

    @Test fun identityChangeKeeps429DeadlineAndAllowsNormalReadAfterExpiry() = runBlocking {
        var now = 1_000L
        val clock = { now }
        val control = ChxRequestControl(ChxRequestGate(now = clock, intervalMillis = 0), now = clock)
        control.bindIdentity("A")
        var sends = 0
        runCatching {
            control.read(1, {}) { owner, check ->
                control.execute(owner, check) {
                    sends++
                    SessionResponse(429, "{}", null, retryAfter = "2")
                }
            }
        }
        assertEquals(3_000L, control.pause.value!!.retryAt)
        val identityB = control.bindIdentity("B")
        assertEquals(3_000L, control.pause.value!!.retryAt)
        val beforeDeadline = runCatching {
            control.read(2, {}, identity = identityB) { owner, check ->
                control.execute(owner, check) {
                    sends++
                    SessionResponse(200, "不可发送", null)
                }
            }
        }.exceptionOrNull()
        assertTrue(beforeDeadline is ChxCourseReadException)
        assertEquals(1, sends)
        now = 3_001L
        control.read(2, {}, identity = identityB) { owner, check ->
            control.execute(owner, check) {
                sends++
                SessionResponse(200, "成功", null)
            }
        }
        assertEquals(2, sends)
        assertNull(control.pause.value)
    }

    @Test fun recoveryUsesFreshSessionCheckForSameIdentity() = runBlocking {
        val now = { 1_000L }
        val control = ChxRequestControl(ChxRequestGate(now = now, intervalMillis = 0), now = now)
        val identity = control.bindIdentity("A")
        var requestVersion = 1
        var reads = 0
        runCatching {
            control.read(1, { assertEquals(1, requestVersion) }, identity = identity) { owner, check ->
                reads++
                control.execute(owner, check) {
                    if (reads == 1) captcha else SessionResponse(200, "成功", null)
                }
            }
        }
        requestVersion = 2
        control.recover(1, { assertEquals(2, requestVersion) }, afterWebVerification = true, identity = identity)
        assertEquals(2, reads)
        assertNull(control.pause.value)
    }

    @Test fun identityChangedAfterReadCannotCommitOldOwnerSnapshotOrTodos() = runBlocking {
        val control = ChxRequestControl(ChxRequestGate(intervalMillis = 0))
        val identityA = control.bindIdentity("A")
        val readFinished = CompletableDeferred<Unit>()
        val allowCommit = CompletableDeferred<Unit>()
        var owner = "B"
        var snapshot = "B 的快照"
        var todos = listOf("B 的待办")
        val oldRead = async {
            val result = control.read(1, {}, identity = identityA) { _, _ -> "A 的快照" }
            readFinished.complete(Unit)
            allowCommit.await()
            runCatching {
                control.withIdentity(identityA) {
                    owner = "A"
                    snapshot = result
                    todos = listOf("A 的待办")
                }
            }.exceptionOrNull()
        }
        readFinished.await()
        control.bindIdentity("B")
        allowCommit.complete(Unit)
        assertTrue(oldRead.await() is CancellationException)
        assertEquals("B", owner)
        assertEquals("B 的快照", snapshot)
        assertEquals(listOf("B 的待办"), todos)
    }

    @Test fun pausedIdentityCannotCommitPartialBusinessResults() = runBlocking {
        val control = ChxRequestControl(ChxRequestGate(intervalMillis = 0))
        val identity = control.bindIdentity("A")
        runCatching {
            control.read(1, {}, identity = identity) { owner, check ->
                control.execute(owner, check) { captcha }
            }
        }
        var commits = 0
        val failure = runCatching { control.withIdentity(identity) { commits++ } }.exceptionOrNull()
        assertTrue(failure is ChxCourseReadException)
        assertEquals(0, commits)
        assertNotNull(control.pause.value)
    }

    @Test fun identityChangeCannotInterleaveIdentityCheckAndCommit() {
        val control = ChxRequestControl(ChxRequestGate(intervalMillis = 0))
        val identity = control.bindIdentity("A")
        val switching = CountDownLatch(1)
        val changed = CountDownLatch(1)
        val workerFailure = AtomicReference<Throwable?>()
        val switcher = Thread {
            switching.countDown()
            try {
                control.bindIdentity("B")
            } catch (failure: Throwable) {
                workerFailure.set(failure)
            } finally {
                changed.countDown()
            }
        }
        var committed = false
        try {
            control.withIdentity(identity, {
                switcher.start()
                assertTrue(switching.await(5, TimeUnit.SECONDS))
                val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
                while (switcher.state != Thread.State.BLOCKED && changed.count != 0L && System.nanoTime() < deadline) {
                    Thread.yield()
                }
                assertEquals(Thread.State.BLOCKED, switcher.state)
                control.checkIdentity(identity)
            }) {
                assertEquals(Thread.State.BLOCKED, switcher.state)
                control.checkIdentity(identity)
                committed = true
            }
        } finally {
            switcher.join(5_000)
        }
        assertFalse(switcher.isAlive)
        assertNull(workerFailure.get())
        assertTrue(committed)
        assertEquals("B", control.currentIdentity().uid)
    }

    @Test fun identityEpochChangedDuringLiveUidCheckCannotCommit() {
        val control = ChxRequestControl(ChxRequestGate(intervalMillis = 0))
        val identity = control.bindIdentity("A")
        var committed = false
        val failure = runCatching {
            control.withIdentity(identity, {
                control.invalidateIdentity()
                control.bindIdentity("A")
            }) { committed = true }
        }.exceptionOrNull()
        assertTrue(failure is CancellationException)
        assertFalse(committed)
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
