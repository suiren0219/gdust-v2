package com.menglimi.gdust.v2.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalTime

class MaintenanceWindowTest {
    private fun at(h: Int, m: Int = 0) = LocalTime.of(h, m)

    @Test
    fun withinSameDayWindow() {
        assertTrue(MaintenanceWindow.isWithin(at(23, 30), 23 * 60, 6 * 60 + 30))
        assertFalse(MaintenanceWindow.isWithin(at(12, 0), 23 * 60, 6 * 60 + 30))
    }

    @Test
    fun acrossMidnightWindow() {
        val start = 23 * 60
        val end = 6 * 60 + 30
        assertTrue(MaintenanceWindow.isWithin(at(0, 0), start, end))
        assertTrue(MaintenanceWindow.isWithin(at(5, 59), start, end))
        assertTrue(MaintenanceWindow.isWithin(at(6, 29), start, end))
        assertFalse(MaintenanceWindow.isWithin(at(6, 30), start, end))
        assertTrue(MaintenanceWindow.isWithin(at(23, 0), start, end))
        assertFalse(MaintenanceWindow.isWithin(at(22, 59), start, end))
    }

    @Test
    fun equalOrInvalidBoundsNeverMatch() {
        assertFalse(MaintenanceWindow.isWithin(at(12), 600, 600))
        assertFalse(MaintenanceWindow.isWithin(at(12), -1, 600))
        assertFalse(MaintenanceWindow.isWithin(at(12), 600, 1440))
    }

    @Test
    fun noticeOnlyDuringWindow() {
        val start = 23 * 60
        val end = 6 * 60 + 30
        val text = MaintenanceWindow.notice(at(23, 5), start, end, "连接超时")
        assertTrue(text!!.contains("夜间关闭"))
        assertTrue(text.contains("23:00–06:30"))
        assertTrue(text.contains("具体错误：连接超时"))
        assertNull(MaintenanceWindow.notice(at(12, 0), start, end, "连接超时"))
        assertNull(MaintenanceWindow.notice(at(23, 5), start, end, null)!!.let { it.takeIf { t -> t.contains("具体错误") } })
        assertEquals(
            MaintenanceWindow.notice(at(23, 5), start, end, null),
            "教务系统夜间关闭（约 23:00–06:30）：学校教务此时段停止服务，" +
                "课表与成绩暂时无法同步。学习通与电量查询不受影响，请白天再试。",
        )
    }

    @Test
    fun parseAndFormatRoundTrip() {
        assertEquals(23 * 60, MaintenanceWindow.parse("23:00"))
        assertEquals(6 * 60 + 30, MaintenanceWindow.parse("06:30"))
        assertEquals(0, MaintenanceWindow.parse("0:00"))
        assertNull(MaintenanceWindow.parse("24:00"))
        assertNull(MaintenanceWindow.parse("7:5"))
        assertNull(MaintenanceWindow.parse("abc"))
        assertEquals("06:30", MaintenanceWindow.format(6 * 60 + 30))
        assertEquals("00:00", MaintenanceWindow.format(0))
    }
}
