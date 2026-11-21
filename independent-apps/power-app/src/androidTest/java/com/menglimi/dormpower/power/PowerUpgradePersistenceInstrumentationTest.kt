package com.menglimi.dormpower.power

import android.Manifest
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.menglimi.power.PowerBinding
import com.menglimi.power.PowerDetail
import com.menglimi.power.PowerDetailSource
import com.menglimi.power.PowerMonitor
import com.menglimi.power.PowerMonitorState
import com.menglimi.power.PowerQueryPreset
import com.menglimi.power.PowerReading
import com.menglimi.power.PowerSample
import java.io.File
import java.security.MessageDigest
import java.time.Clock
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * 跨 APK / 进程测试；普通测试套件未传 powerUpgradePhase 时跳过，绝不清理应用数据。
 *
 * 只在独立构建副本运行：debug applicationIdSuffix=.upgradeqa，两版使用同一 debug 签名，
 * 两版隔离 Manifest 均删除 INTERNET 权限。旧树取当前 0.1.5，不能用更早的 git HEAD 代替。
 * 每阶段都用 ANDROID_SERIAL=emulator-5554，禁止 adb / wrapper / 真机 / 真实接口。
 *
 * 共同 Gradle 参数（分别在旧、新独立树的 independent-apps 目录执行）：
 * -PstandaloneApp=power -PpowerTestApplicationSuffix=.upgradeqa
 * -Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true
 * -Pandroid.testInstrumentationRunnerArguments.class=com.menglimi.dormpower.power.PowerUpgradePersistenceInstrumentationTest
 * -Pandroid.testInstrumentationRunnerArguments.powerUpgradeRunId=<同一个独立用例 ID>
 * -Pandroid.testInstrumentationRunnerArguments.powerUpgradeSerial=emulator-5554
 *
 * 顺序：
 * 1. 旧版 :power-app:connectedDebugAndroidTest，powerUpgradePhase=seed。
 * 2. 新版 :power-app:installDebug，再 connectedDebugAndroidTest，powerUpgradePhase=verify。
 * 3. 新版重新执行 connectedDebugAndroidTest --rerun-tasks，powerUpgradePhase=verifyPersisted。
 * 每阶段参数均为 -Pandroid.testInstrumentationRunnerArguments.<参数名>=<值>。
 * 不得在阶段之间 uninstall / clear / 使用会自动清理数据的 Orchestrator。
 * 若 AGP 没有保留安装或数据，本测试必须失败，不能用重新 seed 冒充升级通过。
 *
 * 可选 powerUpgradeFixture=splitBackup：旧版实际 writer 先保存全部样本，再保存缺少部分
 * 样本的同绑定主快照；升级应从真实加密备份恢复并集。默认 full 不制造缺失。
 * 结果既通过 instrumentation stream 输出，也写入隔离包 noBackupFilesDir 的 JSON。
 */
class PowerUpgradePersistenceInstrumentationTest {
    @Test
    fun nonEmptyEncryptedDataSurvivesActualApkUpgrade() = runBlocking(Dispatchers.IO) {
        val arguments = InstrumentationRegistry.getArguments()
        val phase = arguments.getString("powerUpgradePhase")
        assumeTrue("Only explicit upgrade QA phases may access PowerStore", phase != null)
        require(phase in setOf("seed", "verify", "verifyPersisted")) { "Unknown powerUpgradePhase" }
        val runId = requireNotNull(arguments.getString("powerUpgradeRunId"))
        require(runId.matches(Regex("[A-Za-z0-9_-]{8,80}"))) { "Use a unique synthetic run ID" }
        val context = guardedContext(arguments)
        // Join the production startup path before observing snapshots or encrypted file digests.
        PowerScheduling.ensure(context).join()
        assertFalse("Synthetic QA must never opt in to boot/network queries", PowerScheduling.bootQueryEnabled(context))
        when (phase) {
            "seed" -> seed(context, arguments, runId)
            "verify" -> verifyUpgrade(context, runId)
            "verifyPersisted" -> verifyPersisted(context, runId)
        }
    }

    private fun guardedContext(arguments: Bundle): Context {
        val context = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
        assertEquals("A dedicated package is mandatory", PACKAGE, context.packageName)
        assertTrue("Release applications are forbidden",
            context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0)
        assertEquals("The host must also set ANDROID_SERIAL to this single device",
            "emulator-5554", arguments.getString("powerUpgradeSerial"))
        assertTrue("Physical devices are forbidden: hardware=${Build.HARDWARE}, product=${Build.PRODUCT}, " +
            "model=${Build.MODEL}, fingerprint=${Build.FINGERPRINT}",
            Build.HARDWARE in setOf("ranchu", "goldfish") &&
                (Build.FINGERPRINT.contains("generic") || Build.FINGERPRINT.contains("emulator") ||
                    Build.MODEL.startsWith("sdk_") || Build.PRODUCT.startsWith("sdk_")))
        assertEquals("Remove INTERNET only in the isolated build copies; never edit the production manifest",
            PackageManager.PERMISSION_DENIED, context.checkSelfPermission(Manifest.permission.INTERNET))
        return context
    }

    private fun seed(context: Context, arguments: Bundle, runId: String) {
        val receiptFile = receiptFile(context)
        assertFalse("Refusing to overwrite an earlier QA run; use a fresh isolated app sandbox", receiptFile.exists())
        val installed = installation(context)
        assertEquals("The writer must really run in the frozen old APK", "0.1.5", installed.get("versionName").asString)
        assertEquals(6L, installed.get("versionCode").asLong)
        val store = PowerStore(context)
        val empty = store.snapshot()
        assertEquals("Refusing to alter any existing binding", null, empty.config.binding)
        assertTrue("Refusing to replace existing history", empty.monitor.samples.isEmpty())
        assertEquals(null, empty.monitor.reading)
        val bound = store.bind(BINDING, ROOM, empty.bindingRevision)
        store.configure {
            it.copy(preset = PowerQueryPreset.MANUAL, customIntervalMinutes = 45,
                lowThreshold = 2.5, dropRateThreshold = 6.75,
                awakeWindow = LocalTime.of(6, 15) to LocalTime.of(23, 45), zone = ZoneId.of("Asia/Shanghai"))
        }
        val expected = seededSamples()
        // Use the installed old application's real writer, codec, encrypted preferences and Keystore.
        // Do not manufacture the encrypted XML or inject a replacement persistence implementation.
        assertTrue(store.update(BINDING) {
            PowerMonitorState(cachedBindingKey = BINDING.key,
                reading = PowerReading(detail(OLD_BALANCE), expected.last().time),
                lastSampleAt = expected.last().time, lastAttemptAt = expected.last().time,
                samples = expected, lowWarnAt = BASE + 31 * DAY, dropWarnAt = BASE + 32 * DAY)
        })
        val fixture = arguments.getString("powerUpgradeFixture") ?: "full"
        require(fixture in setOf("full", "splitBackup"))
        val primarySamples = if (fixture == "splitBackup") expected.filterIndexed { index, _ -> index >= 2 } else expected
        if (fixture == "splitBackup") {
            assertTrue(store.update(BINDING) { it.copy(samples = primarySamples) })
        }
        assertSnapshot(store.snapshot(), primarySamples, OLD_BALANCE, expected.last().time, bound.bindingRevision)
        val encrypted = encryptedSnapshot(context)
        assertEquals(primarySamples, PowerPersistenceCodec.decode(encrypted).monitor.samples)
        val receipt = JsonObject().apply {
            addProperty("runId", runId)
            addProperty("fixture", fixture)
            addProperty("seededSampleCount", expected.size)
            addProperty("oldPrimarySampleCount", primarySamples.size)
            addProperty("oldestSampleAt", expected.first().time)
            addProperty("latestOldSampleAt", expected.last().time)
            addProperty("bindingRevision", bound.bindingRevision)
            addProperty("ciphertextSha256", assertEncryptedFile(context))
            add("oldInstallation", installed)
        }
        if (fixture == "splitBackup") {
            assertEquals("The backup must itself have been written by the old production writer",
                expected, PowerPersistenceCodec.decode(requireNotNull(encryptedBackup(context))).monitor.samples)
        }
        writeJson(receiptFile, receipt)
        report(context, "seed", receipt.deepCopy().apply {
            addProperty("actualApplicationWriter", "PowerStore.bind/configure/update")
            addProperty("oldApkEncryptedDataNonEmpty", true)
            addProperty("actualNetworkRequests", 0)
        })
    }

    private suspend fun verifyUpgrade(context: Context, runId: String) {
        val receipt = readReceipt(context, runId)
        assertFalse("Do not refresh the same fixture twice; rerun only verifyPersisted", receipt.has("verifiedInstallation"))
        val installed = assertUpgrade(context, receipt)
        val old = seededSamples()
        val store = PowerStore(context)
        // This first read is deliberately through the real new PowerStore, in a new APK process.
        assertSnapshot(store.snapshot(), old, OLD_BALANCE, old.last().time, receipt.get("bindingRevision").asLong)
        val beforeRefreshCiphertext = assertEncryptedFile(context)
        assertTrue("The fixture must span more than 30 days", old.last().time - old.first().time > 30 * DAY)
        var sourceCalls = 0
        suspend fun refreshAt(time: Long, balance: Double) {
            val monitor = PowerMonitor(store.monitorStore(), PowerDetailSource { binding ->
                assertEquals(BINDING, binding)
                sourceCalls++
                detail(balance)
            }, Clock.fixed(Instant.ofEpochMilli(time), ZoneOffset.UTC))
            val result = monitor.query(force = true)
            assertTrue("A successful refresh must really sample, including after clock rollback", result.sampled)
            assertEquals(PowerReading(detail(balance), time), result.reading)
        }
        // Forward by >30 days: a pruning implementation would destroy the seeded history here.
        refreshAt(FORWARD_TIME, FORWARD_BALANCE)
        val afterForward = old + PowerSample(FORWARD_TIME, FORWARD_BALANCE)
        assertSnapshot(store.snapshot(), afterForward, FORWARD_BALANCE, FORWARD_TIME,
            receipt.get("bindingRevision").asLong)
        assertEquals(afterForward, PowerPersistenceCodec.decode(encryptedSnapshot(context)).monitor.samples)
        // Roll back the injected clock; never change the emulator's or host's actual clock.
        refreshAt(ROLLBACK_TIME, ROLLBACK_BALANCE)
        val afterRollback = (afterForward + PowerSample(ROLLBACK_TIME, ROLLBACK_BALANCE)).sortedBy { it.time }
        assertSnapshot(store.snapshot(), afterRollback, ROLLBACK_BALANCE, ROLLBACK_TIME,
            receipt.get("bindingRevision").asLong)
        assertEquals(2, sourceCalls)
        assertEquals(afterRollback, PowerPersistenceCodec.decode(encryptedSnapshot(context)).monitor.samples)
        receipt.add("verifiedInstallation", installed)
        receipt.addProperty("refreshProcessPid", android.os.Process.myPid())
        receipt.addProperty("refreshProcessStartedAt", android.os.Process.getStartElapsedRealtime())
        receipt.addProperty("postRefreshCiphertextSha256", assertEncryptedFile(context))
        receipt.addProperty("newReadPreservedOldCiphertext", beforeRefreshCiphertext == receipt.get("ciphertextSha256").asString)
        receipt.addProperty("oldSamplesRestoredBeforeRefresh", old.size)
        receipt.addProperty("postRefreshSampleCount", afterRollback.size)
        writeJson(receiptFile(context), receipt)
        report(context, "verify", receipt.deepCopy().apply {
            addProperty("actualApplicationReader", "PowerStore.snapshot")
            addProperty("successfulRefreshes", sourceCalls)
            addProperty("refreshSource", "in-memory PowerDetailSource; no socket or real account")
            addProperty("olderThan30DaysRetained", true)
            addProperty("clockRollbackRetainedAllOldSamples", true)
            addProperty("actualNetworkRequests", 0)
        })
    }

    private fun verifyPersisted(context: Context, runId: String) {
        val receipt = readReceipt(context, runId)
        assertTrue("Run the new APK verify stage first", receipt.has("verifiedInstallation"))
        val current = assertUpgrade(context, receipt)
        val verified = receipt.getAsJsonObject("verifiedInstallation")
        assertEquals("The final disk read must use the same new APK", verified.get("apkSha256"), current.get("apkSha256"))
        assertTrue("The final read must run in a genuinely new process, not the cached PowerStore singleton",
            receipt.get("refreshProcessPid").asInt != android.os.Process.myPid() ||
                receipt.get("refreshProcessStartedAt").asLong != android.os.Process.getStartElapsedRealtime())
        val expected = (seededSamples() + PowerSample(FORWARD_TIME, FORWARD_BALANCE) +
            PowerSample(ROLLBACK_TIME, ROLLBACK_BALANCE)).sortedBy { it.time }
        // A separate instrumentation invocation drops the preceding process's PowerStore singleton.
        assertSnapshot(PowerStore(context).snapshot(), expected, ROLLBACK_BALANCE, ROLLBACK_TIME,
            receipt.get("bindingRevision").asLong)
        assertEquals("No refresh or rewrite is allowed during the final read",
            receipt.get("postRefreshCiphertextSha256").asString, assertEncryptedFile(context))
        assertEquals(expected, PowerPersistenceCodec.decode(encryptedSnapshot(context)).monitor.samples)
        report(context, "verifyPersisted", receipt.deepCopy().apply {
            addProperty("readInSeparateInstrumentationInvocation", true)
            addProperty("oldSamplesRetained", seededSamples().size)
            addProperty("totalSamplesRestoredFromDisk", expected.size)
            addProperty("actualNetworkRequests", 0)
        })
    }

    private fun assertSnapshot(snapshot: PowerSnapshot, samples: List<PowerSample>, balance: Double,
                               updatedAt: Long, revision: Long) {
        assertEquals(BINDING, snapshot.config.binding)
        assertEquals(ROOM, snapshot.roomName)
        assertEquals(revision, snapshot.bindingRevision)
        assertEquals(PowerQueryPreset.MANUAL, snapshot.config.preset)
        assertEquals(45, snapshot.config.customIntervalMinutes)
        assertEquals(2.5, snapshot.config.lowThreshold, 0.0)
        assertEquals(6.75, snapshot.config.dropRateThreshold, 0.0)
        assertEquals(ZoneId.of("Asia/Shanghai"), snapshot.config.zone)
        assertEquals(LocalTime.of(6, 15) to LocalTime.of(23, 45), snapshot.config.awakeWindow)
        assertEquals(BINDING.key, snapshot.monitor.cachedBindingKey)
        assertEquals(PowerReading(detail(balance), updatedAt), snapshot.monitor.reading)
        assertEquals(updatedAt, snapshot.monitor.lastSampleAt)
        assertEquals(updatedAt, snapshot.monitor.lastAttemptAt)
        assertEquals("Every exact timestamp/balance pair must survive; nonempty alone is insufficient",
            samples.sortedBy { it.time }, snapshot.monitor.samples.sortedBy { it.time })
        assertEquals(BASE + 31 * DAY, snapshot.monitor.lowWarnAt)
        assertEquals(BASE + 32 * DAY, snapshot.monitor.dropWarnAt)
    }

    private fun readReceipt(context: Context, runId: String): JsonObject {
        val file = receiptFile(context)
        assertTrue("Old seed receipt is missing: install must preserve data; do not silently re-seed", file.isFile)
        return JsonParser.parseString(file.readText()).asJsonObject.also {
            assertEquals("A different test run cannot supply the seed", runId, it.get("runId").asString)
            assertEquals(seededSamples().size, it.get("seededSampleCount").asInt)
        }
    }

    private fun assertUpgrade(context: Context, receipt: JsonObject): JsonObject {
        val old = receipt.getAsJsonObject("oldInstallation")
        val current = installation(context)
        assertEquals("Package identity must be unchanged", old.get("packageName"), current.get("packageName"))
        assertTrue("An actual higher-version APK is required",
            current.get("versionCode").asLong > old.get("versionCode").asLong)
        assertNotEquals("Do not run verify against the seed APK", old.get("versionName"), current.get("versionName"))
        assertNotEquals("The new APK must be a different artifact", old.get("apkSha256"), current.get("apkSha256"))
        assertEquals("The APK signer must remain identical", old.get("signerSha256"), current.get("signerSha256"))
        assertEquals("Uninstall/reinstall is not an upgrade", old.get("firstInstallTime"), current.get("firstInstallTime"))
        assertEquals("The sandbox UID must survive", old.get("uid"), current.get("uid"))
        assertTrue(current.get("lastUpdateTime").asLong >= old.get("lastUpdateTime").asLong)
        return current
    }

    private fun installation(context: Context): JsonObject {
        val info = context.packageManager.getPackageInfo(context.packageName,
            PackageManager.PackageInfoFlags.of(PackageManager.GET_SIGNING_CERTIFICATES.toLong()))
        val signers = requireNotNull(info.signingInfo).apkContentsSigners.map { sha256(it.toByteArray()) }.sorted()
        assertTrue("The installed APK must be signed", signers.isNotEmpty())
        return JsonObject().apply {
            addProperty("packageName", context.packageName)
            addProperty("versionName", info.versionName)
            addProperty("versionCode", info.longVersionCode)
            addProperty("uid", context.applicationInfo.uid)
            addProperty("firstInstallTime", info.firstInstallTime)
            addProperty("lastUpdateTime", info.lastUpdateTime)
            addProperty("signerSha256", signers.joinToString(","))
            addProperty("apkSha256", fileSha256(File(context.applicationInfo.sourceDir)))
            addProperty("emulatorFingerprint", Build.FINGERPRINT)
        }
    }

    @Suppress("DEPRECATION")
    private fun encryptedPreferences(context: Context) = EncryptedSharedPreferences.create(
        context, "dorm_power_secure_v1",
        MasterKey.Builder(context, "dorm_power_master_v1").setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    // Read-only independent disk observation; all writes above go through the actual PowerStore.
    private fun encryptedSnapshot(context: Context): String =
        requireNotNull(encryptedPreferences(context).getString("snapshot_v1", null))

    private fun encryptedBackup(context: Context): String? =
        encryptedPreferences(context).getString("snapshot_backup_v1", null)

    private fun assertEncryptedFile(context: Context): String {
        val file = File(context.applicationInfo.dataDir, "shared_prefs/dorm_power_secure_v1.xml")
        assertTrue("Real encrypted preferences must be nonempty on disk", file.isFile && file.length() > 0)
        val xml = file.readText()
        assertTrue(xml.contains("__androidx_security_crypto_encrypted_prefs_key_keyset__"))
        assertTrue(xml.contains("__androidx_security_crypto_encrypted_prefs_value_keyset__"))
        listOf(BINDING.account, BINDING.verify, ROOM, "snapshot_v1", "snapshot_backup_v1", "\"samples\"", "\"config\"").forEach {
            assertFalse("Plaintext leaked to the actual preferences file: $it", xml.contains(it))
        }
        return fileSha256(file)
    }

    private fun writeJson(file: File, value: JsonObject) {
        file.outputStream().use { stream ->
            stream.write(value.toString().toByteArray(Charsets.UTF_8))
            stream.fd.sync()
        }
    }

    private fun report(context: Context, phase: String, result: JsonObject) {
        result.addProperty("phase", phase)
        result.addProperty("expectedHostSerial", "emulator-5554")
        result.addProperty("internetPermissionDenied", true)
        writeJson(File(context.noBackupFilesDir, "power-upgradeqa-$phase.json"), result)
        val message = "POWER_UPGRADE_QA $result"
        Log.i("PowerUpgradeQA", message)
        InstrumentationRegistry.getInstrumentation().sendStatus(0, Bundle().apply { putString("stream", "$message\n") })
    }

    private fun receiptFile(context: Context) = File(context.noBackupFilesDir, "power-upgradeqa-receipt.json")
    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes).joinToString("") { "%02x".format(it.toInt() and 0xff) }
    private fun fileSha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { stream ->
            val buffer = ByteArray(8192)
            while (true) {
                val size = stream.read(buffer)
                if (size < 0) break
                digest.update(buffer, 0, size)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }

    private fun seededSamples() = listOf(
        PowerSample(BASE, 180.0), PowerSample(BASE + DAY, 179.0), PowerSample(BASE + 2 * DAY, 178.0),
        PowerSample(BASE + 31 * DAY, 144.5), PowerSample(BASE + 60 * DAY, 115.25),
        PowerSample(BASE + 61 * DAY, 114.0), PowerSample(BASE + 62 * DAY, OLD_BALANCE),
    )

    private fun detail(balance: Double) = PowerDetail(balance, 1.25,
        listOf("星期一" to 1.25, "星期二" to 1.5), 41.75, 300.0)

    private companion object {
        const val PACKAGE = "cn.edu.gdust.suiren.power.upgradeqa"
        const val ROOM = "UPGRADE-QA-SYNTHETIC-ROOM"
        val BINDING = PowerBinding("UPGRADE-QA-SYNTHETIC-ACCOUNT", 999999, "UPGRADE-QA-SYNTHETIC-VERIFY")
        const val DAY = 86_400_000L
        val BASE = Instant.parse("2026-09-01T08:00:00Z").toEpochMilli()
        val FORWARD_TIME = BASE + 100 * DAY
        val ROLLBACK_TIME = BASE + 50 * DAY
        const val OLD_BALANCE = 112.75
        const val FORWARD_BALANCE = 109.25
        const val ROLLBACK_BALANCE = 108.5
    }
}
