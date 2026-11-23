package com.menglimi.dormpower.power

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.google.gson.JsonObject
import com.menglimi.power.PowerMonitor
import com.menglimi.power.PowerQueryPreset
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** A real WorkManager-dispatched worker whose network and preferences are entirely synthetic. */
class PowerInstrumentationProbeWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        starts.incrementAndGet()
        val id = inputData.getString("syntheticCaseId") ?: return Result.failure()
        val persistence = SyntheticPowerPersistence(applicationContext, id)
        // The owning test removes only this alias/file after it observes a terminal WorkInfo.
        val storage = PowerStorage(persistence)
        return PowerNetworkFixture().use { fixture ->
            val result = PowerMonitor(storage.monitorStore(background = true), fixture.api, AdvancingPowerClock())
                .query(background = true)
            Result.success(Data.Builder().putBoolean("sampled", result.sampled)
                .putInt("fixtureCalls", fixture.calls.get()).putInt("dnsCalls", fixture.dnsCalls.get()).build())
        }
    }

    companion object {
        val starts = AtomicInteger()
    }
}

class PowerWorkManagerInstrumentationTest {
    @Test
    fun uniquePersistentQueueDispatchesOnceAndIdleWindowDoesNotPoll() = runBlocking(Dispatchers.IO) {
        val context = instrumentationAppContext()
        val manager = WorkManager.getInstance(context)
        SyntheticPowerPersistence(context).use { persistence ->
            val storage = PowerStorage(persistence)
            storage.bind(syntheticBinding, SYNTHETIC_ROOM, 0L)
            storage.configure { it.copy(preset = PowerQueryPreset.CUSTOM, customIntervalMinutes = 15) }
            val name = "${persistence.name}_queue"
            val idleName = "${persistence.name}_idle"
            val data = Data.Builder().putString("syntheticCaseId", persistence.caseId).build()
            val startsBefore = PowerInstrumentationProbeWorker.starts.get()
            try {
                val request = OneTimeWorkRequestBuilder<PowerInstrumentationProbeWorker>()
                    .setInitialDelay(5, TimeUnit.SECONDS).setInputData(data).addTag(name).build()
                manager.enqueueUniqueWork(name, ExistingWorkPolicy.KEEP, request).result.get(15, TimeUnit.SECONDS)
                val queued = manager.getWorkInfosForUniqueWork(name).get(15, TimeUnit.SECONDS)
                assertEquals(listOf(request.id), queued.map { it.id })
                assertEquals(WorkInfo.State.ENQUEUED, queued.single().state)
                val duplicate = OneTimeWorkRequestBuilder<PowerInstrumentationProbeWorker>()
                    .setInputData(data).addTag(name).build()
                manager.enqueueUniqueWork(name, ExistingWorkPolicy.KEEP, duplicate).result.get(15, TimeUnit.SECONDS)
                assertEquals(listOf(request.id), manager.getWorkInfosForUniqueWork(name).get(15, TimeUnit.SECONDS).map { it.id })
                val terminal = awaitTerminal(manager, request.id)
                assertEquals(WorkInfo.State.SUCCEEDED, terminal.state)
                assertTrue(terminal.outputData.getBoolean("sampled", false))
                assertEquals(1, terminal.outputData.getInt("fixtureCalls", -1))
                assertEquals(0, terminal.outputData.getInt("dnsCalls", -1))
                assertEquals(1, PowerStorage(persistence).state().samples.size)
                assertEquals(startsBefore + 1, PowerInstrumentationProbeWorker.starts.get())
                assertEncryptedOnDisk(persistence)

                val idleRequest = OneTimeWorkRequestBuilder<PowerInstrumentationProbeWorker>()
                    .setInitialDelay(60, TimeUnit.MINUTES).setInputData(data).addTag(idleName).build()
                manager.enqueueUniqueWork(idleName, ExistingWorkPolicy.KEEP, idleRequest).result.get(15, TimeUnit.SECONDS)
                // Let WorkManager finish its enqueue bookkeeping, then take one unpolled short window.
                delay(500)
                val idleBefore = ResourceSample.take(context)
                val stateBefore = idleState(context)
                val executionsBefore = PowerInstrumentationProbeWorker.starts.get()
                delay(10_000)
                val idleAfter = ResourceSample.take(context)
                val executionsAfter = PowerInstrumentationProbeWorker.starts.get()
                val pending = manager.getWorkInfoById(idleRequest.id).get(15, TimeUnit.SECONDS)!!
                assertEquals(WorkInfo.State.ENQUEUED, pending.state)
                assertEquals(executionsBefore, executionsAfter)
                assertEquals(0, pending.runAttemptCount)
                emitPowerReport(JsonObject().apply {
                    addProperty("test", "uniquePersistentQueueDispatchesOnceAndIdleWindowDoesNotPoll")
                    addProperty("queuePersistentWorkInfos", queued.size)
                    addProperty("keepRetainedOriginalWorkId", true)
                    addProperty("dispatchedWorkerExecutions", executionsBefore - startsBefore)
                    addProperty("dispatchedFixtureRequests", terminal.outputData.getInt("fixtureCalls", -1))
                    addProperty("restoredWorkerSamples", 1)
                    add("shortIdleWindow", resourceReport("10 second foreground instrumentation process with delayed background work", idleBefore, idleAfter, 0).apply {
                        addProperty("observedTestWorkerExecutions", executionsAfter - executionsBefore)
                        addProperty("queuedWorkRunAttemptCount", pending.runAttemptCount)
                        addProperty("queuedWorkState", pending.state.name)
                        addProperty("scheduledDelayMinutes", 60)
                        add("deviceStateBefore", stateBefore)
                        add("deviceStateAfter", idleState(context))
                        addProperty("limits", "short unpolled process idle window, not forced Doze, reboot, screen-off standby, real radio use, or full-day observation; pending jobs are counts, not OS execution counts")
                    })
                })
            } finally {
                manager.cancelUniqueWork(name).result.get(15, TimeUnit.SECONDS)
                manager.cancelUniqueWork(idleName).result.get(15, TimeUnit.SECONDS)
            }
        }
    }

    private suspend fun awaitTerminal(manager: WorkManager, id: java.util.UUID): WorkInfo {
        val deadline = android.os.SystemClock.elapsedRealtime() + 30_000
        while (android.os.SystemClock.elapsedRealtime() < deadline) {
            val info = manager.getWorkInfoById(id).get(5, TimeUnit.SECONDS)
            if (info?.state?.isFinished == true) return info
            delay(50)
        }
        throw AssertionError("Synthetic WorkManager worker did not finish within 30 seconds")
    }
}
