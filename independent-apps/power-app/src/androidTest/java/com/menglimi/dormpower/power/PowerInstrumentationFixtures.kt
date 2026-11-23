package com.menglimi.dormpower.power

import android.app.job.JobScheduler
import android.content.Context
import android.content.pm.PackageManager
import android.net.TrafficStats
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.os.Process
import android.os.SystemClock
import android.system.Os
import android.system.OsConstants
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import com.menglimi.power.PowerApi
import com.menglimi.power.PowerBinding
import java.io.File
import java.io.IOException
import java.security.KeyStore
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import okhttp3.Dns
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue

/** All files and Android Keystore aliases belong to a synthetic, uniquely named debug test case. */
@Suppress("DEPRECATION")
internal class SyntheticPowerPersistence(
    val context: Context,
    val caseId: String = UUID.randomUUID().toString().replace("-", ""),
) : PowerPersistence, AutoCloseable {
    val name = "__instrumentation_power_$caseId"
    private val keyAlias = "${name}_master"
    private val masterKey = MasterKey.Builder(context, keyAlias)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
    val preferences = EncryptedSharedPreferences.create(
        context, name, masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )
    val commits = AtomicInteger()
    val file: File get() = File(context.applicationInfo.dataDir, "shared_prefs/$name.xml")

    override fun read(): String? = preferences.getString(SNAPSHOT, null)
    override fun readBackup(): String? = preferences.getString(BACKUP, null)

    override fun write(value: String) {
        // Same encrypted primitive and two-entry commit as PowerStore; no production preference access.
        val next = PowerPersistenceCodec.decode(value)
        fun matching(text: String?): String? = text?.takeIf {
            runCatching { PowerPersistenceCodec.decode(it).config.binding == next.config.binding }.getOrDefault(false)
        }
        val backup = if (next.config.binding == null) value else matching(read()) ?: matching(readBackup()) ?: value
        if (!preferences.edit().putString(SNAPSHOT, value).putString(BACKUP, backup).commit()) {
            throw IOException("Synthetic encrypted commit failed")
        }
        commits.incrementAndGet()
    }

    override fun close() {
        // Never clear a directory, delete another alias, or touch release data.
        assertTrue(name.startsWith("__instrumentation_power_"))
        context.deleteSharedPreferences(name)
        KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.deleteEntry(keyAlias)
    }

    companion object {
        const val SNAPSHOT = "snapshot_v1"
        const val BACKUP = "snapshot_backup_v1"
    }
}

internal val syntheticBinding = PowerBinding("SYNTHETIC-INSTRUMENTATION-ACCOUNT", 999999, "SYNTHETIC-ROOM-VERIFY")
internal const val SYNTHETIC_ROOM = "INSTRUMENTATION-ONLY-ROOM"

/** The application interceptor always returns a fixture; the DNS guard prevents socket fallback. */
internal class PowerNetworkFixture : AutoCloseable {
    val calls = AtomicInteger()
    val requestPayloadBytes = AtomicLong()
    val responsePayloadBytes = AtomicLong()
    val dnsCalls = AtomicInteger()
    private val responseText = """{"success":true,"body":{"modlist":[{"odd":"87.50 度","todayuse":"1.25","weekuselist":[{"weekday":"星期一","dayuse":"1.25"},{"weekday":"星期二","dayuse":"1.5"}],"monthuselist":[{"monthuse":"41.75"}],"sumbuy":"300"}]}}"""
    val client = OkHttpClient.Builder()
        .retryOnConnectionFailure(false)
        .dns(object : Dns {
            override fun lookup(hostname: String): List<java.net.InetAddress> {
                dnsCalls.incrementAndGet()
                throw IOException("Network is forbidden in the instrumentation fixture")
            }
        })
        .addInterceptor { chain ->
            val request = chain.request()
            val form = request.body as? FormBody ?: throw IOException("Unexpected fixture body")
            val parameters = (0 until form.size).associate { form.name(it) to form.value(it) }
            check(parameters["method"] == "h5_getstuindexpage")
            check(parameters["param"].orEmpty().contains(syntheticBinding.account))
            check(parameters["param"].orEmpty().contains(syntheticBinding.verify))
            calls.incrementAndGet()
            requestPayloadBytes.addAndGet(Buffer().also { form.writeTo(it) }.size)
            responsePayloadBytes.addAndGet(responseText.toByteArray(Charsets.UTF_8).size.toLong())
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(200).message("Synthetic fixture")
                .body(responseText.toResponseBody("application/json; charset=utf-8".toMediaType())).build()
        }.build()
    val api = PowerApi(client = client, retryBackoff = {})

    override fun close() {
        client.dispatcher.executorService.shutdown()
        client.connectionPool.evictAll()
        assertEquals("No DNS lookup may occur", 0, dnsCalls.get())
    }
}

internal class AdvancingPowerClock(
    private var now: Long = Instant.parse("2026-11-01T00:00:00Z").toEpochMilli(),
) : Clock() {
    override fun getZone(): ZoneId = ZoneOffset.UTC
    override fun withZone(zone: ZoneId): Clock = Clock.fixed(instant(), zone)
    override fun instant(): Instant = Instant.ofEpochMilli(now)
    override fun millis(): Long = now
    fun advanceMinutes(minutes: Long) { now += minutes * 60_000L }
}

internal fun instrumentationAppContext(): Context {
    val context = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
    assertTrue("Tests must only target the isolated instrumentation app", context.packageName == "cn.edu.gdust.suiren.power.backgroundtest")
    assertTrue("Never run against release data", context.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0)
    return context
}

internal data class ResourceSample(
    val wallNanos: Long,
    val processCpuMillis: Long,
    val rxBytes: Long,
    val txBytes: Long,
    val battery: JsonObject,
    val pendingJobs: Int?,
) {
    companion object {
        fun take(context: Context): ResourceSample {
            // Battery/JobScheduler read overhead is excluded from the beginning of an interval.
            val battery = battery(context)
            val jobs = runCatching { context.getSystemService(JobScheduler::class.java).allPendingJobs.size }.getOrNull()
            val rx = TrafficStats.getUidRxBytes(Process.myUid())
            val tx = TrafficStats.getUidTxBytes(Process.myUid())
            val cpu = Process.getElapsedCpuTime()
            return ResourceSample(SystemClock.elapsedRealtimeNanos(), cpu, rx, tx, battery, jobs)
        }

        private fun battery(context: Context): JsonObject {
            val manager = context.getSystemService(BatteryManager::class.java)
            return JsonObject().apply {
                fun property(name: String, id: Int, unit: String) {
                    val value = manager.getLongProperty(id)
                    add(name, JsonObject().apply {
                        addProperty("supportedByAndroidApi", value != Long.MIN_VALUE)
                        if (value != Long.MIN_VALUE) addProperty("value", value) else add("value", JsonNull.INSTANCE)
                        addProperty("unit", unit)
                    })
                }
                property("chargeCounter", BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER, "uAh")
                property("currentNow", BatteryManager.BATTERY_PROPERTY_CURRENT_NOW, "uA")
                property("currentAverage", BatteryManager.BATTERY_PROPERTY_CURRENT_AVERAGE, "uA")
                property("capacity", BatteryManager.BATTERY_PROPERTY_CAPACITY, "percent")
                property("energyCounter", BatteryManager.BATTERY_PROPERTY_ENERGY_COUNTER, "nWh")
                addProperty("charging", manager.isCharging)
                addProperty("hardwareEnergyValidity", "unverified; emulator values are synthetic even when Android API reports support")
            }
        }
    }
}

internal fun resourceReport(
    label: String,
    before: ResourceSample,
    after: ResourceSample,
    operations: Int,
): JsonObject = JsonObject().apply {
    addProperty("label", label)
    addProperty("operations", operations)
    addProperty("wallMs", (after.wallNanos - before.wallNanos) / 1_000_000.0)
    addProperty("processCpuMs", after.processCpuMillis - before.processCpuMillis)
    addProperty("processCpuReportedUnitMs", 1)
    addProperty("systemClockTickMs", 1000.0 / Os.sysconf(OsConstants._SC_CLK_TCK))
    addProperty("processCpuPrecision", "integer millisecond API output; actual precision not calibrated; Linux CLK_TCK is not this API precision")
    addProperty("processCpuScope", "whole debug target process, including instrumentation and OkHttp/coroutine threads")
    val supported = before.rxBytes >= 0 && after.rxBytes >= 0 && before.txBytes >= 0 && after.txBytes >= 0
    addProperty("uidTrafficSupported", supported)
    if (supported) {
        addProperty("uidRxBytesDelta", after.rxBytes - before.rxBytes)
        addProperty("uidTxBytesDelta", after.txBytes - before.txBytes)
    }
    addProperty("realNetworkRequests", 0)
    addProperty("radioEnergyMeasured", false)
    add("batteryBefore", before.battery)
    add("batteryAfter", after.battery)
    before.pendingJobs?.let { addProperty("appPendingJobsBefore", it) }
    after.pendingJobs?.let { addProperty("appPendingJobsAfter", it) }
}

internal fun emitPowerReport(report: JsonObject) {
    report.addProperty("device", "${Build.MANUFACTURER}/${Build.MODEL}")
    report.addProperty("fingerprint", Build.FINGERPRINT)
    report.addProperty("api", Build.VERSION.SDK_INT)
    report.addProperty("emulator", Build.FINGERPRINT.contains("generic") || Build.MODEL.contains("sdk", true) || Build.HARDWARE in setOf("ranchu", "goldfish"))
    report.addProperty("storageScope", "uniquely named synthetic preferences and keystore alias in debug app; no release data")
    report.addProperty("dailyBatteryPercent", "not measured; no conversion from emulator CPU or battery readings")
    val line = "POWER_RESOURCE_REPORT $report"
    // Keep each logcat payload below Android's per-entry size limit; the stream retains full JSON too.
    val parts = report.toString().chunked(2500)
    parts.forEachIndexed { index, part ->
        Log.i("PowerResourceTest", "POWER_RESOURCE_REPORT_PART ${index + 1}/${parts.size} $part")
    }
    InstrumentationRegistry.getInstrumentation().sendStatus(2, Bundle().apply { putString("stream", "\n$line\n") })
}

internal fun assertEncryptedOnDisk(persistence: SyntheticPowerPersistence) {
    val disk = persistence.file.readText()
    listOf(syntheticBinding.account, syntheticBinding.verify, SYNTHETIC_ROOM, "\"config\"", "\"samples\"").forEach {
        assertFalse("Sensitive synthetic values must be encrypted on disk", disk.contains(it))
    }
}

internal fun idleState(context: Context): JsonObject = JsonObject().apply {
    val power = context.getSystemService(PowerManager::class.java)
    addProperty("interactive", power.isInteractive)
    addProperty("deviceIdleMode", power.isDeviceIdleMode)
    addProperty("batteryStatsPermission", context.checkSelfPermission("android.permission.BATTERY_STATS") == PackageManager.PERMISSION_GRANTED)
    addProperty("osWakeupCount", "unavailable without privileged per-UID BatteryStats access; worker executions are counted separately")
}
