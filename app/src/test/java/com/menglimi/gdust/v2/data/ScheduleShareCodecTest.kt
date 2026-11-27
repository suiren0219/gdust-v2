package com.menglimi.gdust.v2.data

import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ScheduleShareCodecTest {
    @Test fun exportUsesNexioGlobalSectionsAndPeriodRelativeTimes() {
        val calendar = CalendarInfo(week = 3, allWeek = 4, startDate = "2026-09-07", year = "2026", semester = "1")
        val blocks = listOf(
            block(week = 1, day = 1, start = 5, end = 6, name = "数学", date = "2026-09-07"),
            block(week = 3, day = 1, start = 5, end = 6, name = "数学", date = "2026-09-21"),
        )
        val root = JsonParser.parseString(ScheduleShareCodec.export(calendar, blocks)).asJsonObject
        val course = root.getAsJsonArray("courses").single().asJsonObject
        assertEquals(5, course.get("startSection").asInt)
        assertEquals(6, course.get("endSection").asInt)
        assertEquals(listOf(1, 3), course.getAsJsonArray("selectedWeeks").map { it.asInt })
        val time = root.getAsJsonObject("times").getAsJsonObject("afternoon")
        assertEquals("14:40-15:25", time.get("1").asString)
        assertEquals("15:30-16:15", time.get("2").asString)
    }

    @Test fun exportRejectsDateWeekMismatchAndCloudSingleDayExceptions() {
        val calendar = CalendarInfo(week = 3, allWeek = 4, startDate = "2026-09-07", year = "2026", semester = "1")
        assertThrows(IllegalArgumentException::class.java) {
            ScheduleShareCodec.export(calendar, listOf(block(week = 3, day = 1, start = 1, end = 1, date = "2026-09-14")))
        }
        val cloudException = ScheduleShareCodec.export(calendar, listOf(
            block(week = 1, day = 1, start = 1, end = 1, date = "2026-09-07"),
        ))
        assertThrows(IllegalArgumentException::class.java) {
            ScheduleShareCodec.inspect(cloudException.replace("\"routines\":[]", "\"cloud_extensions\":{},\"routines\":[]"))
        }
    }

    @Test fun inspectRejectsEmptyOutOfRangeAndCrossPeriodCourse() {
        val valid = ScheduleShareCodec.export(
            CalendarInfo(1, 4, "2026-09-07", "2026", "1"), listOf(block(1, 1, 1, 1, date = "2026-09-07")))
        val root = JsonParser.parseString(valid).asJsonObject
        assertEquals(1, ScheduleShareCodec.inspect(valid).courseCount)
        val empty = root.deepCopy().apply { add("courses", com.google.gson.JsonArray()) }
        assertThrows(IllegalArgumentException::class.java) { ScheduleShareCodec.inspect(empty.toString()) }
        val cross = root.deepCopy().apply {
            val course = getAsJsonArray("courses")[0].asJsonObject
            course.addProperty("startSection", 4); course.addProperty("endSection", 5)
            course.addProperty("isCustomTime", true)
        }
        assertThrows(IllegalArgumentException::class.java) { ScheduleShareCodec.inspect(cross.toString()) }
    }

    @Test fun rejectsMoreThanSupportedAcademicWeeks() {
        assertThrows(IllegalArgumentException::class.java) {
            ScheduleShareCodec.export(CalendarInfo(1, 61, "2026-09-07", "2026", "1"), listOf(block(1, 1, 1, 1)))
        }
    }

    private fun block(
        week: Int, day: Int, start: Int, end: Int, name: String = "示例课程", date: String = "",
    ) = CourseBlock(week, day, start, end, name, "示例教室", "示例教师", date)
}
