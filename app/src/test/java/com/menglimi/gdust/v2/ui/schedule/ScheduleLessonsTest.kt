// Regression tests for GDUST's pure schedule slot/weekly detail adaptation.
package com.menglimi.gdust.v2.ui.schedule

import com.menglimi.gdust.v2.data.CourseBlock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScheduleLessonsTest {
    private fun lesson(
        name: String = "高等数学",
        week: Int = 1,
        day: Int = 1,
        start: Int = 1,
        end: Int = 2,
        room: String = "A101",
        teacher: String = "张老师",
        parity: Int = 0,
    ) = CourseBlock(week, day, start, end, name, room, teacher, "", parity)

    @Test
    fun aggregateKeepsOnlyImportedWeeksAndDeduplicatesResponses() {
        val selected = lesson(week = 1, parity = 1)
        val later = lesson(week = 7, parity = 1)
        val result = aggregateCellLessons(selected, listOf(later, selected, later, selected))
        assertEquals(1, result.size)
        assertEquals(listOf(1, 7), result.single().weeks)
        assertEquals(1, result.single().startSection)
        assertEquals(2, result.single().endSection)
    }

    @Test
    fun sameNameWithDifferentRoomTeacherOrSpanRemainsSeparate() {
        val selected = lesson()
        val changedRoom = lesson(week = 2, room = "B202")
        val changedTeacher = lesson(week = 3, teacher = "李老师")
        val changedSpan = lesson(week = 4, start = 2, end = 3)
        val result = aggregateCellLessons(selected, listOf(selected, changedRoom, changedTeacher, changedSpan))
        assertEquals(4, result.size)
        assertTrue(result.any { it.room == "B202" && it.weeks == listOf(2) })
        assertTrue(result.any { it.teacher == "李老师" && it.weeks == listOf(3) })
        assertTrue(result.any { it.startSection == 2 && it.endSection == 3 && it.weeks == listOf(4) })
        assertFalse(result.any { it.startSection == 1 && it.endSection == 3 })
    }

    @Test
    fun aggregateExcludesOtherDaysAndNonOverlappingSlots() {
        val selected = lesson()
        val result = aggregateCellLessons(
            selected,
            listOf(selected, lesson(day = 2), lesson(start = 3, end = 4), lesson(week = 2, start = 5, end = 6)),
        )
        assertEquals(1, result.size)
        assertEquals(listOf(1), result.single().weeks)
    }

    @Test
    fun currentWeekConnectedOverlapsIncludeEveryHiddenLessonInDetails() {
        val first = lesson(name = "课程甲", start = 1, end = 2)
        val bridge = lesson(name = "课程乙", start = 2, end = 4)
        val last = lesson(name = "课程丙", start = 4, end = 5)
        val otherWeek = lesson(name = "课程丁", week = 3, start = 5, end = 6)
        val unrelated = lesson(name = "课程戊", start = 6, end = 7)
        val result = aggregateCellLessons(first, listOf(unrelated, otherWeek, last, first, bridge))
        assertEquals(listOf("课程甲", "课程乙", "课程丙", "课程丁"), result.map { it.name })
        assertEquals(listOf(3), result.last().weeks)
        assertEquals(5, result.last().startSection)
        assertEquals(6, result.last().endSection)
    }

    @Test
    fun anOverlapOnlyInAnotherWeekDoesNotPullInUnrelatedCurrentCourses() {
        val first = lesson(name = "课程甲", start = 1, end = 2)
        val otherWeekBridge = lesson(name = "课程乙", week = 2, start = 2, end = 4)
        val currentLater = lesson(name = "课程丙", start = 4, end = 5)
        val result = aggregateCellLessons(first, listOf(first, otherWeekBridge, currentLater))
        assertEquals(listOf("课程甲", "课程乙"), result.map { it.name })
    }

    @Test
    fun groupingHandlesUnsortedAndTransitiveOverlapsButNotAdjacentSlots() {
        val first = lesson(name = "甲", start = 1, end = 2)
        val bridge = lesson(name = "乙", start = 2, end = 4)
        val last = lesson(name = "丙", start = 4, end = 5)
        val adjacent = lesson(name = "丁", start = 6, end = 7)
        assertEquals(
            listOf(listOf(first, bridge, last), listOf(adjacent)),
            groupOverlappingLessons(listOf(adjacent, last, first, bridge)),
        )
        assertTrue(groupOverlappingLessons(emptyList()).isEmpty())
    }

    @Test
    fun lanesShareOnlyExactSpansAndNeverChangeActualBlocks() {
        val first = lesson(name = "甲", start = 1, end = 2)
        val sameSlot = lesson(name = "乙", start = 1, end = 2)
        val overlap = lesson(name = "丙", start = 2, end = 4)
        val later = lesson(name = "丁", start = 4, end = 5)
        val blocks = listOf(later, overlap, sameSlot, first)
        val lanes = lessonLanes(blocks)
        assertEquals(2, lanes.size)
        assertEquals(blocks.toSet(), lanes.flatten().flatten().toSet())
        assertTrue(lanes.flatten().any { it.toSet() == setOf(first, sameSlot) })
        lanes.forEach { lane ->
            lane.zipWithNext().forEach { (a, b) -> assertTrue(a.first().endSection < b.first().startSection) }
            lane.forEach { slot -> assertEquals(1, slot.map { it.startSection to it.endSection }.distinct().size) }
        }
        assertTrue(lessonLanes(emptyList()).isEmpty())
    }

    @Test
    fun weekLabelsRequireActualCompleteCoverageForEveryWeek() {
        assertEquals("每周", formatScheduleWeeks(listOf(4, 2, 1, 3, 3), allWeeks = 4))
        assertEquals("1–2 周、4 周", formatScheduleWeeks(listOf(1, 2, 2, 4), allWeeks = 4))
        assertEquals("2–5 周", formatScheduleWeeks(listOf(2, 3, 4, 5), allWeeks = 4))
        assertEquals("1 周、3 周、7 周", formatScheduleWeeks(listOf(7, 1, 3), allWeeks = 16))
        assertEquals("", formatScheduleWeeks(emptyList(), allWeeks = 16))
    }
}
