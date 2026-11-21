package com.menglimi.dormpower.power

import android.os.Process
import android.os.SystemClock
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.menglimi.power.PowerMonitor
import com.menglimi.power.PowerQueryPreset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Reproducible device-side costs, not a phone battery-drain benchmark. */
class PowerResourceInstrumentationTest {
    private fun distribution(values: List<Double>): JsonObject {
        val sorted = values.sorted()
        return JsonObject().apply {
            addProperty("count", sorted.size)
            addProperty("total", sorted.sum())
            addProperty("mean", sorted.average())
            addProperty("median", (sorted[sorted.size / 2 - 1] + sorted[sorted.size / 2]) / 2)
            addProperty("p95NearestRank", sorted[kotlin.math.ceil(sorted.size * 0.95).toInt() - 1])
            addProperty("min", sorted.first())
            addProperty("max", sorted.last())
            add("samples", JsonArray().apply { values.forEach { add(it) } })
        }
    }
    @Test
    fun queryParseAndEncryptedCommitCostsAreMeasuredAndRestorable() = runBlocking(Dispatchers.IO) {
        val context = instrumentationAppContext()
        PowerNetworkFixture().use { fixture ->
            val clock = AdvancingPowerClock()
            val initializationBefore = ResourceSample.take(context)
            SyntheticPowerPersistence(context).use { persistence ->
                val storage = PowerStorage(persistence)
                storage.bind(syntheticBinding, SYNTHETIC_ROOM, 0L)
                storage.configure { it.copy(preset = PowerQueryPreset.CUSTOM, customIntervalMinutes = 15) }
                val initializationAfter = ResourceSample.take(context)
                val monitor = PowerMonitor(storage.monitorStore(background = true), fixture.api, clock)

                val singleBefore = ResourceSample.take(context)
                val single = monitor.query(background = true)
                val singleAfter = ResourceSample.take(context)
                assertTrue(single.sampled)
                assertEquals(87.5, requireNotNull(single.reading?.detail?.odd), 0.0)
                assertEquals(1.25, requireNotNull(single.reading?.detail?.todayUse), 0.0)
                assertEquals(41.75, requireNotNull(single.reading?.detail?.monthUse), 0.0)
                assertEquals(2, single.reading?.detail?.weekUse?.size)
                assertEquals(1, fixture.calls.get())
                assertEncryptedOnDisk(persistence)

                val commitsBeforeBatch = persistence.commits.get()
                val individualWallMs = ArrayList<Double>(96)
                val individualCpuMs = ArrayList<Double>(96)
                val batchBefore = ResourceSample.take(context)
                repeat(96) {
                    // Logical policy time only: do not wait 24 hours or claim 24 hours of battery measurement.
                    clock.advanceMinutes(15)
                    val cpuBefore = Process.getElapsedCpuTime()
                    val wallBefore = SystemClock.elapsedRealtimeNanos()
                    val result = monitor.query(background = true)
                    individualWallMs += (SystemClock.elapsedRealtimeNanos() - wallBefore) / 1_000_000.0
                    individualCpuMs += (Process.getElapsedCpuTime() - cpuBefore).toDouble()
                    assertTrue(result.sampled)
                }
                val batchAfter = ResourceSample.take(context)
                assertEquals(97, fixture.calls.get())
                assertEquals(192, persistence.commits.get() - commitsBeforeBatch)
                assertEquals(97, storage.state().samples.size)
                assertEncryptedOnDisk(persistence)
                val restored = PowerStorage(persistence)
                assertEquals(storage.snapshot(), restored.snapshot())
                assertEquals(97, restored.state().samples.size)

                val commitsBeforeCached = persistence.commits.get()
                assertTrue(!monitor.query(background = true).sampled)
                assertEquals(97, fixture.calls.get())
                assertEquals(commitsBeforeCached, persistence.commits.get())

                val report = JsonObject().apply {
                    addProperty("test", "queryParseAndEncryptedCommitCostsAreMeasuredAndRestorable")
                    add("coldEncryptedStorageInitialization", resourceReport("new keystore key + bind + configure", initializationBefore, initializationAfter, 1))
                    add("singleQuery", resourceReport("PowerApi parse + PowerMonitor + two encrypted commits", singleBefore, singleAfter, 1))
                    add("batchQueries", resourceReport("96 fixture queries with growing history + 192 encrypted commits", batchBefore, batchAfter, 96).apply {
                        addProperty("meanWallMs", (batchAfter.wallNanos - batchBefore.wallNanos) / 1_000_000.0 / 96)
                        addProperty("meanProcessCpuMs", (batchAfter.processCpuMillis - batchBefore.processCpuMillis) / 96.0)
                        add("perQueryWallMs", distribution(individualWallMs))
                        add("perQueryProcessCpuMs", distribution(individualCpuMs))
                        addProperty("loopTemperature", "warm process after first fixture query; history grows from 1 to 97 samples; no JIT warm-up pass")
                    })
                    addProperty("fixtureCalls", fixture.calls.get())
                    addProperty("fixtureFormPayloadBytes", fixture.requestPayloadBytes.get())
                    addProperty("fixtureResponsePayloadBytes", fixture.responsePayloadBytes.get())
                    addProperty("payloadBytesMeaning", "in-memory UTF-8 form/response body only, not TLS, IP, DNS, or radio bytes")
                    addProperty("socketConnections", 0)
                    addProperty("dnsCalls", fixture.dnsCalls.get())
                    addProperty("encryptedFileBytes", persistence.file.length())
                    addProperty("restoredSamples", restored.state().samples.size)
                    addProperty("cachedQueryExtraRequests", 0)
                    addProperty("cachedQueryExtraCommits", 0)
                    addProperty("logicalTimeAdvanceMinutes", 96 * 15)
                    addProperty("logicalClockPurpose", "synthetic unique sample timestamps for this CPU/parse/encrypted-persistence cost loop; not a scheduler interval or product run frequency")
                    addProperty("queryLoopSemantics", "96 sequential direct PowerMonitor calls; no recurring or periodic work is created by this cost test")
                    addProperty("temperatureMeaning", "coldEncryptedStorageInitialization is a new alias/preferences setup in an already running instrumented process, not OS app cold-start time; singleQuery is first request for this fixture")
                    addProperty("timingIncludes", "production query/parse/storage path; process scheduling, instrumentation, and observation overhead; no real network latency")
                }
                emitPowerReport(report)
            }
        }
    }

    @Test
    fun encryptedSnapshotRecoversFromBackupWithoutUsingProductionPreferences() = runBlocking(Dispatchers.IO) {
        val context = instrumentationAppContext()
        SyntheticPowerPersistence(context).use { persistence ->
            val storage = PowerStorage(persistence)
            storage.bind(syntheticBinding, SYNTHETIC_ROOM, 0L)
            storage.configure { it.copy(preset = PowerQueryPreset.CUSTOM, customIntervalMinutes = 30) }
            val previous = PowerPersistenceCodec.decode(requireNotNull(persistence.readBackup()))
            assertTrue(persistence.preferences.edit().putString(SyntheticPowerPersistence.SNAPSHOT, "{").commit())
            val recovered = PowerStorage(persistence).snapshot()
            assertEquals(previous.config, recovered.config)
            assertEquals(previous.monitor, recovered.monitor)
            assertNotNull(recovered.recoveryMessage)
            assertEquals("{", persistence.read())
            assertEncryptedOnDisk(persistence)
        }
    }
}
