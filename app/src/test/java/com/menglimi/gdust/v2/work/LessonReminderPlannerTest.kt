package com.menglimi.gdust.v2.work

import com.menglimi.gdust.v2.data.CourseBlock
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LessonReminderPlannerTest {
    private val zone = ZoneId.of("Asia/Shanghai")
    private val monday = LocalDate.of(2026, 11, 9)

    private fun millis(day: LocalDate, hour: Int, minute: Int): Long =
        day.atTime(hour, minute).atZone(zone).toInstant().toEpochMilli()

    private fun lesson(date: String = "2026-11-09", week: Int = 1, day: Int = 1,
                       start: Int = 1, end: Int = 2, name: String = "线性代数") =
        CourseBlock(week, day, start, end, name, "A-101", "王老师", date)

    private fun rules(termStart: LocalDate? = monday, lead: Int = 20, inClass: Boolean = true,
                      inClassLead: Int = 10, nextDay: Boolean = true, nextDayHour: Int = 23) =
        ReminderRules(termStart, 1, lead, inClass, inClassLead, nextDay, nextDayHour)

    @Test
    fun beforeClassTimeIsStartMinusLead() {
        val now = millis(monday, 7, 0)
        val events = planReminders(listOf(lesson()), rules(), now, zone)
        val before = events.first { it.kind == ReminderKind.BEFORE_CLASS }
        assertEquals(millis(monday, 8, 10), before.atMillis) // 08:30 - 20min
        assertEquals("lesson-2026-11-09-1-线性代数", before.key)
    }

    @Test
    fun inClassTimeIsEndMinusLead() {
        val events = planReminders(listOf(lesson()), rules(), millis(monday, 7, 0), zone)
        val inClass = events.first { it.kind == ReminderKind.IN_CLASS }
        assertEquals(millis(monday, 9, 55), inClass.atMillis) // 10:05(第2节结束) - 10min
    }

    @Test
    fun nextDaySummaryIsEmittedOnPreviousEvening() {
        val todayLesson = lesson(date = "2026-11-09")
        val tomorrowLesson = lesson(date = "2026-11-10", day = 2, name = "高等数学")
        val events = planReminders(listOf(todayLesson, tomorrowLesson), rules(), millis(monday, 7, 0), zone)
        val nextDay = events.filter { it.kind == ReminderKind.NEXT_DAY }
        assertEquals(1, nextDay.size)
        assertEquals(millis(monday, 23, 0), nextDay.single().atMillis)
        assertEquals(monday.plusDays(1), nextDay.single().date)
        assertEquals(listOf(tomorrowLesson), nextDay.single().nextDayBlocks)
    }

    @Test
    fun explicitDateOverridesWeekAndDay() {
        // week=9/day=1 与真实日期冲突：按 11/10（周二）而不是 week=9 归课；
        // planner 只规划「今天+明天」，两天之外的排课不应出现在事件里。
        val tuesday = lesson(date = "2026-11-10", week = 9, day = 1)
        val later = lesson(date = "2026-11-12", week = 9, day = 1, name = "周四课")
        val events = planReminders(listOf(tuesday, later), rules(), millis(monday, 7, 0), zone)
        val onTuesday = events.filter { it.kind == ReminderKind.BEFORE_CLASS && it.date == monday.plusDays(1) }
        assertEquals(1, onTuesday.size)
        assertEquals(tuesday.name, onTuesday.single().block?.name)
        assertFalse(events.any { it.block?.name == "周四课" })
    }

    @Test
    fun invalidOrUnknownSectionIsSkipped() {
        val unknown = lesson(date = "2026-11-09", start = 11, end = 12, name = "未知节次")
        val invalid = lesson(date = "2026-02-30", start = 1, end = 2, name = "非法日期")
        val events = planReminders(listOf(unknown, invalid), rules(), millis(monday, 7, 0), zone)
        assertFalse(events.any { it.block?.name == "未知节次" })
        assertFalse(events.any { it.block?.name == "非法日期" })
        assertFalse(events.any { it.nextDayBlocks.any { b -> b.name == "非法日期" } })
    }

    @Test
    fun disabledCategoriesDoNotProduceEvents() {
        val rules = rules(inClass = false, nextDay = false)
        val events = planReminders(listOf(lesson()), rules, millis(monday, 7, 0), zone)
        assertEquals(1, events.size)
        assertEquals(ReminderKind.BEFORE_CLASS, events.single().kind)
    }

    @Test
    fun eventsAreSortedByTime() {
        val lessons = listOf(
            lesson(date = "2026-11-09", start = 9, end = 10, name = "晚课"),
            lesson(date = "2026-11-09", start = 1, end = 2, name = "早课"),
            lesson(date = "2026-11-10", day = 2, start = 1, end = 2, name = "明天的课"),
        )
        val events = planReminders(lessons, rules(), millis(monday, 0, 1), zone)
        assertEquals(events.sortedBy { it.atMillis }.map { it.key }, events.map { it.key })
        assertTrue(events.last().atMillis > millis(monday, 23, 0))
    }

    @Test
    fun termWeekUsesFloorDivisionBeforeTermStart() {
        val before = monday.minusDays(7)
        val preTerm = lesson(date = "", week = 0, day = 1, name = "学期前")
        val firstWeek = lesson(date = "", week = 1, day = 1, name = "第一周")
        // 11/02 按 floorDiv 是 week 0：学期前数据应在那天命中，而不被误算成 week 1
        val events = planReminders(listOf(preTerm, firstWeek), rules(termStart = monday), millis(before, 0, 1), zone)
        val beforeEvents = events.filter { it.kind == ReminderKind.BEFORE_CLASS && it.date == before }
        assertEquals(listOf("学期前"), beforeEvents.mapNotNull { it.block?.name })
        // 次日汇总会看到 11/03 的 week=0/day=2 数据（本例没有），第一周课程不在学期前窗口内
        assertTrue(events.none { it.kind == ReminderKind.BEFORE_CLASS && it.block?.name == "第一周" })
    }

    @Test
    fun nextDayHourAndLeadAreConfigurable() {
        val rules = rules(lead = 5, nextDayHour = 21)
        val events = planReminders(listOf(lesson(), lesson(date = "2026-11-10", day = 2, name = "明")), rules, millis(monday, 7, 0), zone)
        assertEquals(millis(monday, 8, 25), events.first { it.kind == ReminderKind.BEFORE_CLASS }.atMillis)
        assertEquals(millis(monday, 21, 0), events.first { it.kind == ReminderKind.NEXT_DAY }.atMillis)
    }
}
