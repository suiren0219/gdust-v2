package com.menglimi.power

import java.io.IOException
import java.time.Clock
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class PowerMonitorTest {
    private val binding = PowerBinding("account-a", 123, "room-a")
    private val replacement = PowerBinding("account-b", 456, "room-b")
    private val zone = ZoneId.of("Asia/Shanghai")
    private val now = Instant.parse("2026-11-15T04:00:00Z").toEpochMilli()
    private val hour = 3_600_000L
    private fun detail(odd: Double? = 50.0) = PowerDetail(odd, 1.0, emptyList(), null, null)

    private class TestClock(start: Long, private val step: Long = 0) : Clock() {
        private val time = AtomicLong(start)
        override fun getZone(): ZoneId = ZoneId.of("Asia/Shanghai")
        override fun withZone(zone: ZoneId): Clock = Clock.fixed(instant(), zone)
        override fun instant(): Instant = Instant.ofEpochMilli(millis())
        override fun millis(): Long = time.getAndAdd(step)
        fun advance(millis: Long) { time.addAndGet(millis) }
    }

    /** 多个 adapter 可共享同一持久化及锁，模拟每次 Worker/页面各自构造 prefs 适配器。 */
    private class Backend(var configuration: PowerMonitorConfig, initial: PowerMonitorState = PowerMonitorState()) {
        private val lock = Any()
        var saved = initial
            private set
        private var updateCount = 0
        var beforeUpdate: (Int) -> Unit = {}
        var afterUpdate: (Int) -> Unit = {}

        fun bind(binding: PowerBinding?) = synchronized(lock) {
            configuration = configuration.copy(binding = binding)
            saved = PowerMonitorState()
        }

        fun adapter(): PowerMonitorStore = object : PowerMonitorStore {
            override fun config() = synchronized(lock) { configuration }
            override fun state() = synchronized(lock) { saved }
            override fun update(binding: PowerBinding, transform: (PowerMonitorState) -> PowerMonitorState): Boolean {
                val index = ++updateCount
                beforeUpdate(index)
                val updated = synchronized(lock) {
                    if (configuration.binding != binding) false else {
                        saved = transform(saved)
                        true
                    }
                }
                if (updated) afterUpdate(index)
                return updated
            }
        }
    }

    private fun backend(state: PowerMonitorState = PowerMonitorState(), preset: PowerQueryPreset = PowerQueryPreset.BALANCED) =
        Backend(PowerMonitorConfig(binding, preset, zone = zone), state)

    @Test fun concurrentForcedQueriesAcrossInstancesShareOneRequestAndOneSample() = runBlocking {
        val backend = backend()
        // 每次读钟前进 1ms：若 requestedAt 在锁内获取，第二个 force 会错误地再请求一次。
        val clock = TestClock(now, step = 1)
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        var calls = 0
        val source = PowerDetailSource {
            calls++
            started.complete(Unit)
            release.await()
            detail()
        }
        val foreground = PowerMonitor(backend.adapter(), source, clock)
        val worker = PowerMonitor(backend.adapter(), source, clock)
        withTimeout(5_000) {
            val first = async(start = CoroutineStart.UNDISPATCHED) { foreground.query(force = true) }
            started.await()
            val second = async(start = CoroutineStart.UNDISPATCHED) { worker.query(force = true) }
            assertEquals(1, calls)
            release.complete(Unit)
            val one = first.await()
            val two = second.await()
            assertTrue(one.sampled)
            assertFalse(two.sampled)
            assertEquals(one.reading, two.reading)
        }
        assertEquals(1, calls)
        assertEquals(1, backend.saved.samples.size)
        assertEquals(binding.key, backend.saved.cachedBindingKey)
        assertTrue(backend.saved.lastAttemptAt >= now)
    }

    @Test fun lateResponseAfterBindingChangeCannotWriteSampleCacheOrWarning() = runBlocking {
        val backend = backend()
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        var warnings = 0
        val monitor = PowerMonitor(backend.adapter(), PowerDetailSource {
            started.complete(Unit)
            release.await()
            detail(1.0)
        }, TestClock(now), { warnings++; true })
        withTimeout(5_000) {
            val request = async(start = CoroutineStart.UNDISPATCHED) { monitor.query(force = true) }
            started.await()
            backend.bind(replacement)
            release.complete(Unit)
            assertTrue(runCatching { request.await() }.exceptionOrNull() is CancellationException)
        }
        assertEquals(PowerMonitorState(), backend.saved)
        assertEquals(replacement, backend.configuration.binding)
        assertEquals(0, warnings)
    }

    @Test fun bindingChangeImmediatelyBeforeCommitIsRejectedAtomically() = runBlocking {
        val backend = backend()
        backend.beforeUpdate = { if (it == 2) backend.bind(replacement) }
        var warnings = 0
        val monitor = PowerMonitor(backend.adapter(), PowerDetailSource { detail(1.0) }, TestClock(now), { warnings++; true })
        assertTrue(runCatching { monitor.query(force = true) }.exceptionOrNull() is CancellationException)
        assertEquals(PowerMonitorState(), backend.saved)
        assertEquals(0, warnings)
    }

    @Test fun bindingChangeAfterSampleCommitSuppressesOldWarning() = runBlocking {
        val backend = backend()
        backend.afterUpdate = { if (it == 2) backend.bind(replacement) }
        var warnings = 0
        val monitor = PowerMonitor(backend.adapter(), PowerDetailSource { detail(1.0) }, TestClock(now), { warnings++; true })
        assertTrue(runCatching { monitor.query(force = true) }.exceptionOrNull() is CancellationException)
        assertEquals(PowerMonitorState(), backend.saved)
        assertEquals(0, warnings)
    }

    @Test fun missingOrIncompleteBindingNeverQueries() = runBlocking {
        var calls = 0
        for (value in listOf(null, PowerBinding("", 1, "v"), PowerBinding("a", 0, "v"), PowerBinding("a", 1, ""))) {
            val backend = Backend(PowerMonitorConfig(value))
            val monitor = PowerMonitor(backend.adapter(), PowerDetailSource { calls++; detail() }, TestClock(now))
            assertEquals(PowerMonitorResult(null), monitor.query(force = true))
        }
        assertEquals(0, calls)
    }

    @Test fun manualPresetOnlyQueriesWhenForcedAndStillWarns() = runBlocking {
        val backend = backend(preset = PowerQueryPreset.MANUAL)
        var calls = 0
        val warnings = mutableListOf<PowerWarning>()
        val monitor = PowerMonitor(backend.adapter(), PowerDetailSource { calls++; detail(10.0) }, TestClock(now), {
            warnings += it
            true
        })
        assertNull(monitor.query().reading)
        assertNull(monitor.query(background = true).reading)
        assertTrue(monitor.query(force = true).sampled)
        assertEquals(1, calls)
        assertEquals(PowerWarningKind.LOW, warnings.single().kind)
        assertEquals(now, backend.saved.lowWarnAt)
    }

    @Test fun backgroundRespectsAwakeWindowButForegroundAndForceCanQuery() = runBlocking {
        val backend = backend()
        backend.configuration = backend.configuration.copy(awakeWindow = LocalTime.of(7, 30) to LocalTime.of(22, 5))
        val night = TestClock(now - 10 * hour)
        var calls = 0
        val monitor = PowerMonitor(backend.adapter(), PowerDetailSource { calls++; detail() }, night)
        assertFalse(monitor.query(background = true).sampled)
        assertTrue(monitor.query().sampled)
        night.advance(1)
        assertTrue(monitor.query(force = true, background = true).sampled)
        assertEquals(2, calls)
    }

    @Test fun changingToManualWhileBackgroundRequestRunsDiscardsResult() = runBlocking {
        val backend = backend()
        val monitor = PowerMonitor(backend.adapter(), PowerDetailSource {
            backend.configuration = backend.configuration.copy(preset = PowerQueryPreset.MANUAL)
            detail(1.0)
        }, TestClock(now), { error("不得发送已关闭后台监控的预警") })
        assertEquals(PowerMonitorResult(null), monitor.query(background = true))
        assertNull(backend.saved.reading)
        assertTrue(backend.saved.samples.isEmpty())
        assertEquals(now, backend.saved.lastAttemptAt)
    }

    @Test fun failedAttemptThrottlesBackgroundButDoesNotBlockForegroundRetry() = runBlocking {
        val backend = backend()
        var calls = 0
        val monitor = PowerMonitor(backend.adapter(), PowerDetailSource {
            if (++calls == 1) throw IOException("offline")
            detail()
        }, TestClock(now))
        assertTrue(runCatching { monitor.query(background = true) }.exceptionOrNull() is IOException)
        assertEquals(now, backend.saved.lastAttemptAt)
        assertFalse(monitor.query(background = true).sampled)
        assertTrue(monitor.query().sampled)
        assertEquals(2, calls)
    }

    @Test fun freshCacheDoesNotWriteAnotherSampleAndForceRefreshStillReplacesBucket() = runBlocking {
        val backend = backend()
        val clock = TestClock(now)
        var calls = 0
        val monitor = PowerMonitor(backend.adapter(), PowerDetailSource { detail(50.0 - calls++) }, clock)
        assertTrue(monitor.query().sampled)
        clock.advance(1_000)
        assertFalse(monitor.query().sampled)
        assertEquals(1, calls)
        assertTrue(monitor.query(force = true).sampled)
        assertEquals(2, calls)
        assertEquals(listOf(PowerSample(now + 1_000, 49.0)), backend.saved.samples)
        assertEquals(backend.saved.reading, monitor.cachedReading())
    }

    @Test fun missingCacheCanRetryForegroundDespiteRecentSampleAnchor() = runBlocking {
        val backend = backend(PowerMonitorState(lastSampleAt = now))
        var calls = 0
        val monitor = PowerMonitor(backend.adapter(), PowerDetailSource { calls++; detail() }, TestClock(now))
        assertFalse(monitor.query(background = true).sampled)
        assertTrue(monitor.query().sampled)
        assertEquals(1, calls)
    }

    @Test fun staleOrInvalidCacheIsNotExposed() {
        val wrongBinding = backend(PowerMonitorState(replacement.key, PowerReading(detail(), now)))
        assertNull(PowerMonitor(wrongBinding.adapter()).cachedReading())
        for (odd in listOf(null, Double.NaN, Double.POSITIVE_INFINITY)) {
            val backend = backend(PowerMonitorState(binding.key, PowerReading(detail(odd), now)))
            assertNull(PowerMonitor(backend.adapter()).cachedReading())
        }
    }

    @Test fun invalidNetworkReadingOnlyRecordsAttemptButZeroAndNegativeRemainValid() = runBlocking {
        for (odd in listOf(null, Double.NaN, Double.POSITIVE_INFINITY)) {
            val backend = backend()
            val monitor = PowerMonitor(backend.adapter(), PowerDetailSource { detail(odd) }, TestClock(now))
            assertTrue(runCatching { monitor.query(force = true) }.exceptionOrNull() is IllegalStateException)
            assertEquals(PowerMonitorState(lastAttemptAt = now), backend.saved)
        }
        for (odd in listOf(0.0, -1.0)) {
            val backend = backend()
            val monitor = PowerMonitor(backend.adapter(), PowerDetailSource { detail(odd) }, TestClock(now))
            assertTrue(monitor.query(force = true).sampled)
            assertEquals(odd, backend.saved.samples.single().odd, 0.0)
            assertEquals(odd, monitor.cachedReading()!!.detail.odd!!, 0.0)
        }
    }

    @Test fun unsuccessfulWarningDoesNotStartCooldownOrUndoSample() = runBlocking {
        for (throws in listOf(false, true)) {
            val backend = backend()
            val clock = TestClock(now)
            var warnings = 0
            val monitor = PowerMonitor(backend.adapter(), PowerDetailSource { detail(1.0) }, clock, {
                warnings++
                if (throws) throw IllegalStateException("notifications disabled")
                false
            })
            assertTrue(monitor.query(force = true).sampled)
            assertEquals(0L, backend.saved.lowWarnAt)
            assertEquals(1, backend.saved.samples.size)
            clock.advance(hour)
            assertTrue(monitor.query(force = true).sampled)
            assertEquals(2, warnings)
            assertEquals(0L, backend.saved.lowWarnAt)
        }
    }

    @Test fun successfulLowWarningUsesTwelveHourCooldown() = runBlocking {
        val backend = backend()
        val clock = TestClock(now)
        var warnings = 0
        val monitor = PowerMonitor(backend.adapter(), PowerDetailSource { detail(1.0) }, clock, { warnings++; true })
        monitor.query(force = true)
        clock.advance(hour)
        monitor.query(force = true)
        assertEquals(1, warnings)
        clock.advance(11 * hour)
        monitor.query(force = true)
        assertEquals(2, warnings)
        assertEquals(now + 12 * hour, backend.saved.lowWarnAt)
    }

    @Test fun warningCancellationPropagatesAfterSuccessfulSampleAndDoesNotStartCooldown() {
        val backend = backend()
        val monitor = PowerMonitor(backend.adapter(), PowerDetailSource { detail(1.0) }, TestClock(now), {
            throw CancellationException("notification cancelled")
        })
        assertThrows(CancellationException::class.java) { runBlocking { monitor.query(force = true) } }
        assertEquals(1, backend.saved.samples.size)
        assertEquals(0L, backend.saved.lowWarnAt)
    }

    @Test fun dropWarningUsesLatestSampleAtLeastFifteenMinutesOld() = runBlocking {
        val backend = backend(PowerMonitorState(samples = listOf(
            PowerSample(now - hour, 100.0), PowerSample(now - 5 * 60_000, 91.0),
        )))
        val warnings = mutableListOf<PowerWarning>()
        val monitor = PowerMonitor(backend.adapter(), PowerDetailSource { detail(90.0) }, TestClock(now), {
            warnings += it
            true
        })
        monitor.query(force = true)
        assertEquals(PowerWarningKind.DROP, warnings.single().kind)
        assertEquals(10.0, warnings.single().dropRate!!, 0.0)
        assertEquals(now, backend.saved.dropWarnAt)
        assertEquals(0L, backend.saved.lowWarnAt)
    }

    @Test fun lowWarningWinsButItsCooldownStillAllowsDropWarning() {
        val previous = PowerSample(now - hour, 40.0)
        val current = PowerSample(now, 20.0)
        fun decide(lowAt: Long, dropAt: Long) = PowerWarningPolicy.decide(
            previous, current, listOf(previous, current), 20.0, 5.0, lowAt, dropAt,
        )
        assertEquals(PowerWarningKind.LOW, decide(0, 0)!!.kind)
        assertEquals(PowerWarningKind.DROP, decide(now - hour, 0)!!.kind)
        assertNull(decide(now - hour, now - hour))
        assertEquals(PowerWarningKind.LOW, decide(now - 12 * hour, now - hour)!!.kind)
    }

    @Test fun warningDecisionUsesDefaultThresholdsAndRejectsInvalidCurrentSample() {
        val current = PowerSample(now, 20.0)
        val warning = PowerWarningPolicy.decide(null, current, listOf(current), Double.NaN, -1.0, 0, 0)
        assertEquals(PowerWarningKind.LOW, warning!!.kind)
        assertEquals(20.0, warning.threshold, 0.0)
        for (invalid in listOf(current.copy(odd = Double.NaN), current.copy(odd = Double.NEGATIVE_INFINITY), current.copy(time = 0))) {
            assertNull(PowerWarningPolicy.decide(null, invalid, emptyList(), 20.0, 5.0, 0, 0))
        }
        val recharged = PowerSample(now, 100.0)
        assertNull(PowerWarningPolicy.decide(current.copy(time = now - hour), recharged, listOf(recharged), 20.0, 5.0, 0, 0))
    }

    @Test fun permissionRecheckRetriesRecentWarningWithoutNetworkOrNewSamples() = runBlocking {
        val sampledAt = now - 60_000L
        val sample = PowerSample(sampledAt, 5.0)
        val initial = PowerMonitorState(cachedBindingKey = binding.key,
            reading = PowerReading(detail(5.0), sampledAt), lastSampleAt = sampledAt, samples = listOf(sample))
        val backend = backend(initial)
        var accepted = false
        var deliveries = 0
        val monitor = PowerMonitor(backend.adapter(), PowerDetailSource { error("No network during recheck") },
            TestClock(now), { deliveries++; accepted })
        assertFalse(monitor.recheckWarnings())
        assertEquals(0L, backend.saved.lowWarnAt)
        accepted = true
        assertTrue(monitor.recheckWarnings())
        assertEquals(now, backend.saved.lowWarnAt)
        assertFalse(monitor.recheckWarnings())
        assertEquals(2, deliveries)
        assertEquals(initial.samples, backend.saved.samples)
        assertEquals(sampledAt, backend.saved.lastSampleAt)
    }

    @Test fun permissionRecheckRejectsStaleFutureAndWrongBindingReadings() = runBlocking {
        for (time in listOf(now - 16 * 60_000L, now + 60_000L)) {
            val backend = backend(PowerMonitorState(cachedBindingKey = binding.key,
                reading = PowerReading(detail(1.0), time)))
            val monitor = PowerMonitor(backend.adapter(), clock = TestClock(now),
                onWarning = { error("Invalid reading cannot notify") })
            assertFalse(monitor.recheckWarnings())
        }
        val backend = backend(PowerMonitorState(cachedBindingKey = replacement.key,
            reading = PowerReading(detail(1.0), now)))
        assertFalse(PowerMonitor(backend.adapter(), clock = TestClock(now),
            onWarning = { error("Wrong room cannot notify") }).recheckWarnings())
    }

    @Test fun permissionRecheckPreservesCancellationAndCooldown() {
        val initial = PowerMonitorState(cachedBindingKey = binding.key,
            reading = PowerReading(detail(1.0), now))
        val backend = backend(initial)
        val monitor = PowerMonitor(backend.adapter(), clock = TestClock(now),
            onWarning = { throw CancellationException("Cancelled") })
        assertThrows(CancellationException::class.java) { runBlocking { monitor.recheckWarnings() } }
        assertEquals(initial, backend.saved)
    }

    @Test fun permissionRecheckUsesCurrentTimeForExpiredCooldown() = runBlocking {
        val sampledAt = now - 5 * 60_000L
        val backend = backend(PowerMonitorState(cachedBindingKey = binding.key,
            reading = PowerReading(detail(1.0), sampledAt), lowWarnAt = now - 12 * hour))
        assertTrue(PowerMonitor(backend.adapter(), clock = TestClock(now), onWarning = { true }).recheckWarnings())
        assertEquals(now, backend.saved.lowWarnAt)
    }

    @Test fun warningEstimateUsesPreAppendHistoryWhileStorageDeduplicatesBucket() = runBlocking {
        val currentAt = now + 60_000
        val history = listOf(PowerSample(now - 24 * hour, 40.0), PowerSample(now, 11.0))
        val backend = backend(PowerMonitorState(samples = history))
        val warnings = mutableListOf<PowerWarning>()
        val monitor = PowerMonitor(backend.adapter(), PowerDetailSource { detail(10.0) }, TestClock(currentAt), {
            warnings += it
            true
        })
        monitor.query(force = true)
        val expectedDays = PowerQueryPolicy.estimate(history + PowerSample(currentAt, 10.0), currentAt).daysLeft!!
        assertEquals(expectedDays, warnings.single().daysLeft!!, 0.0)
        assertEquals(listOf(history.first(), PowerSample(currentAt, 10.0)), backend.saved.samples)
    }
}
