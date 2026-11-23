package com.menglimi.dormpower.power

import com.menglimi.power.PowerBinding
import com.menglimi.power.PowerDetail
import com.menglimi.power.PowerDetailSource
import com.menglimi.power.PowerMonitorConfig
import com.menglimi.power.PowerMonitorState
import com.menglimi.power.PowerMonitorStore
import com.menglimi.power.PowerQueryPreset
import com.menglimi.power.PowerReading
import com.menglimi.power.PowerSample
import java.io.IOException
import java.time.Clock
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneOffset
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import org.junit.Assert.*
import org.junit.Test

class PowerBackgroundRunnerTest {
    private val now = Instant.parse("2026-11-20T04:00:00Z").toEpochMilli()
    private val clock = Clock.fixed(Instant.ofEpochMilli(now), ZoneOffset.UTC)
    private val binding = PowerBinding("synthetic", 1, "room")
    private val detail = PowerDetail(42.0, null, emptyList(), null, null)

    private class Repository(var value: PowerSnapshot) : PowerRepository {
        override fun snapshot() = value
        override fun config() = value.config
        override fun state() = value.monitor
        override fun monitorStore(background: Boolean): PowerMonitorStore = this
        override fun update(binding: PowerBinding, transform: (PowerMonitorState) -> PowerMonitorState): Boolean {
            if (value.config.binding != binding) return false
            value = value.copy(monitor = transform(value.monitor))
            return true
        }
        override fun configure(transform: (PowerMonitorConfig) -> PowerMonitorConfig): PowerSnapshot {
            value = value.copy(config = transform(value.config))
            return value
        }
        override fun bind(binding: PowerBinding, roomName: String, expectedRevision: Long): PowerSnapshot = error("unused")
        override fun unbind(): PowerSnapshot = error("unused")
        override fun editLedger(expectedRevision: Long, transform: (PowerLedgerState) -> PowerLedgerState): PowerSnapshot =
            error("unused")
    }
    private class Diagnostic : PowerBackgroundDiagnostics {
        var value = PowerBackgroundStatus()
        override fun update(transform: (PowerBackgroundStatus) -> PowerBackgroundStatus) { value = transform(value) }
    }
    private fun repository() = Repository(PowerSnapshot(config = PowerMonitorConfig(binding = binding), bindingRevision = 0L))

    @Test fun manualPresetAllowsBootQueryAndSameTimestampDoesNotDuplicateSample() = runTest {
        val repository = repository()
        repository.configure { it.copy(preset = PowerQueryPreset.MANUAL) }
        val diagnostic = Diagnostic()
        var calls = 0
        val runner = PowerBackgroundRunner(repository, PowerDetailSource { calls++; detail }, diagnostic, clock)
        assertEquals(PowerBackgroundOutcome.SUCCESS,
            runner.run(0) { true })
        assertEquals(1, calls)
        assertEquals(now, diagnostic.value.lastRunAt)
        assertEquals(now, diagnostic.value.lastSuccessAt)
        assertEquals(listOf(PowerSample(now, 42.0)), repository.state().samples)
        // 同 clock 的第二次手工调用只验证请求时间戳去重，不代表安排了第二次工作。
        assertEquals(PowerBackgroundOutcome.SKIPPED,
            runner.run(0) { true })
        assertEquals(1, calls)
        assertEquals(listOf(PowerSample(now, 42.0)), repository.state().samples)
        assertEquals(now, diagnostic.value.lastSuccessAt)
        assertEquals(now, diagnostic.value.lastSkippedAt)
    }

    @Test fun singleRunsIgnorePresetRecentReadingAndAwakeWindow() = runTest {
        for (preset in PowerQueryPreset.entries) {
            val repository = repository()
            val recentTime = now - 1L
            repository.value = repository.value.copy(
                config = repository.config().copy(preset = preset, zone = ZoneOffset.UTC,
                    awakeWindow = LocalTime.of(5, 0) to LocalTime.of(6, 0)),
                monitor = PowerMonitorState(cachedBindingKey = binding.key,
                    reading = PowerReading(detail, recentTime), lastSampleAt = recentTime,
                    lastAttemptAt = recentTime, samples = listOf(PowerSample(recentTime, 42.0))))
            val diagnostic = Diagnostic()
            var calls = 0
            val source = PowerDetailSource { calls++; detail }
            assertEquals(PowerBackgroundOutcome.SUCCESS, PowerBackgroundRunner(repository, source, diagnostic, clock)
                .run(0) { true })
            assertEquals(1, calls)
            assertEquals(now, repository.state().lastSampleAt)
            assertEquals(listOf(PowerSample(recentTime, 42.0), PowerSample(now, 42.0)), repository.state().samples)
            assertEquals(now, diagnostic.value.lastSuccessAt)
        }
    }

    @Test fun failureKeepsReadingAndDoesNotThrottleAnotherSingleRun() = runTest {
        val repository = repository()
        val oldTime = now - 4 * 3_600_000L
        val oldReading = PowerReading(detail, oldTime)
        val oldSamples = listOf(PowerSample(oldTime, 42.0))
        repository.value = repository.value.copy(monitor = PowerMonitorState(cachedBindingKey = binding.key,
            reading = oldReading, lastSampleAt = oldTime, samples = oldSamples))
        val diagnostic = Diagnostic()
        var calls = 0
        val source = PowerDetailSource { calls++; throw IOException("synthetic-account must not be logged") }
        assertEquals(PowerBackgroundOutcome.FAILURE, PowerBackgroundRunner(repository, source, diagnostic, clock)
            .run(0) { true })
        assertEquals(oldReading, repository.state().reading)
        assertEquals(oldSamples, repository.state().samples)
        assertEquals(oldTime, repository.state().lastSampleAt)
        assertEquals(now, repository.state().lastAttemptAt)
        assertEquals(now, diagnostic.value.lastFailureAt)
        assertEquals(0L, diagnostic.value.lastSuccessAt)
        assertFalse(diagnostic.value.outcomeExplanation.contains("synthetic-account"))
        assertEquals(PowerBackgroundOutcome.FAILURE, PowerBackgroundRunner(repository, source, diagnostic, clock)
            .run(0) { true })
        assertEquals(2, calls)
        assertEquals(oldReading, repository.state().reading)
        assertEquals(oldSamples, repository.state().samples)
        assertEquals(0L, diagnostic.value.lastSuccessAt)
    }

    @Test fun unboundInvalidOrStaleBindingSkipsWithoutCallingSource() = runTest {
        val original = repository().snapshot()
        for (snapshot in listOf(original.copy(config = original.config.copy(binding = null)),
            original.copy(config = original.config.copy(binding = binding.copy(verify = ""))),
            original.copy(bindingRevision = original.bindingRevision + 1))) {
            val repository = Repository(snapshot)
            val diagnostic = Diagnostic()
            var calls = 0
            assertEquals(PowerBackgroundOutcome.SKIPPED, PowerBackgroundRunner(repository,
                PowerDetailSource { calls++; detail }, diagnostic, clock)
                .run(0) { true })
            assertEquals(0, calls)
            assertEquals(snapshot.monitor, repository.state())
            assertEquals(0L, diagnostic.value.lastSuccessAt)
            assertEquals(now, diagnostic.value.lastSkippedAt)
        }
    }

    @Test fun optOutSkipsWithoutCallingSource() = runTest {
        val repository = repository()
        val diagnostic = Diagnostic()
        var calls = 0
        assertEquals(PowerBackgroundOutcome.SKIPPED, PowerBackgroundRunner(repository,
            PowerDetailSource { calls++; detail }, diagnostic, clock)
            .run(0) { false })
        assertEquals(0, calls)
        assertEquals(0L, repository.state().lastAttemptAt)
        assertEquals(0L, diagnostic.value.lastSuccessAt)
        assertEquals(now, diagnostic.value.lastSkippedAt)
    }

    @Test fun optOutBeforeSourceCancelsWithoutCallingSource() = runTest {
        val repository = repository()
        val diagnostic = Diagnostic()
        var calls = 0
        var eligibilityChecks = 0
        try {
            PowerBackgroundRunner(repository, PowerDetailSource { calls++; detail }, diagnostic, clock)
                .run(0) { ++eligibilityChecks == 1 }
            fail("Cancellation must propagate")
        } catch (_: CancellationException) {
            assertEquals(PowerBackgroundOutcome.CANCELLED, diagnostic.value.lastOutcome)
        }
        assertEquals(0, calls)
        assertNull(repository.state().reading)
        assertEquals(0L, diagnostic.value.lastSuccessAt)
    }

    @Test fun executionCancelledWhileWaitingDoesNotCallSource() = runTest {
        val repository = repository()
        val sourceStarted = CompletableDeferred<Unit>()
        val releaseSource = CompletableDeferred<Unit>()
        val blockingRun = launch {
            PowerBackgroundRunner(repository, PowerDetailSource {
                sourceStarted.complete(Unit)
                releaseSource.await()
                detail
            }, Diagnostic(), clock).run(0) { true }
        }
        sourceStarted.await()
        val diagnostic = Diagnostic()
        var calls = 0
        val job = launch {
            PowerBackgroundRunner(repository, PowerDetailSource { calls++; detail }, diagnostic, clock)
                .run(0) { true }
        }
        yield()
        job.cancelAndJoin()
        assertTrue(job.isCancelled)
        assertEquals(0, calls)
        assertEquals(PowerBackgroundOutcome.CANCELLED, diagnostic.value.lastOutcome)
        assertEquals(0L, diagnostic.value.lastSuccessAt)
        assertNull(repository.state().reading)
        releaseSource.complete(Unit)
        blockingRun.join()
    }

    @Test fun diagnosticFailureCannotPreventSnapshotPersistenceOrChangeSuccess() = runTest {
        val repository = repository()
        val diagnostic = PowerBackgroundDiagnostics { throw IOException("diagnostic disk full") }
        assertEquals(PowerBackgroundOutcome.SUCCESS, PowerBackgroundRunner(repository,
            PowerDetailSource { detail }, diagnostic, clock)
            .run(0) { true })
        assertEquals(now, repository.state().lastSampleAt)
        assertEquals(detail, repository.state().reading?.detail)
        assertEquals(listOf(PowerSample(now, 42.0)), repository.state().samples)
    }

    @Test fun boundedTimeoutIsFailureAndPreservesOldData() = runTest {
        val repository = repository()
        val oldTime = now - 4 * 3_600_000L
        val oldReading = PowerReading(detail, oldTime)
        val oldSamples = listOf(PowerSample(oldTime, 42.0))
        repository.value = repository.value.copy(monitor = PowerMonitorState(cachedBindingKey = binding.key,
            reading = oldReading, lastSampleAt = oldTime, samples = oldSamples))
        val diagnostic = Diagnostic()
        assertEquals(PowerBackgroundOutcome.FAILURE, PowerBackgroundRunner(repository,
            PowerDetailSource { awaitCancellation() }, diagnostic, clock, timeoutMs = 1_000L)
            .run(0) { true })
        assertEquals(now, repository.state().lastAttemptAt)
        assertEquals(oldReading, repository.state().reading)
        assertEquals(oldSamples, repository.state().samples)
        assertEquals(oldTime, repository.state().lastSampleAt)
        assertEquals(0L, diagnostic.value.lastSuccessAt)
        assertEquals(now, diagnostic.value.lastFailureAt)
    }

    @Test fun externalCancellationIsRethrownAndNeverReportedAsSuccess() = runTest {
        val repository = repository()
        val diagnostic = Diagnostic()
        val cancellation = CancellationException("synthetic cancellation")
        try {
            PowerBackgroundRunner(repository, PowerDetailSource { throw cancellation }, diagnostic, clock)
                .run(0) { true }
            fail("Cancellation must propagate")
        } catch (actual: CancellationException) {
            // 协程调试的栈恢复可能复制异常实例；校验取消语义而非引用身份。
            assertEquals(cancellation.message, actual.message)
        }
        assertEquals(PowerBackgroundOutcome.CANCELLED, diagnostic.value.lastOutcome)
        assertEquals(0L, diagnostic.value.lastSuccessAt)
        assertNull(repository.state().reading)
    }
}
