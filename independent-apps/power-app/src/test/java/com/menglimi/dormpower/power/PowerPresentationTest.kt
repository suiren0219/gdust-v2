package com.menglimi.dormpower.power

import com.menglimi.power.PowerBinding
import com.menglimi.power.PowerDetail
import com.menglimi.power.PowerMonitorConfig
import com.menglimi.power.PowerQueryPreset
import com.menglimi.power.PowerReading
import com.menglimi.power.PowerSample
import org.junit.Assert.*
import org.junit.Test

class PowerPresentationTest {
    private val now = 1_794_804_000_000L
    private val binding = PowerBinding("synthetic-account", 1, "synthetic-room")
    private fun state(odd: Double? = 42.0, age: Long = 600_000) = PowerUiState(
        config = PowerMonitorConfig(binding = binding),
        reading = PowerReading(PowerDetail(odd, null, emptyList(), null, null), now - age),
        lastSampleAt = now - age, lastAttemptAt = now - age,
    )

    @Test fun missingAndNonFiniteReadingsNeverBecomeZero() {
        assertEquals("—", powerValue(null))
        assertEquals("—", powerValue(Double.NaN))
        assertEquals("—", powerValue(Double.POSITIVE_INFINITY))
        assertEquals("0.00", powerValue(0.0))
        assertEquals("-1.00", powerValue(-1.0))
        assertEquals(ReadingStatus.UNKNOWN, readingStatus(state(null), now))
        assertEquals(ReadingStatus.LOW, readingStatus(state(0.0), now))
        assertEquals(ReadingStatus.LOW, readingStatus(state(-1.0), now))
    }

    @Test fun expiredOrFailedCachedReadingIsNeverLabelledHealthy() {
        assertEquals(ReadingStatus.READY, readingStatus(state(), now))
        // 默认已是手动（24 小时有效），按查询节奏过期要用显式均衡配置验证。
        val balanced = state(age = 120 * 60_000).copy(config = PowerMonitorConfig(binding, PowerQueryPreset.BALANCED))
        assertEquals(ReadingStatus.STALE, readingStatus(balanced, now))
        assertEquals(ReadingStatus.STALE, readingStatus(state(age = -60_000), now))
        assertEquals(ReadingStatus.ERROR, readingStatus(state().copy(error = "synthetic failure"), now))
        val pendingFailure = state().copy(lastAttemptAt = now - 180_000)
        assertEquals(ReadingStatus.STALE, readingStatus(pendingFailure, now))
        assertEquals(ReadingStatus.UNBOUND, readingStatus(state().copy(config = PowerMonitorConfig()), now))
    }

    @Test fun manualCacheExpiresAfterOneDayAndCustomIntervalIsRespected() {
        val manual = state(age = 23 * 3600_000L).copy(config = PowerMonitorConfig(binding, PowerQueryPreset.MANUAL))
        assertEquals(ReadingStatus.READY, readingStatus(manual, now))
        assertEquals(ReadingStatus.STALE, readingStatus(manual, now + 3600_000L))
        val custom = state(age = 45 * 60_000L).copy(config = PowerMonitorConfig(binding, PowerQueryPreset.CUSTOM, 45))
        assertEquals(ReadingStatus.STALE, readingStatus(custom, now))
    }

    @Test fun trendRejectsFutureInvalidAndOutsideRangeSamplesButPreservesZeroAndDebt() {
        val points = trendSamples(listOf(PowerSample(0, 1.0), PowerSample(now + 1, 2.0),
            PowerSample(now - 2 * 86_400_000, 3.0), PowerSample(now - 3_000, Double.NaN),
            PowerSample(now - 2_000, 0.0), PowerSample(now - 1_000, -0.5)), 1, now)
        assertEquals(listOf(0.0, -0.5), points.map { it.odd })
        assertEquals(TrendAxis(-0.5, 0.0), trendAxis(points))
    }

    @Test fun singlePointIsNotDayUsageAndRechargeIsExplicitlyBalanceChange() {
        assertEquals("—（仅一次采样）", balanceChange(listOf(PowerSample(now, 10.0))))
        val change = balanceChange(listOf(PowerSample(now - 60_000, 10.0), PowerSample(now, 30.0)))
        assertTrue(change.startsWith("+20.00"))
        assertTrue(change.contains("充值"))
        assertFalse(change.contains("耗电"))
        val axis = trendAxis(listOf(PowerSample(now - 1_000, 0.0), PowerSample(now, 0.0)))
        assertEquals(-0.5, axis.min, 0.0)
        assertEquals(0.5, axis.max, 0.0)
        assertEquals(3, axis.ticks.size)
    }

    @Test fun onlyRealLowBalanceTurnsHeroRedRegardlessOfFreshness() {
        for (age in listOf(600_000L, 3 * 3600_000L)) {
            for (error in listOf(null, "synthetic failure")) {
                val healthy = readingPresentation(state(40.9, age).copy(error = error), now)
                assertFalse(healthy.warning)
                assertFalse(healthy.tags.contains("低电量"))
                val low = readingPresentation(state(8.0, age).copy(error = error), now)
                assertTrue(low.warning)
                assertTrue(low.tags.contains("低电量"))
            }
        }
        assertEquals("电量为零", readingPresentation(state(0.0), now).tags.first())
        assertEquals("负余额", readingPresentation(state(-1.0), now).tags.first())
        assertFalse(readingPresentation(state(null).copy(error = "failed"), now).warning)
        assertTrue(readingPresentation(state(40.9).copy(busy = true), now).tags.contains("更新中"))
        assertFalse(readingPresentation(state(40.9, age = 0), now).cached)
    }

    @Test fun missingAndInvalidUsageAreNotPresentedAsZeroOrNegativeConsumption() {
        assertEquals("—", usageValue(null))
        assertEquals("—", usageValue(Double.NaN))
        assertEquals("—", usageValue(-1.0))
        assertEquals("0.00", usageValue(0.0))
        assertEquals("0.00", powerValue(-0.0))
        assertEquals("暂无法估算", estimateTitle(com.menglimi.power.PowerUsageEstimate(
            com.menglimi.power.PowerEstimateState.INSUFFICIENT)))
        assertEquals("不足 1 天", estimateTitle(com.menglimi.power.PowerUsageEstimate(
            com.menglimi.power.PowerEstimateState.AVAILABLE, dailyUse = 3.0, daysLeft = 0.2)))
    }

    @Test fun customerCodeAcceptsOnlyPositiveIntegerWithoutTruncation() {
        assertEquals(1000145, schoolCode(" 1000145 "))
        listOf("", "0", "-1", "1.5", "1e6", "9999999999999999", "学校").forEach { assertNull(schoolCode(it)) }
    }
}
