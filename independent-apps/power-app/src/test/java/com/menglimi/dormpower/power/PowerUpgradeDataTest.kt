package com.menglimi.dormpower.power

import com.google.gson.JsonParser
import com.menglimi.power.PowerBinding
import com.menglimi.power.PowerDetail
import com.menglimi.power.PowerMonitorConfig
import com.menglimi.power.PowerMonitorState
import com.menglimi.power.PowerReading
import com.menglimi.power.PowerSample
import org.junit.Assert.*
import org.junit.Test

class PowerUpgradeDataTest {
    private val binding = PowerBinding("synthetic-upgrade", 1, "room")
    private val now = 1_795_146_000_000L
    private val ledger = PowerLedgerState("0.72", listOf(PowerLedgerEntry("receipt-1", now - 60_000L,
        kwh = 50.0, unitPrice = "0.72", paidYuan = "35.00", payer = "合成室友", note = "升级前充值")),
        listOf(PowerDayCost("2026-10-01", 2.5, "1.80", 1, now - 120_000L, now - 60_000L)))
    private val old = PowerSample(now - 62 * 86_400_000L, 150.0)
    private val recent = PowerSample(now - 60_000L, 42.0)

    private fun snapshot() = PowerSnapshot(config = PowerMonitorConfig(binding), roomName = "合成宿舍",
        monitor = PowerMonitorState(binding.key, PowerReading(PowerDetail(42.0, null, emptyList(), null, null), recent.time),
            recent.time, recent.time, listOf(old, recent)), bindingRevision = 9L, ledger = ledger)

    private class Memory(var primary: String?, var backup: String? = null) : PowerPersistence {
        var writes = 0
        override fun read() = primary
        override fun readBackup() = backup
        override fun write(value: String) { primary = value; writes++ }
    }

    @Test fun originalLedgerVersionOneFieldsDecodeWithoutBeingRewrittenAsEmpty() {
        val legacy = """{
            "version":1,"bindingRevision":9,"roomName":"合成宿舍",
            "config":{"binding":{"account":"synthetic-upgrade","code":1,"verify":"room"},
                "preset":"manual","interval":60,"zone":"Asia/Shanghai"},
            "monitor":{"cachedBindingKey":"${binding.key}","samples":[{"time":1000,"odd":50.0}]},
            "ledger":{"unitPrice":"0.72","entries":[{"id":"legacy-receipt","time":1000,"fromTime":900,
                "kwh":50.0,"price":"0.72","paid":"35.00","payer":"合成室友","note":"旧版充值备注",
                "detected":false,"ignored":false}],"days":[{"date":"2026-10-01","kwh":2.5,
                "yuan":"1.80","intervals":1,"firstAt":900,"lastAt":1000}]}
        }""".trimIndent()
        val state = PowerPersistenceCodec.decode(legacy)
        assertEquals("0.72", state.ledger!!.unitPrice)
        assertEquals("35.00", state.ledger!!.entries.single().paidYuan)
        assertEquals("合成室友", state.ledger!!.entries.single().payer)
        assertEquals("旧版充值备注", state.ledger!!.entries.single().note)
        assertEquals("1.80", state.ledger!!.days.single().yuan)
        val disk = Memory(legacy)
        PowerStorage(disk).configure { it.copy(lowThreshold = 12.0) }
        assertEquals(state.ledger, PowerStorage(disk).snapshot().ledger)
        assertEquals(listOf(PowerSample(1000, 50.0)), PowerStorage(disk).state().samples)
    }

    @Test fun ledgerFromLegacyVersionOneSnapshotSurvivesRefreshWritesAndSettingChanges() {
        val original = snapshot()
        val encoded = PowerPersistenceCodec.encode(original)
        val json = JsonParser.parseString(encoded).asJsonObject
        assertEquals("合成室友", json.getAsJsonObject("ledger").getAsJsonArray("entries")[0].asJsonObject["payer"].asString)
        val disk = Memory(encoded)
        val store = PowerStorage(disk)
        store.update(binding) { it.copy(lastAttemptAt = now) }
        store.update(binding) { it.copy(lastSampleAt = now) }
        store.configure { it.copy(lowThreshold = 12.0) }
        val reopened = PowerStorage(disk).snapshot()
        assertEquals(ledger, reopened.ledger)
        assertEquals(original.monitor.samples, reopened.monitor.samples)
        assertEquals(12.0, reopened.config.lowThreshold, 0.0)
    }

    @Test fun backupCanRestoreMissingLedgerAndHistoryWithoutReplacingPrimarySettingsOrReadings() {
        val original = snapshot()
        val primary = original.copy(ledger = null, monitor = original.monitor.copy(samples = listOf(recent)),
            config = original.config.copy(lowThreshold = 12.0))
        val disk = Memory(PowerPersistenceCodec.encode(primary), PowerPersistenceCodec.encode(original))
        val store = PowerStorage(disk)
        val recovered = store.snapshot()
        assertEquals(ledger, recovered.ledger)
        assertEquals(original.monitor.samples, recovered.monitor.samples)
        assertEquals(primary.monitor.reading, recovered.monitor.reading)
        assertEquals(12.0, recovered.config.lowThreshold, 0.0)
        assertNotNull(recovered.recoveryMessage)
        assertEquals(0, disk.writes)
        store.configure { it.copy(dropRateThreshold = 3.0) }
        assertEquals(ledger, PowerStorage(disk).snapshot().ledger)
        assertEquals(original.monitor.samples, PowerStorage(disk).state().samples)
    }

    @Test fun backupDoesNotReviveDeletedEntriesOrDataFromAnotherBindingRevision() {
        val original = snapshot()
        val cleared = original.copy(ledger = PowerLedgerState("0.8"))
        assertEquals(cleared, recoverMissingHistory(cleared, original))
        val changed = original.copy(ledger = null, bindingRevision = original.bindingRevision + 1,
            monitor = PowerMonitorState())
        assertEquals(changed, recoverMissingHistory(changed, original))
        val other = original.copy(config = original.config.copy(binding = PowerBinding("other", 1, "other")))
        assertEquals(changed, recoverMissingHistory(changed, other))
    }

    @Test fun unreadableLedgerRejectsSnapshotRatherThanErasingItAndFallsBackToBackup() {
        val original = snapshot()
        val valid = PowerPersistenceCodec.encode(original)
        val json = JsonParser.parseString(valid).asJsonObject
        json.getAsJsonObject("ledger").addProperty("entries", "broken")
        assertThrows(IllegalArgumentException::class.java) { PowerPersistenceCodec.decode(json.toString()) }
        val disk = Memory(json.toString(), valid)
        val restored = PowerStorage(disk).snapshot()
        assertEquals(ledger, restored.ledger)
        assertEquals(original.monitor.samples, restored.monitor.samples)
        assertNotNull(restored.recoveryMessage)
        assertEquals(0, disk.writes)
    }

    @Test fun malformedHistoryAndLedgerFieldsFallBackToSameBindingBackup() {
        val original = snapshot()
        val valid = PowerPersistenceCodec.encode(original)
        val json = JsonParser.parseString(valid).asJsonObject
        json.getAsJsonObject("monitor").addProperty("samples", "broken")
        json.getAsJsonObject("ledger").getAsJsonArray("entries")[0].asJsonObject.addProperty("payer", false)
        val disk = Memory(json.toString(), valid)
        val restored = PowerStorage(disk).snapshot()
        assertEquals(ledger, restored.ledger)
        assertEquals(original.monitor.samples, restored.monitor.samples)
        assertNotNull(restored.recoveryMessage)
        assertEquals(0, disk.writes)
    }

    @Test fun rebindSameRoomRetainsLedgerAndChangingRoomCannotCarryItAcross() {
        val disk = Memory(PowerPersistenceCodec.encode(snapshot()))
        val store = PowerStorage(disk)
        store.bind(binding, "合成宿舍", 9L)
        assertEquals(ledger, store.snapshot().ledger)
        store.bind(PowerBinding("other", 1, "other-room"), "另一宿舍", 10L)
        assertTrue(store.snapshot().ledger!!.entries.isEmpty())
        assertTrue(store.snapshot().ledger!!.days.isEmpty())
        assertTrue(store.state().samples.isEmpty())
    }
}
