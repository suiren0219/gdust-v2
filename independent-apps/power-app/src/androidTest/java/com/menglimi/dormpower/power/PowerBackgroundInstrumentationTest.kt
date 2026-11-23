package com.menglimi.dormpower.power

import android.content.Context
import android.content.Intent
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.google.gson.JsonObject
import com.menglimi.power.PowerDetailSource
import com.menglimi.power.PowerQueryPreset
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Exercises the production synchronous runner and restore/reconcile entry points. */
class PowerBackgroundInstrumentationTest {
    @Test
    fun realBackgroundRunnerPersistsSuccessSkipsAndFailureWithoutRealNetwork() = runBlocking(Dispatchers.IO) {
        val context = instrumentationAppContext()
        SyntheticPowerPersistence(context).use { persistence ->
            val storage = PowerStorage(persistence)
            storage.bind(syntheticBinding, SYNTHETIC_ROOM, 0L)
            storage.configure { it.copy(preset = PowerQueryPreset.CUSTOM, customIntervalMinutes = 15) }
            val diagnostics = TestBackgroundDiagnostics()
            val clock = AdvancingPowerClock()
            PowerNetworkFixture().use { fixture ->
                val runner = PowerBackgroundRunner(storage, fixture.api, diagnostics, clock)
                val before = ResourceSample.take(context)
                val revision = storage.snapshot().bindingRevision
                assertEquals(PowerBackgroundOutcome.SUCCESS, runner.run(revision) { true })
                val after = ResourceSample.take(context)
                assertEquals(PowerBackgroundOutcome.SUCCESS, diagnostics.status.lastOutcome)
                assertEquals(clock.millis(), diagnostics.status.lastRunAt)
                assertEquals(clock.millis(), diagnostics.status.lastSuccessAt)
                assertEquals(1, PowerStorage(persistence).state().samples.size)
                assertEquals(1, fixture.calls.get())
                assertEncryptedOnDisk(persistence)

                assertEquals(PowerBackgroundOutcome.SKIPPED, runner.run(revision) { false })
                assertEquals(1, fixture.calls.get())
                assertEquals(PowerBackgroundOutcome.SKIPPED, diagnostics.status.lastOutcome)
                assertEquals(PowerBackgroundOutcome.SKIPPED, runner.run(revision - 1) { true })
                assertEquals(1, fixture.calls.get())
                storage.configure { it.copy(preset = PowerQueryPreset.MANUAL) }
                clock.advanceMinutes(15)
                // The explicit boot opt-in is independent of the old foreground refresh preset.
                assertEquals(PowerBackgroundOutcome.SUCCESS, runner.run(revision) { true })
                assertEquals(2, fixture.calls.get())

                storage.configure { it.copy(preset = PowerQueryPreset.CUSTOM, customIntervalMinutes = 15) }
                clock.advanceMinutes(15)
                val failing = PowerBackgroundRunner(storage, PowerDetailSource { throw IOException("synthetic failure") }, diagnostics, clock)
                assertEquals(PowerBackgroundOutcome.FAILURE, failing.run(revision) { true })
                assertEquals(PowerBackgroundOutcome.FAILURE, diagnostics.status.lastOutcome)
                assertEquals(clock.millis(), diagnostics.status.lastFailureAt)
                assertEquals(clock.millis(), PowerStorage(persistence).state().lastAttemptAt)
                assertEquals(2, PowerStorage(persistence).state().samples.size)
                assertEquals(PowerBackgroundOutcome.SKIPPED, runner.run(revision) { false })
                assertEquals(2, fixture.calls.get())

                clock.advanceMinutes(15)
                val timedOut = PowerBackgroundRunner(storage, PowerDetailSource { awaitCancellation() }, diagnostics, clock, timeoutMs = 30)
                assertEquals(PowerBackgroundOutcome.FAILURE, timedOut.run(revision) { true })
                assertEquals(PowerBackgroundOutcome.FAILURE, diagnostics.status.lastOutcome)
                clock.advanceMinutes(15)
                val cancelled = PowerBackgroundRunner(storage, PowerDetailSource { throw CancellationException("synthetic cancellation") }, diagnostics, clock)
                var cancellationPropagated = false
                try {
                    cancelled.run(revision) { true }
                } catch (_: CancellationException) {
                    cancellationPropagated = true
                }
                assertTrue(cancellationPropagated)
                assertEquals(PowerBackgroundOutcome.CANCELLED, diagnostics.status.lastOutcome)
                emitPowerReport(JsonObject().apply {
                    addProperty("test", "realBackgroundRunnerPersistsSuccessSkipsAndFailureWithoutRealNetwork")
                    add("success", resourceReport("production PowerBackgroundRunner + fixture API + encrypted persistence", before, after, 1))
                    addProperty("successSamplesRestored", 2)
                    addProperty("fixtureCalls", fixture.calls.get())
                    addProperty("optOutAndRevisionSkipsVerified", true)
                    addProperty("manualPresetAllowsExplicitBootOptIn", true)
                    addProperty("optOutAfterFailureSkipsVerified", true)
                    addProperty("timeoutAsFailureVerified", true)
                    addProperty("externalCancellationPropagated", cancellationPropagated)
                })
            }
        }
    }

    @Test
    fun testOneShotBootRestoreAndEnsureNeverEnqueues() = runBlocking(Dispatchers.IO) {
        val context = instrumentationAppContext()
        assertFalse("Keep the real app opted out so queued workers cannot query the network",
            PowerScheduling.bootQueryEnabled(context))
        val manager = WorkManager.getInstance(context)
        SyntheticPowerPersistence(context).use { persistence ->
            val storage = PowerStorage(persistence)
            storage.bind(syntheticBinding, SYNTHETIC_ROOM, 0L)
            storage.configure { it.copy(preset = PowerQueryPreset.CUSTOM, customIntervalMinutes = 15) }
            val names = PowerWorkNames("${persistence.name}_legacy_periodic", "${persistence.name}_legacy_catchup",
                "${persistence.name}_boot_query")
            val preferences = context.getSharedPreferences("${persistence.name}_boot_opt_in", Context.MODE_PRIVATE)
            val data = Data.Builder().putString("syntheticCaseId", persistence.caseId).build()
            val restoreCalls = java.util.concurrent.atomic.AtomicInteger()
            try {
                assertTrue(preferences.edit().putBoolean("enabled", false).commit())
                suspend fun reconcile(startupEvent: Boolean) = PowerScheduling.reconcile(
                    manager, storage, names, startupEvent,
                    enabled = preferences.getBoolean("enabled", false),
                )
                suspend fun restore(action: String): Boolean = PowerBootReceiver.restore(action) {
                    restoreCalls.incrementAndGet()
                    reconcile(startupEvent = true)
                }

                // Delayed probe seeds exercise legacy cancellation without dispatching any query.
                val legacyPeriodic = PeriodicWorkRequestBuilder<PowerInstrumentationProbeWorker>(15, TimeUnit.MINUTES)
                    .setInitialDelay(60, TimeUnit.MINUTES).setInputData(data).addTag(names.periodic).build()
                manager.enqueueUniquePeriodicWork(names.periodic, ExistingPeriodicWorkPolicy.KEEP, legacyPeriodic)
                    .result.get(15, TimeUnit.SECONDS)
                val legacyCatchup = OneTimeWorkRequestBuilder<PowerInstrumentationProbeWorker>()
                    .setInitialDelay(60, TimeUnit.MINUTES).setInputData(data).addTag(names.catchUp).build()
                manager.enqueueUniqueWork(names.catchUp, ExistingWorkPolicy.KEEP, legacyCatchup)
                    .result.get(15, TimeUnit.SECONDS)

                reconcile(startupEvent = false)
                assertEquals(0, activeWork(manager, names.bootQuery).size)
                assertAllFinished(manager, names.periodic)
                assertAllFinished(manager, names.catchUp)
                assertTrue(restore(Intent.ACTION_BOOT_COMPLETED))
                assertTrue(restore(Intent.ACTION_MY_PACKAGE_REPLACED))
                assertTrue(manager.getWorkInfosForUniqueWork(names.bootQuery).get(15, TimeUnit.SECONDS).isEmpty())
                assertFalse(PowerBootReceiver.restore(Intent.ACTION_SCREEN_ON) {
                    throw AssertionError("Unrelated actions must not enter the restore callback")
                })
                assertEquals(2, restoreCalls.get())

                // The test owns the isolated opt-in preference; restore only reads the gateway.
                assertTrue(preferences.edit().putBoolean("enabled", true).commit())
                reconcile(startupEvent = false)
                assertTrue(manager.getWorkInfosForUniqueWork(names.bootQuery).get(15, TimeUnit.SECONDS).isEmpty())
                assertTrue(restore(Intent.ACTION_BOOT_COMPLETED))
                val bootRecords = manager.getWorkInfosForUniqueWork(names.bootQuery).get(15, TimeUnit.SECONDS)
                assertEquals(1, bootRecords.size)
                val bootRecord = bootRecords.single()
                assertTrue(bootRecord.tags.contains(PowerBootQueryWorker::class.java.name))

                assertTrue(restore(Intent.ACTION_BOOT_COMPLETED))
                val repeatedBootRecords = manager.getWorkInfosForUniqueWork(names.bootQuery).get(15, TimeUnit.SECONDS)
                assertTrue("KEEP must not leave concurrent boot queries",
                    repeatedBootRecords.count { !it.state.isFinished } <= 1)
                val originalAfterRepeat = manager.getWorkInfoById(bootRecord.id).get(15, TimeUnit.SECONDS)!!
                if (!originalAfterRepeat.state.isFinished) {
                    assertEquals(listOf(bootRecord.id), repeatedBootRecords.map { it.id })
                }

                // A separate update event must also enqueue after the earlier query is cancelled.
                manager.cancelUniqueWork(names.bootQuery).result.get(15, TimeUnit.SECONDS)
                val beforeUpdate = manager.getWorkInfosForUniqueWork(names.bootQuery).get(15, TimeUnit.SECONDS).map { it.id }.toSet()
                assertTrue(restore(Intent.ACTION_MY_PACKAGE_REPLACED))
                val updateRecords = manager.getWorkInfosForUniqueWork(names.bootQuery).get(15, TimeUnit.SECONDS)
                val updateRecord = updateRecords.single { it.id !in beforeUpdate }
                assertTrue(updateRecord.tags.contains(PowerBootQueryWorker::class.java.name))
                assertTrue(restore(Intent.ACTION_MY_PACKAGE_REPLACED))
                val repeatedUpdateRecords = manager.getWorkInfosForUniqueWork(names.bootQuery).get(15, TimeUnit.SECONDS)
                assertTrue("KEEP must not leave concurrent update queries",
                    repeatedUpdateRecords.count { !it.state.isFinished } <= 1)
                val updateAfterRepeat = manager.getWorkInfoById(updateRecord.id).get(15, TimeUnit.SECONDS)!!
                if (!updateAfterRepeat.state.isFinished) {
                    assertEquals(listOf(updateRecord.id), repeatedUpdateRecords.filterNot { it.state.isFinished }.map { it.id })
                }

                // MANUAL controls foreground refresh; the explicit boot opt-in remains eligible.
                manager.cancelUniqueWork(names.bootQuery).result.get(15, TimeUnit.SECONDS)
                storage.configure { it.copy(preset = PowerQueryPreset.MANUAL) }
                val beforeManual = manager.getWorkInfosForUniqueWork(names.bootQuery).get(15, TimeUnit.SECONDS).map { it.id }.toSet()
                assertTrue(restore(Intent.ACTION_BOOT_COMPLETED))
                assertTrue(manager.getWorkInfosForUniqueWork(names.bootQuery).get(15, TimeUnit.SECONDS)
                    .any { it.id !in beforeManual && it.tags.contains(PowerBootQueryWorker::class.java.name) })

                storage.unbind()
                assertTrue(restore(Intent.ACTION_MY_PACKAGE_REPLACED))
                assertAllFinished(manager, names.bootQuery)
                storage.bind(syntheticBinding, SYNTHETIC_ROOM, storage.snapshot().bindingRevision)
                assertTrue(preferences.edit().putBoolean("enabled", false).commit())
                assertTrue(restore(Intent.ACTION_BOOT_COMPLETED))
                assertTrue(restore(Intent.ACTION_MY_PACKAGE_REPLACED))
                reconcile(startupEvent = false)
                assertAllFinished(manager, names.bootQuery)
                assertFalse(PowerScheduling.bootQueryEnabled(context))

                val report = JsonObject().apply {
                    addProperty("test", "testOneShotBootRestoreAndEnsureNeverEnqueues")
                    addProperty("productionRestoreEntryPoint", "PowerBootReceiver.restore")
                    addProperty("productionSchedulingEntryPoint", "PowerScheduling.reconcile")
                    addProperty("unrelatedActionIgnored", true)
                    addProperty("ensureWithOptInDoesNotEnqueue", true)
                    addProperty("legacyPeriodicQueueCancelled", true)
                    addProperty("legacyCatchUpQueueCancelled", true)
                    addProperty("bootEnqueuedWorker", PowerBootQueryWorker::class.java.name)
                    addProperty("activeOneTimeQueriesAfterRepeatedBroadcasts", repeatedUpdateRecords.count { !it.state.isFinished })
                    addProperty("keepCheckedWhilePending", !originalAfterRepeat.state.isFinished || !updateAfterRepeat.state.isFinished)
                    addProperty("manualPresetAllowsExplicitBootOptIn", true)
                    addProperty("unboundCancelsBootQuery", true)
                    addProperty("optOutBlocksBootQuery", true)
                    addProperty("restoreActionsHandled", restoreCalls.get())
                    addProperty("debugAppPreferenceRemainsOptedOut", !PowerScheduling.bootQueryEnabled(context))
                    addProperty("actualNetworkRequests", 0)
                    addProperty("rebootOrBroadcastDeliveryObserved", false)
                }
                emitPowerReport(report)
            } finally {
                manager.cancelUniqueWork(names.periodic).result.get(15, TimeUnit.SECONDS)
                manager.cancelUniqueWork(names.catchUp).result.get(15, TimeUnit.SECONDS)
                manager.cancelUniqueWork(names.bootQuery).result.get(15, TimeUnit.SECONDS)
                context.deleteSharedPreferences("${persistence.name}_boot_opt_in")
            }
        }
    }

    private fun activeWork(manager: WorkManager, name: String): List<WorkInfo> =
        manager.getWorkInfosForUniqueWork(name).get(15, TimeUnit.SECONDS).filterNot { it.state.isFinished }

    private fun assertAllFinished(manager: WorkManager, name: String) {
        assertTrue("Expected no unfinished WorkManager records for $name",
            manager.getWorkInfosForUniqueWork(name).get(15, TimeUnit.SECONDS).none { !it.state.isFinished })
    }
}

internal class TestBackgroundDiagnostics : PowerBackgroundDiagnostics {
    var status = PowerBackgroundStatus()
        private set
    override fun update(transform: (PowerBackgroundStatus) -> PowerBackgroundStatus) { status = transform(status) }
}
