// Regression tests for the GDUST date/time adaptation of Nexio's schedule grid.
package com.menglimi.gdust.v2.ui.schedule

import com.menglimi.gdust.v2.data.CourseBlock
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScheduleTimesTest {
    private val termStart = LocalDate.of(2026, 9, 7)

    private fun lesson(
        date: String = "2026-09-07",
        week: Int = 1,
        day: Int = 1,
        start: Int = 1,
        end: Int = 2,
    ) = CourseBlock(week, day, start, end, "高等数学", "A101", "张老师", date)

    @Test
    fun actualDateOverridesWeekAndDayForAllStatuses() {
        val block = lesson(date = "2026-09-09", week = 9, day = 1)
        assertEquals(LocalDate.of(2026, 9, 9), ScheduleTimes.lessonDate(block, termStart))
        assertTrue(ScheduleTimes.isOngoing(block, termStart, LocalDateTime.of(2026, 9, 9, 8, 30)))
        assertFalse(ScheduleTimes.isOngoing(block, termStart, LocalDateTime.of(2026, 11, 2, 8, 30)))
        assertFalse(ScheduleTimes.isPast(block, termStart, LocalDateTime.of(2026, 9, 8, 22, 0)))
        assertTrue(ScheduleTimes.isPast(block, termStart, LocalDateTime.of(2026, 9, 10, 0, 0)))
    }

    @Test
    fun actualDateStillWorksWithoutCalendar() {
        val block = lesson(date = "2026-09-09T00:00:00+08:00")
        assertEquals(LocalDate.of(2026, 9, 9), ScheduleTimes.lessonDate(block, null))
        assertTrue(ScheduleTimes.isOngoing(block, null, LocalDateTime.of(2026, 9, 9, 9, 0)))
    }

    @Test
    fun blankDateFallsBackOnlyWhenCalendarPositionIsValid() {
        assertEquals(LocalDate.of(2026, 9, 20), ScheduleTimes.lessonDate(lesson(" ", week = 2, day = 7), termStart))
        assertNull(ScheduleTimes.lessonDate(lesson(""), null))
        assertNull(ScheduleTimes.dateAt(termStart, 0, 1))
        assertNull(ScheduleTimes.dateAt(termStart, 1, 0))
        assertNull(ScheduleTimes.dateAt(termStart, 1, 8))
    }

    @Test
    fun invalidExplicitDateDoesNotInventCalendarFallback() {
        val block = lesson(date = "2026-02-30")
        assertNull(ScheduleTimes.lessonDate(block, termStart))
        assertFalse(ScheduleTimes.isOngoing(block, termStart, LocalDateTime.of(2026, 9, 7, 9, 0)))
        assertFalse(ScheduleTimes.isPast(block, termStart, LocalDateTime.of(2026, 9, 8, 9, 0)))
    }

    @Test
    fun isoAndPortalSuffixesPreserveStatedLocalDate() {
        val expected = LocalDate.of(2026, 9, 7)
        listOf(
            "2026-09-07",
            " 2026-09-07T08:30:00 ",
            "2026-09-07T00:00:00+08:00",
            "2026-09-07T23:59:59.123-05:00",
            "2026-09-07T08:30:00Z",
            "2026-09-07 08:30:00",
        ).forEach { value -> assertEquals(value, expected, ScheduleTimes.parseDate(value)) }
    }

    @Test
    fun malformedDatesAndSuffixesAreUnknown() {
        listOf(null, "", " ", "invalid", "2026-02-30", "2026-09-07garbage", "2026-09-07T99:99:99")
            .forEach { value -> assertNull(value, ScheduleTimes.parseDate(value)) }
    }

    @Test
    fun termWeeksUseFloorDivisionBeforeStartAndAdvanceOnMonday() {
        assertEquals(-1, ScheduleTimes.weekAt(termStart, termStart.minusDays(8)))
        assertEquals(0, ScheduleTimes.weekAt(termStart, termStart.minusDays(7)))
        assertEquals(0, ScheduleTimes.weekAt(termStart, termStart.minusDays(1)))
        assertEquals(1, ScheduleTimes.weekAt(termStart, termStart))
        assertEquals(1, ScheduleTimes.weekAt(termStart, termStart.plusDays(6)))
        assertEquals(2, ScheduleTimes.weekAt(termStart, termStart.plusDays(7)))
        assertEquals(16, ScheduleTimes.weekAt(termStart, termStart.plusDays(16 * 7L - 1)))
        assertEquals(17, ScheduleTimes.weekAt(termStart, termStart.plusWeeks(16)))
    }

    @Test
    fun termDatesRemainCorrectAcrossCalendarYear() {
        val start = LocalDate.of(2026, 12, 28)
        assertEquals(LocalDate.of(2027, 1, 3), ScheduleTimes.dateAt(start, 1, 7))
        assertEquals(LocalDate.of(2027, 1, 4), ScheduleTimes.dateAt(start, 2, 1))
        assertEquals(2, ScheduleTimes.weekAt(start, LocalDate.of(2027, 1, 4)))
    }

    @Test
    fun classStartsInclusivelyAndEndsExclusively() {
        val block = lesson()
        val start = LocalDateTime.of(2026, 9, 7, 8, 30)
        val end = LocalDateTime.of(2026, 9, 7, 10, 5)
        assertFalse(ScheduleTimes.isOngoing(block, termStart, start.minusNanos(1)))
        assertTrue(ScheduleTimes.isOngoing(block, termStart, start))
        assertTrue(ScheduleTimes.isOngoing(block, termStart, end.minusNanos(1)))
        assertFalse(ScheduleTimes.isOngoing(block, termStart, end))
        assertFalse(ScheduleTimes.isPast(block, termStart, end.minusNanos(1)))
        assertTrue(ScheduleTimes.isPast(block, termStart, end))
    }

    @Test
    fun sectionAndRedLineStopAtBellAndDuringBreaks() {
        assertNull(ScheduleTimes.currentSection(LocalTime.of(8, 29, 59)))
        assertEquals(1, ScheduleTimes.currentSection(LocalTime.of(8, 30)))
        assertNull(ScheduleTimes.currentSection(LocalTime.of(9, 15)))
        assertEquals(2, ScheduleTimes.currentSection(LocalTime.of(9, 20)))
        assertNull(ScheduleTimes.currentSection(LocalTime.of(12, 0)))
        assertNull(ScheduleTimes.currentSection(LocalTime.of(14, 39)))
        assertNull(ScheduleTimes.currentSection(LocalTime.of(21, 5)))
        assertEquals(0f, ScheduleTimes.sectionProgress(1, LocalTime.of(8, 30))!!, 0.0001f)
        assertEquals(0.5f, ScheduleTimes.sectionProgress(1, LocalTime.of(8, 52, 30))!!, 0.0001f)
        assertNull(ScheduleTimes.sectionProgress(1, LocalTime.of(9, 15)))
    }

    @Test
    fun unknownSectionsNeverInventClockTimes() {
        for (section in listOf(0, 11, 12, 99)) {
            assertEquals("", ScheduleTimes.start(section))
            assertEquals("", ScheduleTimes.end(section))
            assertNull(ScheduleTimes.sectionProgress(section, LocalTime.of(21, 0)))
        }
        val unknown = lesson(start = 11, end = 12)
        assertFalse(ScheduleTimes.isOngoing(unknown, termStart, LocalDateTime.of(2026, 9, 7, 21, 0)))
        assertFalse(ScheduleTimes.isPast(unknown, termStart, LocalDateTime.of(2026, 9, 7, 23, 59)))
        assertTrue(ScheduleTimes.isPast(unknown, termStart, LocalDateTime.of(2026, 9, 8, 0, 0)))
        assertFalse(ScheduleTimes.isOngoing(lesson(start = 9, end = 11), termStart, LocalDateTime.of(2026, 9, 7, 20, 0)))
    }

    @Test
    fun crossingLunchOrDinnerUsesOnlyActualSegments() {
        assertEquals(listOf(1..2), ScheduleTimes.segments(1, 2))
        assertEquals(listOf(3..4, 5..6), ScheduleTimes.segments(3, 6))
        assertEquals(listOf(4..4, 5..8, 9..10), ScheduleTimes.segments(4, 10))
        assertEquals(listOf(8..8, 9..12), ScheduleTimes.segments(8, 12))
        assertEquals(listOf(11..12), ScheduleTimes.segments(11, 12))
        assertTrue(ScheduleTimes.segments(0, 2).isEmpty())
        assertTrue(ScheduleTimes.segments(4, 3).isEmpty())
        val block = lesson(start = 3, end = 10)
        assertTrue(ScheduleTimes.isOngoing(block, termStart, LocalDateTime.of(2026, 9, 7, 11, 30)))
        assertFalse(ScheduleTimes.isOngoing(block, termStart, LocalDateTime.of(2026, 9, 7, 13, 0)))
        assertTrue(ScheduleTimes.isOngoing(block, termStart, LocalDateTime.of(2026, 9, 7, 14, 40)))
        assertFalse(ScheduleTimes.isOngoing(block, termStart, LocalDateTime.of(2026, 9, 7, 18, 30)))
        assertTrue(ScheduleTimes.isOngoing(block, termStart, LocalDateTime.of(2026, 9, 7, 19, 30)))
        assertFalse(ScheduleTimes.isPast(block, termStart, LocalDateTime.of(2026, 9, 7, 13, 0)))
    }
}
