package com.menglimi.dormpower.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PowerWidgetFormatTest {
    private val now = 1_794_804_000_000L

    @Test fun balanceNeverTurnsMissingOrNonFiniteDataIntoZero() {
        assertEquals("—", widgetBalanceText(null))
        assertEquals("—", widgetBalanceText(Double.NaN))
        assertEquals("—", widgetBalanceText(Double.POSITIVE_INFINITY))
        assertEquals("0.00", widgetBalanceText(0.0))
        assertEquals("-1.20", widgetBalanceText(-1.2))
        assertEquals("42.60", widgetBalanceText(42.6))
    }

    @Test fun ageTextIsHonestAboutUnknownFutureAndOldReadings() {
        assertEquals("暂无更新", widgetAgeText(0, now))
        assertEquals("时间待确认", widgetAgeText(now + 1_000, now))
        assertEquals("刚刚更新", widgetAgeText(now - 30_000, now))
        assertEquals("5 分钟前更新", widgetAgeText(now - 5 * 60_000, now))
        assertEquals("3 小时前更新", widgetAgeText(now - 3 * 3_600_000, now))
        assertEquals("2 天前更新", widgetAgeText(now - 2 * 86_400_000, now))
    }

    @Test fun statusSeparatesDebtZeroLowAndStaleInsteadOfUsingStaleAsHealthy() {
        assertEquals("暂无读数", widgetStatusText(null, 20.0, stale = false))
        assertEquals("负余额", widgetStatusText(-1.0, 20.0, stale = true))
        assertEquals("电量为零", widgetStatusText(0.0, 20.0, stale = true))
        assertEquals("待更新", widgetStatusText(42.0, 20.0, stale = true))
        assertEquals("低电量", widgetStatusText(8.0, 20.0, stale = false))
        assertEquals("电量正常", widgetStatusText(42.0, 20.0, stale = false))
    }

    @Test fun stalenessFollowsQueryIntervalAndFallsBackToOneDayInManualMode() {
        assertTrue(widgetStale(0, now, intervalMinutes = 120))
        assertTrue(widgetStale(now + 1_000, now, intervalMinutes = 120))
        assertTrue(widgetStale(now - 130 * 60_000, now, intervalMinutes = 120))
        assertFalse(widgetStale(now - 110 * 60_000, now, intervalMinutes = 120))
        // 手动模式没有固定间隔：24 小时内不标待更新。
        assertFalse(widgetStale(now - 23 * 3_600_000, now, intervalMinutes = null))
        assertTrue(widgetStale(now - 25 * 3_600_000, now, intervalMinutes = null))
    }

    @Test fun usageTextDoesNotShowNegativeOrInvalidServerStatistics() {
        assertEquals("—", widgetUsageText(null))
        assertEquals("—", widgetUsageText(-1.0))
        assertEquals("—", widgetUsageText(Double.NaN))
        assertEquals("2.30", widgetUsageText(2.3))
        assertEquals("0.00", widgetUsageText(0.0))
    }
}
