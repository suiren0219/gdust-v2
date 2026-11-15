package com.menglimi.gdust.v2.widget

import androidx.compose.ui.unit.dp
import com.menglimi.gdust.v2.data.CourseBlock
import com.menglimi.gdust.v2.data.Store
import com.menglimi.gdust.v2.data.Todo
import com.menglimi.gdust.v2.data.TodoGroups
import com.menglimi.gdust.v2.data.TodoKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/** 小部件展示口径回归：形状分档、标题随展示日期、剩余按堂、截止与走势统计。 */
class WidgetPresentationTest {

    private val zone = ZoneId.systemDefault()
    private fun ms(date: LocalDate, hour: Int, minute: Int = 0): Long =
        date.atTime(hour, minute).atZone(zone).toInstant().toEpochMilli()

    private fun data(
        scope: WidgetData.Scope,
        lessons: List<CourseBlock>,
        week: Int? = 1,
        date: LocalDate = LocalDate.of(2026, 11, 14),
    ) = WidgetData(
        week = week, scope = scope, scopeDate = date, lessons = lessons,
        odd = 35.2, roomName = "主分区", warnTh = 20f, lastSampleAt = 0L,
        termStart = LocalDate.of(2026, 11, 9),
        now = LocalDateTime.of(date, LocalTime.NOON),
    )

    private fun block(start: Int, end: Int, name: String = "高等数学", day: Int = 6) =
        CourseBlock(week = 1, day = day, startSection = start, endSection = end, name = name, room = "A-301", teacher = "", date = "2026-11-14")

    // ── 形状分档 ────────────────────────────────────────────────────────────

    @Test
    fun shapeMatchesLauncherCells() {
        assertEquals(WidgetPresentation.Shape.WIDE, WidgetPresentation.shapeOf(300.dp, 150.dp))
        assertEquals(WidgetPresentation.Shape.MEDIUM, WidgetPresentation.shapeOf(150.dp, 150.dp))
        assertEquals(WidgetPresentation.Shape.TALL, WidgetPresentation.shapeOf(75.dp, 150.dp))
        // 1×2 竖条横拉：宽>高且不够高的横条落到 ROW
        assertEquals(WidgetPresentation.Shape.ROW, WidgetPresentation.shapeOf(150.dp, 75.dp))
        assertEquals(WidgetPresentation.Shape.ROW, WidgetPresentation.shapeOf(300.dp, 75.dp))
    }

    // ── 可读性预算：真实尺寸、字体放大、数值精度与强调文字对比 ────────────────

    @Test
    fun largerFontsAndShorterCardsReduceRowsBeforeTextSize() {
        val normal = WidgetPresentation.layout(340.dp, 170.dp, 1f)
        val large = WidgetPresentation.layout(340.dp, 170.dp, 1.3f)
        val shortest = WidgetPresentation.layout(250.dp, 110.dp, 1f)
        assertTrue(normal.visibleRows(8) > large.visibleRows(8))
        assertTrue(normal.visibleRows(8) > shortest.visibleRows(8))
        assertTrue(shortest.visibleRows(8) >= 1)
        listOf(normal, large, shortest, WidgetPresentation.layout(250.dp, 110.dp, 2f)).forEach { layout ->
            val rows = layout.visibleRows(8)
            val used = layout.headerHeight + WidgetStyle.Space.gap + rows * layout.rowHeight.value
            assertTrue("行高不能越过实际卡面：$layout", used <= layout.contentHeight)
            assertTrue(layout.rowHeight.value >= layout.line(WidgetStyle.Type.body) + 12f)
            assertEquals(0, layout.visibleRows(0))
            if (layout.showMore(8)) {
                assertTrue(layout.contentHeight - used >= layout.line(WidgetStyle.Type.caption))
            }
        }
    }

    @Test
    fun narrowWidthDropsSecondaryColumnsAndRangeControls() {
        val normal = WidgetPresentation.layout(340.dp, 170.dp, 1f)
        val narrow = WidgetPresentation.layout(250.dp, 110.dp, 1f)
        val enlarged = WidgetPresentation.layout(340.dp, 170.dp, 1.3f)
        assertTrue(normal.showColumns)
        assertTrue(normal.showRangeSwitch)
        assertFalse(narrow.showColumns)
        assertFalse(narrow.showRangeSwitch)
        assertFalse(enlarged.showColumns)
        assertFalse(WidgetPresentation.layout(150.dp, 75.dp, 1.3f).showRowDetail)
    }

    @Test
    fun mediumChartAndDetailsRequireRoomForReadableLabels() {
        val normal = WidgetPresentation.layout(180.dp, 180.dp, 1f)
        val enlarged = WidgetPresentation.layout(180.dp, 180.dp, 1.3f)
        val minimum = WidgetPresentation.layout(130.dp, 110.dp, 1f)
        assertTrue(normal.chartHeight.value > 0f)
        assertTrue(normal.chartHeight.value >= normal.line(WidgetStyle.Type.caption) * 2 + 24f)
        assertEquals(0f, enlarged.chartHeight.value, 0.001f)
        assertEquals(0f, minimum.chartHeight.value, 0.001f)
        assertTrue(normal.detailLines > enlarged.detailLines)
        assertEquals(0, minimum.detailLines)
        assertTrue(normal.headerHeight + normal.line(WidgetStyle.Type.number) + WidgetStyle.Space.gap + normal.chartHeight.value <= normal.contentHeight)
    }

    @Test
    fun minimumTallCardKeepsExactPowerAndUnitAtLargeFontScales() {
        listOf(1f, 1.3f, 2f).forEach { scale ->
            val layout = WidgetPresentation.layout(57.dp, 110.dp, scale)
            assertTrue(layout.narrow)
            assertFalse(layout.showTallFooter)
            listOf("46.8", "19.9", "0.1", "—").forEach { value ->
                val metric = WidgetPresentation.metric(value, layout)
                assertEquals(value, metric.text)
                assertTrue(metric.sizeSp > 0f)
                assertTrue(layout.line(metric.sizeSp) + layout.line(WidgetStyle.Type.label) <= layout.contentHeight)
            }
        }
        val small = WidgetPresentation.metric("19.9", WidgetPresentation.layout(57.dp, 110.dp, 1.3f))
        val regular = WidgetPresentation.metric("19.9", WidgetPresentation.layout(96.dp, 192.dp, 1f))
        assertTrue(regular.sizeSp > small.sizeSp)
        assertTrue(WidgetPresentation.powerLow(19.9, 20f))
        assertEquals("—", WidgetPresentation.powerValue(null))
    }

    @Test
    fun textAccentsPassOnCardAndCompositedBadgeInBothThemes() {
        listOf(false, true).forEach { dark ->
            val card = WidgetStyle.card(dark)
            val accentBadge = WidgetStyle.blend(WidgetStyle.accent(dark), card, if (dark) 0.24f else 0.14f)
            val dangerBadge = WidgetStyle.blend(WidgetStyle.danger(dark), card, 0.12f)
            listOf(card, accentBadge).forEach { background ->
                assertTrue(WidgetStyle.contrastRatio(WidgetStyle.accentText(dark), background) >= 4.5)
            }
            listOf(card, dangerBadge).forEach { background ->
                assertTrue(WidgetStyle.contrastRatio(WidgetStyle.dangerText(dark), background) >= 4.5)
            }
        }
        // 基准装饰色本身不够承载小字；派生前景必须确实改善对比。
        assertTrue(WidgetStyle.contrastRatio(WidgetStyle.ACCENT_LIGHT, WidgetStyle.CARD_LIGHT) < 4.5)
        assertTrue(WidgetStyle.contrastRatio(WidgetStyle.DANGER, WidgetStyle.CARD_LIGHT) < 4.5)
        assertEquals(21.0, WidgetStyle.contrastRatio(WidgetStyle.TEXT_LIGHT, WidgetStyle.CARD_LIGHT), 0.0001)
    }

    // ── 标题与口径 ──────────────────────────────────────────────────────────

    @Test
    fun tomorrowTitleUsesTomorrowWeekday() {
        // 2026-11-14 是周六 → 明天是周日；旧实现错用今天的星期
        val d = data(WidgetData.Scope.TOMORROW, listOf(block(3, 4)), date = LocalDate.of(2026, 11, 15))
        assertEquals("明天 · 周日", WidgetPresentation.scheduleTitle(d))
    }

    @Test
    fun summaryCountsBlocksNotSections() {
        // 一张 1-4 节的卡是一堂课，不再被数成 4 节
        val d = data(WidgetData.Scope.TODAY_REST, listOf(block(1, 4), block(5, 6)))
        assertEquals("剩 2 堂", WidgetPresentation.scheduleSummary(d))
        val all = data(WidgetData.Scope.TODAY_ALL, listOf(block(1, 4)))
        assertEquals("今日 1 堂", WidgetPresentation.scheduleSummary(all))
    }

    @Test
    fun weekUnknownBeforeTermStartOrWithoutCalendar() {
        val d = data(WidgetData.Scope.TODAY_ALL, listOf(block(1, 2)), week = null)
        assertEquals("今天", WidgetPresentation.scheduleTitle(d))
        assertEquals("待导入", WidgetPresentation.scheduleSummary(d.copy(scope = WidgetData.Scope.NO_DATA, lessons = emptyList())))
    }

    // ── 空态区分 ────────────────────────────────────────────────────────────

    @Test
    fun emptyStatesDistinguishNoDataAndNoLessons() {
        assertEquals("还没有课表数据", WidgetPresentation.scheduleEmpty(data(WidgetData.Scope.NO_DATA, emptyList())))
        assertEquals("今明两天没有课程", WidgetPresentation.scheduleEmpty(data(WidgetData.Scope.NO_LESSONS, emptyList())))
    }

    // ── 倒计时与截止 ────────────────────────────────────────────────────────

    @Test
    fun countdownRespectsHalfOpenInterval() {
        val b = block(1, 2) // 08:30–10:05
        val before = WidgetPresentation.nextCountdown(WidgetData.Scope.TODAY_ALL, b, LocalTime.of(8, 0))
        assertTrue(before.contains("分钟后"))
        // 08:30 起算进行中；10:05 整（半开区间）不再显示剩余分钟
        val ongoing = WidgetPresentation.nextCountdown(WidgetData.Scope.TODAY_ALL, b, LocalTime.of(8, 30))
        assertTrue(ongoing.startsWith("上课中"))
        val done = WidgetPresentation.nextCountdown(WidgetData.Scope.TODAY_ALL, b, LocalTime.of(10, 5))
        assertEquals("上课中", done)
    }

    @Test
    fun unknownSectionKeepsButDoesNotClaimOngoing() {
        val b = block(11, 12) // 超出作息表的节次无时间
        assertEquals("第 11 节", WidgetPresentation.nextCountdown(WidgetData.Scope.TODAY_ALL, b, LocalTime.NOON))
    }

    @Test
    fun dueTextFlagsOverdue() {
        val now = ms(LocalDate.of(2026, 11, 14), 12)
        val overdue = Todo(1, "作业", false, TodoKind.HOMEWORK, "", ms(LocalDate.of(2026, 11, 13), 10))
        val today = Todo(2, "考试", false, TodoKind.EXAM, "", ms(LocalDate.of(2026, 11, 14), 20, 30))
        assertEquals("逾期 1 天" to true, WidgetPresentation.dueText(overdue, now))
        assertEquals("今天 20:30" to false, WidgetPresentation.dueText(today, now))
    }

    // ── 走势统计 ────────────────────────────────────────────────────────────

    @Test
    fun trendStatsNeedsTwoSamples() {
        val now = ms(LocalDate.of(2026, 11, 14), 18)
        assertNull(WidgetPresentation.trendStats(listOf(Store.Sample(now, 50.0)), now, now))
        val stats = WidgetPresentation.trendStats(
            listOf(
                Store.Sample(now - 3600_000L * 6, 44.0),
                Store.Sample(now, 42.4),
            ),
            now, now,
        )
        assertTrue(stats != null)
        stats!!
        assertEquals(42.4, stats.lo, 0.001)
        assertEquals(44.0, stats.hi, 0.001)
        assertEquals(42.4, stats.endValue, 0.001)
        assertEquals(6.0, stats.coverageHours, 0.001)
        assertTrue(stats.ratePerHour!! < 0) // 在下降
        assertTrue(WidgetPresentation.rateLabel(stats).startsWith("↓"))
    }

    @Test
    fun longCoverageSwitchesToDateFormat() {
        val now = ms(LocalDate.of(2026, 11, 14), 18)
        val stats = WidgetPresentation.trendStats(
            listOf(
                Store.Sample(now - 3600_000L * 96, 58.0),
                Store.Sample(now, 42.0),
            ),
            now, now,
        )
        // 覆盖超过 48 小时：横轴不再用 HH:mm
        assertFalse(stats!!.startLabel.contains(":"))
    }

    // ── 待办排序 ────────────────────────────────────────────────────────────

    @Test
    fun pendingTodosPutsOverdueFirst() {
        val now = ms(LocalDate.of(2026, 11, 14), 12)
        val groups = TodoGroups(
            today = listOf(Todo(2, "今天到期", false, dueAt = now + 1000)),
            overdue = listOf(Todo(1, "已逾期", false, dueAt = now - 1000)),
            noDue = listOf(Todo(3, "无截止", false)),
        )
        val ordered = WidgetPresentation.pendingTodos(groups, now)
        assertEquals(listOf("已逾期", "今天到期", "无截止"), ordered.map { it.todo.text })
        assertTrue(ordered[0].overdue)
        assertFalse(ordered[1].overdue)
    }
}
