package com.menglimi.gdust.v2.widget

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.menglimi.gdust.v2.data.CalendarInfo
import com.menglimi.gdust.v2.data.CourseBlock
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
) {
    enum class Scope { TODAY_REST, TODAY_ALL, TOMORROW, EMPTY }

    /** 标题行文案，如「今天 · 第 11 周」「明天 · 周三」。 */
    fun title(): String = when (scope) {
        Scope.TODAY_REST, Scope.TODAY_ALL -> "今天 · 第 $week 周"
        Scope.TOMORROW -> "明天 · ${ScheduleTimes.dayNames[LocalDate.now().dayOfWeek.value % 7]}"
        Scope.EMPTY -> "暂无课表"
    }
}

internal object WidgetDataSource {

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

        if (blocks.isEmpty()) {
            return WidgetData(cal?.week ?: 1, WidgetData.Scope.EMPTY, emptyList(), odd, roomName)
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
            return WidgetData(week, scope, restToday.sortedBy { it.startSection }, odd, roomName)
        }

        // 今天结束/无课 → 明天（跨周：周六看周日属于下周，周次用明天的日期推）
        val tomorrow = today.plusDays(1)
        val tomorrowWeek = weekOf(tomorrow)
        val tomorrowLessons = blocks.filter { it.week == tomorrowWeek && it.day == tomorrow.dayOfWeek.value }
            .sortedBy { it.startSection }
        if (tomorrowLessons.isNotEmpty()) {
            return WidgetData(tomorrowWeek, WidgetData.Scope.TOMORROW, tomorrowLessons, odd, roomName)
        }
        return WidgetData(week, WidgetData.Scope.EMPTY, emptyList(), odd, roomName)
    }
}
