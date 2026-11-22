package com.menglimi.gdust.v2.widget

import com.menglimi.gdust.v2.data.CourseBlock
import java.time.LocalDate
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WidgetScheduleSelectionTest {
    private val monday = LocalDate.of(2026, 11, 9)
    private fun lesson(date: LocalDate, start: Int = 1, end: Int = 2, room: String = "A101") =
        CourseBlock(1, date.dayOfWeek.value, start, end, "高等数学", room, "教师", date.toString())

    @Test
    fun endedClassesAreRemovedAtTheBellAndTomorrowPreviewBegins() {
        val morning = lesson(monday)
        val afternoon = lesson(monday, 5, 6)
        val tomorrow = lesson(monday.plusDays(1))
        val blocks = listOf(morning, afternoon, tomorrow)
        val atBell = WidgetDataSource.selectSchedule(blocks, monday, true, monday.atTime(10, 5))
        assertEquals(WidgetData.Scope.TODAY_REST, atBell.scope)
        assertEquals(listOf(afternoon), atBell.lessons)
        val afterDay = WidgetDataSource.selectSchedule(blocks, monday, true, monday.atTime(16, 15))
        assertEquals(WidgetData.Scope.TOMORROW, afterDay.scope)
        assertEquals(monday.plusDays(1), afterDay.date)
        assertEquals(listOf(tomorrow), afterDay.lessons)
    }

    @Test
    fun midnightSwitchesTheSameLessonFromTomorrowToToday() {
        val next = lesson(monday.plusDays(1))
        val evening = WidgetDataSource.selectSchedule(listOf(next), monday, true, monday.atTime(22, 0))
        val midnight = WidgetDataSource.selectSchedule(listOf(next), monday, true, monday.plusDays(1).atStartOfDay())
        assertEquals(WidgetData.Scope.TOMORROW, evening.scope)
        assertEquals(WidgetData.Scope.TODAY_ALL, midnight.scope)
        assertEquals(evening.lessons, midnight.lessons)
        assertEquals(evening.date, midnight.date)
    }

    @Test
    fun emptyTermIsDifferentFromMissingImportAndPartialWeekSnapshot() {
        assertEquals(WidgetData.Scope.NO_DATA,
            WidgetDataSource.selectSchedule(emptyList(), monday, false, monday.atTime(12, 0)).scope)
        assertEquals(WidgetData.Scope.NO_LESSONS,
            WidgetDataSource.selectSchedule(emptyList(), monday, true, monday.atTime(12, 0)).scope)
        assertEquals(WidgetData.Scope.TOMORROW_EMPTY,
            WidgetDataSource.selectSchedule(emptyList(), monday, true, monday.atTime(22, 0)).scope)
        assertEquals(WidgetData.Scope.INCOMPLETE,
            WidgetDataSource.selectSchedule(listOf(lesson(monday)), monday, false, monday.atTime(22, 0)).scope)
    }

    @Test
    fun differentRoomsArePreservedAndWeekAdvancesOnMonday() {
        val sunday = monday.plusDays(6)
        val nextMonday = monday.plusWeeks(1)
        val blocks = listOf(lesson(nextMonday), lesson(nextMonday, room = "B202"))
        val selection = WidgetDataSource.selectSchedule(blocks, monday, true, sunday.atTime(22, 0))
        assertEquals(2, selection.week)
        assertEquals(2, selection.lessons.size)
        assertEquals(listOf("A101", "B202"), selection.lessons.map { it.room })
    }

    @Test
    fun beforeTermAndMissingCalendarDoNotInventWeekOne() {
        val now = LocalDateTime.of(2026, 11, 8, 8, 0)
        assertNull(WidgetDataSource.selectSchedule(emptyList(), monday, true, now).week)
        assertNull(WidgetDataSource.selectSchedule(listOf(lesson(monday)), null, true, now).week)
    }
}
