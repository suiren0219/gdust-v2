package com.menglimi.gdust.v2.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ZfScheduleParserTest {
    private val calendar = CalendarInfo(
        week = 4,
        allWeek = 16,
        startDate = "2026-09-07",
        year = "2026",
        semester = "1",
    )

    @Test
    fun parseWeeksExpandsRangesParityAndDiscreteWeeks() {
        assertEquals(listOf(1, 3, 5, 7, 9), ZfScheduleParser.parseWeeks("1-9周（单）", 16))
        assertEquals(listOf(2, 4, 6, 8), ZfScheduleParser.parseWeeks("2-8周(双)", 16))
        assertEquals(listOf(1, 4, 9), ZfScheduleParser.parseWeeks("1，4，9周", 16))
    }

    @Test
    fun parseKeepsParitySegmentsAndBreakWeeksIndependent() {
        val courses = ZfScheduleParser.parse(
            """{"kbList":[{"kcmc":"单双分段","xqj":1,"jcs":"1-2节","zcd":"1-4周(单),5-8周(双)"},{"kcmc":"隔周停课","xqj":2,"jcs":"3-4","zcd":"1-3周,7-9周"}]}""",
            calendar,
        )

        assertEquals(listOf(1, 3, 6, 8), courses.filter { it.name == "单双分段" }.map { it.week })
        assertEquals(listOf(1, 2, 3, 7, 8, 9), courses.filter { it.name == "隔周停课" }.map { it.week })
        assertEquals(listOf(0, 0, 0, 0), courses.filter { it.name == "单双分段" }.map { it.weekParity })
    }

    @Test
    fun isolatedTenthWeekAdjustmentDoesNotChangeOtherWeeks() {
        val courses = ZfScheduleParser.parse(
            """{"kbList":[{"kcmc":"常规课","xqj":1,"jcs":"1-2","zcd":"1-9周,11-16周","cdmc":"原教室"},{"kcmc":"常规课","xqj":3,"jcs":"5-6节","zcd":"10周","cdmc":"临时教室"}]}""",
            calendar,
        )

        assertEquals((1..16).filter { it != 10 }, courses.filter { it.room == "原教室" }.map { it.week })
        val changed = courses.single { it.room == "临时教室" }
        assertEquals(10, changed.week)
        assertEquals(3, changed.day)
        assertEquals(5, changed.startSection)
        assertEquals("2026-11-11", changed.date)
    }

    @Test
    fun emptyOptionalFieldsAndJsonNullDoNotDropCourse() {
        val courses = ZfScheduleParser.parse(
            """{"kbList":[{"kcmc":"自习","xqj":5,"jcs":"1-2节","zcd":"1周","xm":null,"cdmc":""}]}""",
            calendar,
        )

        assertEquals(1, courses.size)
        assertEquals("", courses.single().teacher)
        assertEquals("", courses.single().room)
        assertEquals("2026-09-11", courses.single().date)
    }

    @Test
    fun missingOrInvalidWeeksFailsInsteadOfReturningPartialSchedule() {
        val error = runCatching {
            ZfScheduleParser.parse(
                """{"kbList":[{"kcmc":"有效","xqj":1,"jcs":"1-2","zcd":"1-2周"},{"kcmc":"不完整","xqj":2,"jcs":"3-4"}]}""",
                calendar,
            )
        }.exceptionOrNull()

        assertTrue(error is IllegalArgumentException)
        assertTrue(error?.message.orEmpty().contains("zcd"))
    }

    @Test
    fun parserNeverCopiesCurrentWeekSnapshotAcrossTerm() {
        val courses = ZfScheduleParser.parse(
            """{"kbList":[{"kcmc":"仅本周","xqj":4,"jcs":"1节","zcd":"4周"}]}""",
            calendar,
        )

        assertEquals(listOf(4), courses.map { it.week })
        assertEquals("2026-10-01", courses.single().date)
    }

    @Test
    fun emptyKbListAndOutOfTermWeeksAreErrors() {
        val emptyError = runCatching { ZfScheduleParser.parse("""{"kbList":[]}""", calendar) }.exceptionOrNull()
        assertTrue(emptyError is IllegalArgumentException)
        assertTrue(emptyError?.message.orEmpty().contains("无课"))

        val weekError = runCatching { ZfScheduleParser.parseWeeks("1-17周", 16) }.exceptionOrNull()
        assertTrue(weekError is IllegalArgumentException)
    }
}
