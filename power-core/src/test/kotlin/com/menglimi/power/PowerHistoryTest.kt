package com.menglimi.power

import com.google.gson.Gson
import com.google.gson.JsonParser
import java.security.MessageDigest
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PowerHistoryTest {
    private val zone = ZoneId.of("Asia/Shanghai")
    private val now = Instant.parse("2026-11-15T04:00:00Z").toEpochMilli()

    @Test fun dailyAggregationSortsSamplesAndPreservesZeroAndNegativeBalances() {
        val first = PowerSample(now, 3.0)
        val second = PowerSample(now + 60_000, 0.0)
        val last = PowerSample(now + 120_000, -2.0)
        val stat = PowerHistory.group(listOf(last, first, second), zone).values.single()
        assertEquals(LocalDate.of(2026, 11, 15), stat.date)
        assertEquals(listOf(first, second, last), stat.samples)
        assertEquals(-2.0, stat.lastOdd, 0.0)
        assertEquals(-5.0, stat.dayChange, 0.0)
    }

    @Test fun rechargeRemainsPositiveChangeAndInvalidSamplesAreExcluded() {
        val first = PowerSample(now, 1.0)
        val last = PowerSample(now + 60_000, 50.0)
        val invalid = listOf(PowerSample(0, 1.0), PowerSample(now, Double.NaN), PowerSample(now, Double.POSITIVE_INFINITY))
        val stat = PowerHistory.group(invalid + listOf(last, first), zone).values.single()
        assertEquals(49.0, stat.dayChange, 0.0)
        assertEquals(listOf(first, last), stat.samples)
        assertTrue(PowerHistory.group(invalid, zone).isEmpty())
    }

    @Test fun groupingUsesInjectedZoneInsteadOfProcessDefault() {
        val before = PowerSample(Instant.parse("2026-11-15T15:59:00Z").toEpochMilli(), 10.0)
        val after = PowerSample(Instant.parse("2026-11-15T16:01:00Z").toEpochMilli(), 9.0)
        assertEquals(2, PowerHistory.group(listOf(before, after), zone).size)
        assertEquals(1, PowerHistory.group(listOf(before, after), ZoneId.of("UTC")).size)
    }

    @Test fun legacySampleAndDetailJsonRetainTheirFieldNames() {
        val gson = Gson()
        val sample = gson.fromJson("""{"time":1234,"odd":-1.5}""", PowerSample::class.java)
        assertEquals(PowerSample(1234, -1.5), sample)
        assertEquals(setOf("time", "odd"), JsonParser.parseString(gson.toJson(sample)).asJsonObject.keySet())
        val json = """{"odd":12.5,"todayUse":1.0,"weekUse":[{"first":"一","second":2.5}],"monthUse":30.0,"sumBuy":100.0}"""
        val detail = gson.fromJson(json, PowerDetail::class.java)
        assertEquals(PowerDetail(12.5, 1.0, listOf("一" to 2.5), 30.0, 100.0), detail)
        assertEquals(JsonParser.parseString(json), JsonParser.parseString(gson.toJson(detail)))
        val room = gson.fromJson("""{"name":"101","verify":"v"}""", PowerRoom::class.java)
        assertEquals(PowerRoom("101", "v"), room)
    }

    @Test fun bindingKeyMatchesLegacyWorkerDigestIncludingEscapedValues() {
        val binding = PowerBinding("a+<&\"", 123, "v\\\n")
        val expected = MessageDigest.getInstance("SHA-256")
            .digest(Gson().toJson(listOf(binding.account, binding.code, binding.verify)).toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }
        assertEquals(expected, binding.key)
        assertTrue(binding.isValid)
        assertFalse(binding.copy(code = 0).isValid)
        assertFalse(binding.copy(account = " ").isValid)
        assertFalse(binding.copy(verify = "").isValid)
    }
}
