package com.menglimi.gdust.v2.widget

import com.menglimi.gdust.v2.data.CalendarInfo
import com.menglimi.gdust.v2.data.CourseBlock
import java.time.LocalDate
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetDataSourceTest {
    private val monday = LocalDate.of(2026, 11, 9)
    private val calendar = CalendarInfo(1, 20, monday.toString(), "2026", "1")

    private fun lesson(
        date: String = monday.toString(),
        week: Int = 1,
        day: Int = 1,
        start: Int = 1,
        end: Int = 2,
        name: String = "高等数学",
    ) = CourseBlock(week, day, start, end, name, "A101", "张老师", date)

    private fun snapshot(
        blocks: List<CourseBlock>,
        now: LocalDateTime = monday.atTime(8, 0),
        cal: CalendarInfo? = calendar,
        termStart: String? = null,
        source: String = "zf_term",
        odd: Double? = null,
        sampled: LocalDateTime? = null,
    ) = WidgetDataSource.snapshot(blocks, cal, termStart, source, now, odd, "一栋101", sampled)

    @Test
    fun explicitDateWinsOverWeekAndDayIncludingPortalTimestamp() {
        val rescheduled = lesson(date = "2026-11-10T00:00:00+08:00", week = 9, day = 1)
        val data = snapshot(listOf(rescheduled))
        assertEquals(WidgetData.Scope.TOMORROW, data.scope)
        assertEquals(listOf(rescheduled), data.lessons)
        assertEquals("明天 · 周二", data.title())
        assertEquals("下一节 · 明天", data.nextTitle())
    }

    @Test
    fun snapshotDateControlsTitleAcrossMidnightWithoutReadingClockAgain() {
        val tomorrow = lesson(date = "2026-11-10", day = 2)
        val data = snapshot(listOf(tomorrow), now = monday.atTime(23, 59, 59))
        assertEquals("明天 · 周二", data.title())
        assertEquals("下一节 · 明天", data.nextTitle())
    }

    @Test
    fun exactEndDropsFinishedLessonAndSelectsNextLesson() {
        val morning = lesson()
        val later = lesson(start = 3, end = 4, name = "大学英语")
        val beforeEnd = snapshot(listOf(later, morning), now = monday.atTime(10, 4, 59))
        assertEquals(morning, beforeEnd.lessons.first())
        assertEquals("正在上课", beforeEnd.nextTitle())
        val atEnd = snapshot(listOf(later, morning), now = monday.atTime(10, 5))
        assertEquals(listOf(later), atEnd.lessons)
        assertEquals(WidgetData.Scope.TODAY_REST, atEnd.scope)
        assertEquals("下一节 · 今天", atEnd.nextTitle())
        assertTrue(atEnd.ongoingLessons.isEmpty())
    }

    @Test
    fun classStartIsInclusiveAndLastClassEndFallsBackToTomorrow() {
        val today = lesson()
        val tomorrow = lesson(date = "2026-11-10", day = 2, name = "线性代数")
        assertEquals("下一节 · 今天", snapshot(listOf(today), now = monday.atTime(8, 29, 59)).nextTitle())
        assertEquals("正在上课", snapshot(listOf(today), now = monday.atTime(8, 30)).nextTitle())
        val data = snapshot(listOf(today, tomorrow), now = monday.atTime(10, 5))
        assertEquals(WidgetData.Scope.TOMORROW, data.scope)
        assertEquals(listOf(tomorrow), data.lessons)
    }

    @Test
    fun sundayToMondayAdvancesWeekForUndatedLessons() {
        val sunday = lesson(date = "", day = 7)
        val nextMonday = lesson(date = "", week = 2, day = 1, name = "第二周课程")
        val data = snapshot(listOf(sunday, nextMonday), now = monday.plusDays(6).atTime(10, 5))
        assertEquals(WidgetData.Scope.TOMORROW, data.scope)
        assertEquals(2, data.week)
        assertEquals(listOf(nextMonday), data.lessons)
        assertEquals("明天 · 周一", data.title())
    }

    @Test
    fun beforeTermDoesNotPullFirstWeekIntoToday() {
        val firstWeekSunday = lesson(date = "", day = 7)
        val data = snapshot(listOf(firstWeekSunday), now = monday.minusDays(1).atTime(8, 0))
        assertTrue(data.lessons.isEmpty())
        assertEquals(WidgetData.Scope.TOMORROW, data.scope)
    }

    @Test
    fun explicitDateWorksWithoutCalendarButUndatedCacheCannotInventCurrentWeek() {
        val dated = lesson(week = 9)
        assertEquals(listOf(dated), snapshot(listOf(dated), cal = null).lessons)
        val unknown = snapshot(listOf(lesson(date = "")), cal = null)
        assertEquals(WidgetData.Scope.EMPTY, unknown.scope)
        assertTrue(unknown.lessons.isEmpty())
    }

    @Test
    fun invalidExplicitDateIsNotReplacedWithWeekAndDay() {
        val data = snapshot(listOf(lesson(date = "2026-02-30")))
        assertEquals(WidgetData.Scope.EMPTY, data.scope)
        assertTrue(data.lessons.isEmpty())
    }

    @Test
    fun malformedTermOverrideFallsBackToValidCalendarDate() {
        val datedByWeek = lesson(date = "")
        assertEquals(listOf(datedByWeek), snapshot(listOf(datedByWeek), termStart = "invalid").lessons)
    }

    @Test
    fun legacyCopiedWeekIsRejectedButValidatedOriginalRemains() {
        val original = lesson()
        val copied = lesson(week = 2, name = "复制课程")
        val data = snapshot(listOf(copied, original), source = "legacy")
        assertEquals(listOf(original), data.lessons)
        assertEquals(WidgetData.Scope.EMPTY, snapshot(listOf(copied), source = "legacy").scope)
    }

    @Test
    fun noUpcomingCacheIsDistinctFromNoUsableSchedule() {
        val empty = snapshot(emptyList())
        assertEquals(WidgetData.Scope.EMPTY, empty.scope)
        assertEquals("暂无可用课表", empty.emptyTitle())
        val noUpcoming = snapshot(listOf(lesson()), now = monday.atTime(21, 5), source = "portal_week")
        assertEquals(WidgetData.Scope.TOMORROW, noUpcoming.scope)
        assertEquals("明天暂无缓存课程", noUpcoming.emptyTitle())
        assertTrue(noUpcoming.sourceLabel.contains("门户周课表"))
    }

    @Test
    fun unknownSectionsRemainVisibleWithoutClaimingTheyAreOngoing() {
        val unknown = lesson(start = 11, end = 12)
        val data = snapshot(listOf(unknown), now = monday.atTime(22, 0))
        assertEquals(listOf(unknown), data.lessons)
        assertFalse(unknown in data.ongoingLessons)
    }

    @Test
    fun lunchBreakIsNotShownAsOngoingForSpanningCourse() {
        val spanning = lesson(start = 3, end = 6)
        val data = snapshot(listOf(spanning), now = monday.atTime(13, 0))
        assertEquals(listOf(spanning), data.lessons)
        assertFalse(spanning in data.ongoingLessons)
        assertEquals("下一节 · 今天", data.nextTitle())
    }

    @Test
    fun missingAndNonFinitePowerNeverAppearAsValidReadings() {
        listOf(null, -1.0, Double.NaN, Double.POSITIVE_INFINITY).forEach { reading ->
            val data = snapshot(emptyList(), odd = reading)
            assertNull(data.odd)
            assertEquals("暂无电量缓存 · 打开应用更新", data.powerSourceLabel())
        }
        assertEquals(0.0, snapshot(emptyList(), odd = 0.0).odd!!, 0.0)
    }

    @Test
    fun cachedPowerExposesActualSampleTimeAndUnknownTime() {
        val unknown = snapshot(emptyList(), odd = 12.5)
        assertEquals("本机缓存 · 采样时间未知", unknown.powerSourceLabel())
        val sampled = snapshot(emptyList(), odd = 12.5, sampled = monday.atTime(7, 10))
        assertEquals("采样于 今天 07:10", sampled.powerSourceLabel())
        val stale = snapshot(emptyList(), odd = 12.5, sampled = monday.minusDays(2).atTime(7, 10))
        assertEquals("采样于 11/7 07:10", stale.powerSourceLabel())
    }
}
