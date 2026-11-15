package com.menglimi.gdust.v2.work

import com.menglimi.gdust.v2.data.CourseBlock
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ClassDndPlannerTest {
    private val zone = ZoneId.of("Asia/Shanghai")
    private val monday = LocalDate.of(2026, 11, 9)
    private fun at(hour: Int, minute: Int, date: LocalDate = monday) =
        date.atTime(hour, minute).atZone(zone).toInstant().toEpochMilli()
    private fun lesson(start: Int = 1, end: Int = 2, date: String = monday.toString(), week: Int = 1, day: Int = 1) =
        CourseBlock(week, day, start, end, "课程", "A101", "教师", date)
    private fun plan(windows: List<ClassDndWindow>, now: Long, automatic: Boolean = true,
                     manual: ClassDndWindow? = null, skippedUntil: Long = 0) =
        planClassDnd(windows, automatic, manual, skippedUntil, now)

    @Test fun classStartsAtActualStartAndExitsAtExactEnd() {
        val windows = classDndWindows(listOf(lesson()), monday, zone)
        assertEquals(listOf(ClassDndWindow(at(8, 30), at(10, 5))), windows)
        assertEquals(ClassDndPlan(null, at(8, 30)), plan(windows, at(8, 29)))
        assertEquals(windows.single(), plan(windows, at(8, 30)).active)
        assertEquals(at(10, 5), plan(windows, at(9, 15)).nextAtMillis)
        assertNull(plan(windows, at(10, 5)).active)
        assertNull(plan(windows, at(10, 5)).nextAtMillis)
    }

    @Test fun lateAlarmOrRestartReconcilesCurrentState() {
        val windows = classDndWindows(listOf(lesson()), monday, zone)
        assertEquals(windows.single(), plan(windows, at(9, 0)).active)
        assertNull(plan(windows, at(11, 0)).active)
    }

    @Test fun overlappingAndAdjacentLessonsExitOnlyAfterLastEnd() {
        val windows = listOf(ClassDndWindow(100, 200), ClassDndWindow(150, 300), ClassDndWindow(300, 400))
        assertEquals(ClassDndWindow(100, 400), plan(windows, 200).active)
        assertEquals(400L, plan(windows, 200).nextAtMillis)
        assertNull(plan(windows, 400).active)
    }

    @Test fun lunchAndDinnerGapsAreNotSilenced() {
        val windows = classDndWindows(listOf(lesson(start = 3, end = 10)), monday, zone)
        assertEquals(3, windows.size)
        assertNull(plan(windows, at(13, 0)).active)
        assertEquals(at(14, 40), plan(windows, at(13, 0)).nextAtMillis)
        assertNull(plan(windows, at(18, 30)).active)
        assertEquals(at(19, 30), plan(windows, at(18, 30)).nextAtMillis)
    }

    @Test fun explicitDateWinsAndInvalidDateNeverFallsBack() {
        val windows = classDndWindows(listOf(
            lesson(date = "2026-11-10", week = 20, day = 7),
            lesson(date = "2026-02-30"),
        ), monday, zone)
        assertEquals(listOf(ClassDndWindow(at(8, 30, monday.plusDays(1)), at(10, 5, monday.plusDays(1)))), windows)
    }

    @Test fun missingDateUsesTermWeekWithoutInventingOtherWeeks() {
        val windows = classDndWindows(listOf(lesson(date = "", week = 3, day = 2)), monday, zone)
        assertEquals(at(8, 30, monday.plusDays(15)), windows.single().startMillis)
        assertTrue(classDndWindows(listOf(lesson(date = "")), null, zone).isEmpty())
    }

    @Test fun unknownOrReversedSectionsAreSkipped() {
        assertTrue(classDndWindows(listOf(lesson(start = 0), lesson(start = 4, end = 2),
            lesson(start = 11, end = 12), lesson(end = 12)), monday, zone).isEmpty())
    }

    @Test fun nextLessonIsPlannedAcrossLongEmptyGap() {
        val future = monday.plusDays(15)
        val windows = classDndWindows(listOf(lesson(date = future.toString())), monday, zone)
        assertEquals(at(8, 30, future), plan(windows, at(8, 0)).nextAtMillis)
    }

    @Test fun disabledAutomaticModeSchedulesNothing() {
        val windows = listOf(ClassDndWindow(100, 200))
        assertEquals(ClassDndPlan(null, null), plan(windows, 150, automatic = false))
    }

    @Test fun manualModeActivatesOnlySelectedRealLesson() {
        val selected = ClassDndWindow(100, 200)
        val other = ClassDndWindow(300, 400)
        val windows = listOf(selected, other)
        assertEquals(selected, plan(windows, 150, automatic = false, manual = selected).active)
        assertEquals(ClassDndPlan(null, null), plan(windows, 250, automatic = false, manual = selected))
        assertNull(plan(windows, 350, automatic = false, manual = selected).active)
    }

    @Test fun clearingOrReplacingScheduleCancelsManualSession() {
        val old = ClassDndWindow(100, 200)
        assertEquals(ClassDndPlan(null, null), plan(emptyList(), 150, automatic = false, manual = old))
        assertNull(plan(listOf(ClassDndWindow(300, 400)), 150, automatic = false, manual = old).active)
    }

    @Test fun cancelCurrentSkipsOverlappingGroupButKeepsNextClass() {
        val windows = listOf(ClassDndWindow(100, 200), ClassDndWindow(150, 300), ClassDndWindow(400, 500))
        assertEquals(ClassDndPlan(null, 400), plan(windows, 180, skippedUntil = 300))
        assertEquals(ClassDndWindow(400, 500), plan(windows, 400, skippedUntil = 300).active)
    }

    @Test fun timezoneChangeRecomputesPhysicalBoundary() {
        val lesson = listOf(lesson())
        val shanghai = classDndWindows(lesson, monday, zone).single()
        val utc = classDndWindows(lesson, monday, ZoneId.of("UTC")).single()
        assertEquals(8 * 60 * 60_000L, utc.startMillis - shanghai.startMillis)
        assertEquals(8 * 60 * 60_000L, utc.endMillis - shanghai.endMillis)
    }
}
