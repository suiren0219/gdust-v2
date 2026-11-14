package com.menglimi.gdust.v2.widget

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.menglimi.gdust.v2.data.CalendarInfo
import com.menglimi.gdust.v2.data.CourseBlock
import com.menglimi.gdust.v2.data.Store
import com.menglimi.gdust.v2.data.TodoGroups
import com.menglimi.gdust.v2.data.groupTodos
import com.menglimi.gdust.v2.data.parseTodos
import com.menglimi.gdust.v2.ui.schedule.ScheduleTimes
import java.time.LocalDate
import java.time.LocalTime
import java.time.temporal.ChronoUnit

/** 小组件数据快照：全部来自本机缓存，不打网络。 */
data class WidgetData(
    val week: Int,
    /** today = 今天还没下课的课；tomorrow = 今天课程已结束/无课时回退显示明天。 */
    val scope: Scope,
    val lessons: List<CourseBlock>,
    val odd: Double?,
    val roomName: String,
    /** 低电量阈值（gdust_cache "warn_th"，默认 20 度），用于小部件危险色。 */
    val warnTh: Float = 20f,
) {
    enum class Scope { TODAY_REST, TODAY_ALL, TOMORROW, EMPTY }

    /** 标题行文案，如「今天 · 第 11 周」「明天 · 周三」。 */
    fun title(): String = when (scope) {
        Scope.TODAY_REST, Scope.TODAY_ALL -> "今天 · 第 $week 周"
        Scope.TOMORROW -> "明天 · ${ScheduleTimes.dayNames[LocalDate.now().dayOfWeek.value - 1]}"
        Scope.EMPTY -> "暂无课表"
    }
}

internal object WidgetDataSource {

    /** 电量款走势范围（七天 = 7 / 半月 = 15），由小部件上的切换钮写入。 */
    fun powerRangeDays(context: Context): Int =
        context.getSharedPreferences("gdust_cache", Context.MODE_PRIVATE).getInt("widget_power_range", 7)

    /** 电量走势样本（gdust_cache "samples"，App 后台小时采样自动积累）。 */
    fun trend(context: Context, days: Int): List<Store.Sample> {
        val plain = context.getSharedPreferences("gdust_cache", Context.MODE_PRIVATE)
        val t = object : TypeToken<List<Store.Sample>>() {}.type
        val all = runCatching {
            Gson().fromJson<List<Store.Sample>>(plain.getString("samples", "[]"), t)
        }.getOrDefault(emptyList())
        val cutoff = System.currentTimeMillis() - days.toLong() * 24 * 3600 * 1000
        return all.filter { it.time >= cutoff }.sortedBy { it.time }
    }

    /** 待办分组（结构化 Todo，旧 JSON 自动兼容）。 */
    fun todos(context: Context): TodoGroups {
        val plain = context.getSharedPreferences("gdust_cache", Context.MODE_PRIVATE)
        val list = runCatching { parseTodos(plain.getString("todos", "[]").orEmpty(), Gson()) }
            .getOrDefault(emptyList())
        return groupTodos(list, System.currentTimeMillis())
    }

    fun load(context: Context): WidgetData {
        val cache = context.getSharedPreferences("gdust_cache", Context.MODE_PRIVATE)
        val power = context.getSharedPreferences("gdust_power", Context.MODE_PRIVATE)
        val gson = Gson()
        val blocks = runCatching {
            gson.fromJson<List<CourseBlock>>(
                cache.getString("blocks", "[]"),
                object : TypeToken<List<CourseBlock>>() {}.type,
            )
        }.getOrDefault(emptyList())
        val cal = runCatching {
            gson.fromJson(cache.getString("calendar", null), CalendarInfo::class.java)
        }.getOrNull()
        val odd = power.getFloat("prev_odd", -1f).takeIf { it >= 0 }?.toDouble()
        val roomName = power.getString("name", null).orEmpty()
        val warnTh = cache.getFloat("warn_th", 20f)

        if (blocks.isEmpty()) {
            return WidgetData(cal?.week ?: 1, WidgetData.Scope.EMPTY, emptyList(), odd, roomName, warnTh)
        }

        val termStart = cache.getString("term_start", null)?.takeIf { it.isNotBlank() } ?: cal?.startDate
        val weekOf = { date: LocalDate ->
            termStart?.let {
                runCatching { (ChronoUnit.DAYS.between(LocalDate.parse(it), date) / 7 + 1).toInt() }.getOrNull()
            } ?: cal?.week ?: 1
        }
        val today = LocalDate.now()
        val week = weekOf(today)
        val now = LocalTime.now()

        // 今天还没下课的课：结束时间晚于当前时刻（无时间的按节次兜底）
        val restToday = blocks.filter { it.week == week && it.day == today.dayOfWeek.value }
            .filter { b ->
                val end = ScheduleTimes.end(b.endSection)
                end.isEmpty() || now.isBefore(LocalTime.parse(end))
            }
        if (restToday.isNotEmpty()) {
            val allToday = blocks.filter { it.week == week && it.day == today.dayOfWeek.value }
            val scope = if (restToday.size == allToday.size) WidgetData.Scope.TODAY_ALL else WidgetData.Scope.TODAY_REST
            return WidgetData(week, scope, restToday.sortedBy { it.startSection }, odd, roomName, warnTh)
        }

        // 今天结束/无课 → 明天（跨周：周六看周日属于下周，周次用明天的日期推）
        val tomorrow = today.plusDays(1)
        val tomorrowWeek = weekOf(tomorrow)
        val tomorrowLessons = blocks.filter { it.week == tomorrowWeek && it.day == tomorrow.dayOfWeek.value }
            .sortedBy { it.startSection }
        if (tomorrowLessons.isNotEmpty()) {
            return WidgetData(tomorrowWeek, WidgetData.Scope.TOMORROW, tomorrowLessons, odd, roomName, warnTh)
        }
        return WidgetData(week, WidgetData.Scope.EMPTY, emptyList(), odd, roomName, warnTh)
    }
}
