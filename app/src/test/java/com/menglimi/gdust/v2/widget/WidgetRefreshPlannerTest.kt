package com.menglimi.gdust.v2.widget

import com.menglimi.gdust.v2.data.CourseBlock
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetRefreshPlannerTest {
    private val monday = LocalDate.of(2026, 11, 9)
    private fun lesson(date: LocalDate = monday, start: Int = 1, end: Int = 2) =
        CourseBlock(1, date.dayOfWeek.value, start, end, "课程", "教室", "教师", date.toString())

    @Test
    fun scheduleRefreshesAtClassStartAndEnd() {
        val blocks = listOf(lesson())
        assertEquals(monday.atTime(8, 30), WidgetRefreshPlanner.nextRefreshAt(blocks, monday, monday.atTime(8, 0)))
        assertEquals(monday.atTime(10, 5), WidgetRefreshPlanner.nextRefreshAt(blocks, monday, monday.atTime(8, 30)))
        assertEquals(monday.atTime(18, 0), WidgetRefreshPlanner.nextRefreshAt(blocks, monday, monday.atTime(10, 5)))
    }

    @Test
    fun splitCourseRefreshesAtLunchAndEveningBreaks() {
        val blocks = listOf(lesson(start = 3, end = 10))
        assertEquals(monday.atTime(12, 0), WidgetRefreshPlanner.nextRefreshAt(blocks, monday, monday.atTime(11, 30)))
        assertEquals(monday.atTime(14, 40), WidgetRefreshPlanner.nextRefreshAt(blocks, monday, monday.atTime(12, 0)))
        assertEquals(monday.atTime(19, 30), WidgetRefreshPlanner.nextRefreshAt(blocks, monday, monday.atTime(18, 5)))
    }

    @Test
    fun noClassesStillRefreshesAtTomorrowPreviewAndMidnight() {
        assertEquals(monday.atTime(18, 0), WidgetRefreshPlanner.nextRefreshAt(emptyList(), monday, monday.atTime(12, 0)))
        assertEquals(monday.plusDays(1).atStartOfDay(),
            WidgetRefreshPlanner.nextRefreshAt(emptyList(), monday, monday.atTime(18, 0)))
        assertEquals(monday.plusDays(1).atStartOfDay(),
            WidgetRefreshPlanner.nextRefreshAt(emptyList(), null, monday.atTime(23, 59, 59)))
    }

    @Test
    fun anotherDateAndUnknownSectionsNeverInventClassBoundaries() {
        val blocks = listOf(lesson(monday.plusDays(1)), lesson(start = 11, end = 12), lesson(start = 4, end = 3))
        assertEquals(monday.atTime(18, 0), WidgetRefreshPlanner.nextRefreshAt(blocks, monday, monday.atTime(8, 0)))
    }

    @Test
    fun nextRefreshIsAlwaysInTheFutureAcrossEveryBell() {
        val blocks = listOf(lesson(), lesson(start = 3, end = 10))
        listOf(0 to 0, 8 to 30, 10 to 5, 12 to 0, 14 to 40, 18 to 0, 19 to 30, 21 to 5, 23 to 59).forEach { (hour, minute) ->
            val now = monday.atTime(hour, minute)
            assertTrue(WidgetRefreshPlanner.nextRefreshAt(blocks, monday, now).isAfter(now))
        }
    }
}
