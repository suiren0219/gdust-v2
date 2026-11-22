package com.menglimi.dormpower.power

import com.menglimi.power.*
import java.io.IOException
import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.*
import org.junit.Test

class PowerLedgerTest {
    private val zone = ZoneId.of("Asia/Shanghai")
    private val now = Instant.parse("2026-11-19T04:00:00Z").toEpochMilli()
    private fun reading(time: Long, odd: Double) = PowerReading(PowerDetail(odd, null, emptyList(), null, null), time)

    @Test fun risesAreEstimatesAndFailuresRepeatedOrOldReadingsCannotCreateDuplicates() {
        val before = reading(now, 10.0)
        val after = reading(now + 1000, 110.0)
        val result = PowerLedger.record(PowerLedgerState(), before, after, zone)
        assertEquals(1, result.entries.size)
        assertTrue(result.entries.single().detected)
        assertNull(result.entries.single().paidYuan)
        assertEquals("60.00", PowerLedger.money(PowerLedger.amount(result.entries.single())))
        assertEquals(result, PowerLedger.record(result, before, after, zone))
        assertEquals(result, PowerLedger.record(result, after, after, zone))
        assertEquals(result, PowerLedger.record(result, after, before, zone))
        assertEquals(result, PowerLedger.record(result, before, null, zone))
    }

    @Test fun downwardIntervalsUsePricesAtTheTimeAndDoNotOffsetRechargeAgainstConsumption() {
        val first = PowerLedger.record(PowerLedgerState(), reading(now, 30.0), reading(now + 1000, 20.0), zone)
        val changed = first.copy(unitPrice = "0.8")
        val second = PowerLedger.record(changed, reading(now + 1000, 20.0), reading(now + 2000, 15.0), zone)
        assertEquals(15.0, second.days.single().kwh, 0.00001)
        assertEquals("10.00", PowerLedger.money(second.days.single().yuan.toBigDecimal()))
        val recharge = PowerLedger.record(second, reading(now + 2000, 15.0), reading(now + 3000, 115.0), zone)
        assertEquals(second.days, recharge.days)
        assertEquals("80.00", PowerLedger.money(PowerLedger.amount(recharge.entries.single())))
    }

    @Test fun moneyUsesDecimalRoundingAndInputsRejectInvalidData() {
        assertEquals("1.38", PowerLedger.yuan(2.3, "0.6"))
        assertEquals("—", PowerLedger.yuan(Double.NaN, "0.6"))
        listOf("0", "-0.6", "NaN", "1e2", "0.12345", "101").forEach { assertNull(PowerLedger.price(it)) }
        assertNull(PowerLedger.payment("1.001"))
        assertEquals("0.6000", PowerLedger.price("0.6000")?.toPlainString())
        val actual = PowerLedgerEntry(time = now, kwh = 40.0, paidYuan = "30.00", payer = "室友甲")
        assertEquals("30.00", PowerLedger.money(PowerLedger.amount(actual)))
    }

    private class Memory : PowerPersistence {
        var data: String? = null
        var fail = false
        override fun read() = data
        override fun write(value: String) { if (fail) throw IOException("synthetic"); data = value }
    }

    @Test fun ledgerPersistsAtomicallyAcrossForegroundAndWorkerUpdatesAndSameRoomRebind() {
        val memory = Memory()
        val store = PowerStorage(memory)
        val binding = PowerBinding("synthetic-account", 1, "synthetic-room")
        store.bind(binding, "测试宿舍", 0)
        store.update(binding) { it.copy(reading = reading(now, 10.0), cachedBindingKey = binding.key) }
        store.monitorStore(background = true).update(binding) { it.copy(reading = reading(now + 1000, 20.0)) }
        // 手动模式拒绝后台提交。
        assertTrue(store.snapshot().ledger.entries.isEmpty())
        store.update(binding) { it.copy(reading = reading(now + 1000, 20.0)) }
        val entry = store.snapshot().ledger.entries.single().copy(payer = "室友乙", note = "共同电费", paidYuan = "6.00")
        store.editLedger(store.snapshot().bindingRevision) { it.copy(entries = listOf(entry)) }
        val reopened = PowerStorage(memory).snapshot()
        assertEquals(store.snapshot(), reopened)
        assertEquals("共同电费", reopened.ledger.entries.single().note)
        val previous = store.snapshot().ledger
        store.bind(binding, "测试宿舍", store.snapshot().bindingRevision)
        assertEquals(previous, store.snapshot().ledger)
        memory.fail = true
        assertThrows(IOException::class.java) { store.editLedger(store.snapshot().bindingRevision) { it.copy(unitPrice = "0.8") } }
        assertEquals(previous, store.snapshot().ledger)
        memory.fail = false
        store.unbind()
        assertTrue(store.snapshot().ledger.entries.isEmpty())
    }

    @Test fun oldSnapshotsRemainReadableAndWrongRoomRevisionCannotEditLedger() {
        val memory = Memory()
        val store = PowerStorage(memory)
        store.bind(PowerBinding("synthetic", 1, "room"), "测试", 0)
        val revision = store.snapshot().bindingRevision
        store.unbind()
        assertThrows(IllegalStateException::class.java) { store.editLedger(revision) { it.copy(unitPrice = "0.8") } }
        val old = com.google.gson.JsonParser.parseString(PowerPersistenceCodec.encode(store.snapshot())).asJsonObject
        old.remove("ledger")
        assertEquals("0.6", PowerPersistenceCodec.decode(old.toString()).ledger.unitPrice)
    }
}
