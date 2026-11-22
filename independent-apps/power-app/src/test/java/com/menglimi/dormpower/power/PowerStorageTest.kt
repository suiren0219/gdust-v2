package com.menglimi.dormpower.power

import com.menglimi.power.PowerBinding
import com.menglimi.power.PowerDetail
import com.menglimi.power.PowerDetailSource
import com.menglimi.power.PowerMonitor
import com.menglimi.power.PowerMonitorState
import com.menglimi.power.PowerQueryPreset
import com.menglimi.power.PowerReading
import com.menglimi.power.PowerSample
import java.io.IOException
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test

class PowerStorageTest {
    private val binding = PowerBinding("synthetic-account", 1, "synthetic-room")
    private val other = PowerBinding("other-account", 2, "other-room")
    private val now = Instant.parse("2026-11-16T04:00:00Z").toEpochMilli()
    private val clock = Clock.fixed(Instant.ofEpochMilli(now), ZoneId.of("Asia/Shanghai"))
    private val detail = PowerDetail(1.0, 2.0, listOf("一" to 3.0), 4.0, 5.0)

    private class MemoryPersistence : PowerPersistence {
        var value: String? = null
        var backup: String? = null
        var readFailure: Exception? = null
        var backupReadFailure: Exception? = null
        var fail = false
        var writes = 0
        override fun read(): String? {
            readFailure?.let { throw it }
            return value
        }
        override fun readBackup(): String? {
            backupReadFailure?.let { throw it }
            return backup
        }
        override fun write(value: String) {
            writes++
            if (fail) throw IOException("synthetic persistence failure")
            this.value = value
        }
    }

    private fun bound(persistence: MemoryPersistence = MemoryPersistence()): PowerStorage = PowerStorage(persistence).apply {
        bind(binding, "测试宿舍", snapshot().bindingRevision)
    }

    @Test fun changingBindingAtomicallyClearsHistoryCacheAndBothCooldowns() {
        val store = bound()
        store.update(binding) { PowerMonitorState(binding.key, PowerReading(detail, now), now, now,
            listOf(PowerSample(now, 1.0)), now, now) }
        val revision = store.snapshot().bindingRevision
        val changed = store.bind(other, "另一宿舍", revision)
        assertEquals(other, changed.config.binding)
        assertEquals("另一宿舍", changed.roomName)
        assertEquals(PowerMonitorState(), changed.monitor)
        assertEquals(revision + 1, changed.bindingRevision)
        var calls = 0
        assertFalse(store.update(binding) { calls++; it })
        assertEquals(0, calls)
    }

    @Test fun confirmingSameRoomPreservesHistoryButInvalidatesPreviousSession() {
        val store = bound()
        store.update(binding) { PowerMonitorState(binding.key, PowerReading(detail, now), now, now,
            listOf(PowerSample(now, 1.0)), now, now) }
        val previousSession = store.monitorStore()
        val previousMonitor = store.state()
        val revision = store.snapshot().bindingRevision
        val changed = store.bind(binding, "测试宿舍", revision)
        assertEquals(previousMonitor, changed.monitor)
        assertEquals(revision + 1, changed.bindingRevision)
        assertFalse(previousSession.update(binding) { it.copy(lowWarnAt = now) })
    }

    @Test fun oldRoomQueryCannotReplaceBindingAndUnbindInvalidatesItsRevision() {
        val store = bound()
        val old = store.snapshot().bindingRevision
        store.unbind()
        assertThrows(IllegalStateException::class.java) { store.bind(binding, "测试宿舍", old) }
        assertNull(store.config().binding)
        assertEquals(PowerMonitorState(), store.state())
    }

    @Test fun bindingRoundTripCannotReviveOldMonitorSession() {
        val store = bound()
        val oldSession = store.monitorStore()
        store.bind(other, "另一宿舍", store.snapshot().bindingRevision)
        store.bind(binding, "测试宿舍", store.snapshot().bindingRevision)
        var calls = 0
        assertFalse(oldSession.update(binding) { calls++; it })
        assertThrows(CancellationException::class.java) { oldSession.config() }
        assertEquals(0, calls)
    }

    @Test fun failedCommitDoesNotPublishPartialBindingAndNeverRetriesTransform() {
        val persistence = MemoryPersistence()
        val store = bound(persistence)
        val before = store.snapshot()
        persistence.fail = true
        assertThrows(IOException::class.java) { store.bind(other, "另一宿舍", before.bindingRevision) }
        assertEquals(before, store.snapshot())
        var calls = 0
        assertThrows(IOException::class.java) {
            store.update(binding) { calls++; it.copy(lastAttemptAt = now) }
        }
        assertEquals(1, calls)
        assertEquals(before, store.snapshot())
        assertEquals(before, PowerStorage(persistence).snapshot())
    }

    @Test fun backgroundSessionStopsAtManualPresetButForegroundCanStillSave() {
        val store = bound()
        val worker = store.monitorStore(background = true)
        val foreground = store.monitorStore()
        store.configure { it.copy(preset = PowerQueryPreset.MANUAL) }
        var calls = 0
        assertFalse(worker.update(binding) { calls++; it })
        assertEquals(0, calls)
        assertTrue(foreground.update(binding) { it.copy(lastAttemptAt = now) })
        assertEquals(now, store.state().lastAttemptAt)
    }

    @Test fun lateNetworkResultAfterBindingRoundTripCannotSampleOrNotify() = runBlocking {
        val store = bound()
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        var warnings = 0
        val monitor = PowerMonitor(store.monitorStore(), PowerDetailSource {
            started.complete(Unit)
            release.await()
            detail
        }, clock, { warnings++; true })
        withTimeout(5_000) {
            val request = async(start = CoroutineStart.UNDISPATCHED) { monitor.query(force = true) }
            started.await()
            store.bind(other, "另一宿舍", store.snapshot().bindingRevision)
            store.bind(binding, "测试宿舍", store.snapshot().bindingRevision)
            release.complete(Unit)
            assertTrue(runCatching { request.await() }.exceptionOrNull() is CancellationException)
        }
        assertEquals(PowerMonitorState(), store.state())
        assertEquals(0, warnings)
    }

    @Test fun notificationFailurePreservesSamplingWithoutCooldownAndSuccessUsesSampleTime() = runBlocking {
        val store = bound()
        var accepted = false
        val monitor = PowerMonitor(store.monitorStore(), PowerDetailSource { detail }, clock, { accepted })
        monitor.query(force = true)
        assertEquals(0L, store.state().lowWarnAt)
        assertEquals(now, store.state().lastSampleAt)
        assertEquals(1, store.state().samples.size)
        accepted = true
        val later = Clock.fixed(Instant.ofEpochMilli(now + 60_000), clock.zone)
        PowerMonitor(store.monitorStore(), PowerDetailSource { detail }, later, { accepted }).query(force = true)
        assertEquals(now + 60_000, store.state().lowWarnAt)
        assertEquals(1, store.state().samples.size)
    }

    @Test fun persistedHistorySurvivesRestartInvalidRefreshAndLaterSuccessfulUpdate() = runBlocking {
        val persistence = MemoryPersistence()
        val store = bound(persistence)
        val old = now - 86_400_000L
        val oldReading = PowerReading(detail.copy(odd = 40.9), old)
        val oldSamples = listOf(PowerSample(old, 40.9))
        store.update(binding) { PowerMonitorState(binding.key, oldReading, old, old, oldSamples) }
        store.configure { it.copy(lowThreshold = 10.0) }
        val saved = persistence.value
        val restarted = PowerStorage(persistence)
        assertEquals(store.snapshot(), restarted.snapshot())
        assertEquals(saved, persistence.value)
        val bad = PowerMonitor(restarted.monitorStore(), PowerDetailSource { detail.copy(odd = null) }, clock)
        assertTrue(runCatching { bad.query(force = true) }.isFailure)
        val afterFailure = PowerStorage(persistence)
        assertEquals(oldReading, afterFailure.state().reading)
        assertEquals(oldSamples, afterFailure.state().samples)
        assertEquals(old, afterFailure.state().lastSampleAt)
        assertEquals(now, afterFailure.state().lastAttemptAt)
        val newDetail = detail.copy(odd = 37.9)
        PowerMonitor(afterFailure.monitorStore(), PowerDetailSource { newDetail }, clock).query(force = true)
        val afterUpdate = PowerStorage(persistence)
        assertEquals(PowerReading(newDetail, now), afterUpdate.state().reading)
        assertEquals(oldSamples + PowerSample(now, 37.9), afterUpdate.state().samples)
        assertEquals(binding, afterUpdate.config().binding)
        assertEquals(10.0, afterUpdate.config().lowThreshold, 0.0)
    }

    private fun savedSnapshot(): PowerSnapshot {
        val store = bound()
        store.update(binding) { PowerMonitorState(binding.key, PowerReading(detail, now), now, now,
            listOf(PowerSample(now - 900_000, 2.0), PowerSample(now, 1.0)), now, now) }
        store.configure { it.copy(lowThreshold = 10.0, customIntervalMinutes = 45) }
        return store.snapshot()
    }

    @Test fun missingMalformedOrUndecryptablePrimaryRecoversBackupWithoutWriting() {
        val expected = savedSnapshot()
        val backupText = PowerPersistenceCodec.encode(expected)
        for (primaryCase in listOf("missing", "malformed", "undecryptable")) {
            val persistence = MemoryPersistence().apply {
                backup = backupText
                value = if (primaryCase == "missing") null else "broken-primary"
                if (primaryCase == "undecryptable") readFailure = SecurityException("synthetic decryption failure")
            }
            val original = persistence.value
            val store = PowerStorage(persistence)

            val restored = store.snapshot()

            assertEquals(primaryCase, expected, restored.copy(recoveryMessage = null))
            assertFalse(primaryCase, restored.recoveryMessage.isNullOrBlank())
            assertEquals(primaryCase, restored, store.snapshot())
            assertEquals(primaryCase, expected.config, store.config())
            assertEquals(primaryCase, expected.monitor, store.state())
            assertEquals(primaryCase, original, persistence.value)
            assertEquals(primaryCase, backupText, persistence.backup)
            assertEquals(primaryCase, 0, persistence.writes)
            val restarted = PowerStorage(persistence).snapshot()
            assertEquals(primaryCase, restored, restarted)
            assertEquals(primaryCase, 0, persistence.writes)
        }
    }

    @Test fun readablePrimaryWinsOverBackupAndRecoveryHintIsNeverSerialized() {
        val expected = savedSnapshot()
        val backup = expected.copy(roomName = "旧备份宿舍", bindingRevision = expected.bindingRevision - 1)
        val persistence = MemoryPersistence().apply {
            value = PowerPersistenceCodec.encode(expected)
            this.backup = PowerPersistenceCodec.encode(backup)
        }

        assertEquals(expected, PowerStorage(persistence).snapshot())
        assertEquals(0, persistence.writes)
        val hinted = expected.copy(recoveryMessage = "仅本次恢复提示")
        val encoded = PowerPersistenceCodec.encode(hinted)
        assertEquals(PowerPersistenceCodec.encode(expected), encoded)
        assertEquals(expected, PowerPersistenceCodec.decode(encoded))
    }

    @Test fun unreadableExistingDataRejectsAllMutationsInsteadOfOverwritingWithDefaults() {
        val cases = listOf("both-malformed", "primary-only-malformed", "backup-only-malformed",
            "primary-decryption-failure", "backup-decryption-failure", "both-decryption-failure")
        for (failureCase in cases) {
            val persistence = MemoryPersistence().apply {
                when (failureCase) {
                    "both-malformed" -> { value = "broken-primary"; backup = "broken-backup" }
                    "primary-only-malformed" -> value = "broken-primary"
                    "backup-only-malformed" -> backup = "broken-backup"
                    "primary-decryption-failure" -> readFailure = SecurityException("synthetic primary key failure")
                    "backup-decryption-failure" -> backupReadFailure = SecurityException("synthetic backup key failure")
                    "both-decryption-failure" -> {
                        value = "encrypted-primary"
                        backup = "encrypted-backup"
                        readFailure = SecurityException("synthetic primary key failure")
                        backupReadFailure = SecurityException("synthetic backup key failure")
                    }
                }
            }
            val original = persistence.value
            val originalBackup = persistence.backup
            val store = PowerStorage(persistence)
            var transforms = 0

            assertThrows(failureCase, IOException::class.java) { store.snapshot() }
            assertThrows(failureCase, IOException::class.java) {
                store.configure { transforms++; it.copy(lowThreshold = 10.0) }
            }
            assertThrows(failureCase, IOException::class.java) { store.bind(binding, "测试宿舍", 0L) }
            assertThrows(failureCase, IOException::class.java) { store.unbind() }
            assertThrows(failureCase, IOException::class.java) {
                store.update(binding) { transforms++; it.copy(lastAttemptAt = now) }
            }
            assertEquals(failureCase, 0, transforms)
            assertEquals(failureCase, 0, persistence.writes)
            assertEquals(failureCase, original, persistence.value)
            assertEquals(failureCase, originalBackup, persistence.backup)
            assertThrows(failureCase, IOException::class.java) { PowerStorage(persistence).snapshot() }
        }
    }

    @Test fun mismatchedPrimaryBindingKeyRestoresReadableBackupWithoutDiscardingItsHistory() {
        val expected = savedSnapshot()
        val wrong = expected.copy(monitor = expected.monitor.copy(cachedBindingKey = other.key))
        val primaryText = PowerPersistenceCodec.encode(wrong)
        val backupText = PowerPersistenceCodec.encode(expected)
        val persistence = MemoryPersistence().apply { value = primaryText; backup = backupText }

        val restored = PowerStorage(persistence).snapshot()

        assertEquals(expected, restored.copy(recoveryMessage = null))
        assertFalse(restored.recoveryMessage.isNullOrBlank())
        assertEquals(binding.key, restored.monitor.cachedBindingKey)
        assertEquals(expected.monitor.samples, restored.monitor.samples)
        assertEquals(expected.monitor.reading, restored.monitor.reading)
        assertEquals(primaryText, persistence.value)
        assertEquals(backupText, persistence.backup)
        assertEquals(0, persistence.writes)
    }

    @Test fun cancellationFromWarningIsNotPersistedAsSuccessfulDelivery() {
        val store = bound()
        val monitor = PowerMonitor(store.monitorStore(), PowerDetailSource { detail }, clock, {
            throw CancellationException("synthetic cancellation")
        })
        assertThrows(CancellationException::class.java) { runBlocking { monitor.query(force = true) } }
        assertEquals(now, store.state().lastSampleAt)
        assertEquals(0L, store.state().lowWarnAt)
    }
}
