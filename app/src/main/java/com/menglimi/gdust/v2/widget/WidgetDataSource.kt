package com.menglimi.gdust.v2.widget

import android.content.Context
import com.google.gson.Gson
import com.google.gson.JsonParser
import com.google.gson.reflect.TypeToken
import com.menglimi.gdust.v2.data.CourseBlock
import com.menglimi.gdust.v2.data.Store
import com.menglimi.gdust.v2.data.TodoGroups
import com.menglimi.gdust.v2.data.groupTodos
import com.menglimi.gdust.v2.data.parseTodos
import com.menglimi.gdust.v2.ui.schedule.ScheduleTimes
import com.menglimi.gdust.v2.work.PowerWorker
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/** 小组件数据快照：全部来自本机缓存，不打网络。 */
data class WidgetData(
    val week: Int?,
    val scope: Scope,
    val scopeDate: LocalDate,
    val lessons: List<CourseBlock>,
    val odd: Double?,
    val roomName: String,
    val warnTh: Float = 20f,
    val lastSampleAt: Long = 0L,
    val termStart: LocalDate? = null,
    /** 标题、筛选与进行中判定沿用同一快照，跨午夜不二次读取时钟。 */
    val now: LocalDateTime = LocalDateTime.now(),
    val scheduleLastSyncAt: Long = 0L,
    val powerBound: Boolean = false,
    /** 只有正方整学期来源才可断言某天没有排课。 */
    val scheduleImported: Boolean = false,
    /** 仅提示缓存年龄，不代表学校会话失效。 */
    val scheduleStale: Boolean = false,
    val hasScheduleCache: Boolean = false,
    val powerStale: Boolean = false,
) {
    enum class Scope { TODAY_REST, TODAY_ALL, TOMORROW, TOMORROW_EMPTY, NO_LESSONS, INCOMPLETE, NO_DATA }
}

internal object WidgetDataSource {
    fun powerRangeDays(context: Context): Int =
        context.getSharedPreferences("gdust_cache", Context.MODE_PRIVATE).getInt("widget_power_range", 7)
            .takeIf { it == 7 || it == 15 } ?: 7

    fun trend(context: Context, days: Int): List<Store.Sample> {
        val plain = context.getSharedPreferences("gdust_cache", Context.MODE_PRIVATE)
        val type = object : TypeToken<List<Store.Sample>>() {}.type
        val all = runCatching {
            Gson().fromJson<List<Store.Sample>>(plain.getString("samples", "[]"), type).orEmpty()
        }.getOrDefault(emptyList())
        val cutoff = System.currentTimeMillis() - days.toLong() * 24 * 3600 * 1000
        return all.filter { it.time >= cutoff && it.odd.isFinite() }.sortedBy { it.time }
    }

    fun todos(context: Context): TodoGroups {
        val plain = context.getSharedPreferences("gdust_cache", Context.MODE_PRIVATE)
        val list = runCatching { parseTodos(plain.getString("todos", "[]").orEmpty(), Gson()) }
            .getOrDefault(emptyList())
        return groupTodos(list, System.currentTimeMillis())
    }

    fun load(context: Context, now: LocalDateTime = LocalDateTime.now()): WidgetData {
        val cache = context.getSharedPreferences("gdust_cache", Context.MODE_PRIVATE)
        val power = context.getSharedPreferences("gdust_power", Context.MODE_PRIVATE)
        val store = Store(context)
        // 与主屏共用 legacy 过滤；读取失败不能回退到未经校验的原始缓存。
        val blocks = runCatching { store.loadBlocks().orEmpty() }.getOrDefault(emptyList())
        val termStart = sequenceOf(runCatching { store.loadCalendar()?.startDate }.getOrNull(), cache.getString("term_start", null))
            .mapNotNull { ScheduleTimes.parseDate(it) }.firstOrNull()
        val valid = blocks.filter {
            it.startSection in 1..ScheduleTimes.TOTAL_SECTIONS &&
                it.endSection in it.startSection..ScheduleTimes.TOTAL_SECTIONS &&
                ScheduleTimes.lessonDate(it, termStart) != null
        }.distinct()
        val cachedArray = runCatching {
            JsonParser.parseString(cache.getString("blocks", null)).takeIf { it.isJsonArray }?.asJsonArray
        }.getOrNull()
        val imported = (store.isIndependentSchedule || (store.scheduleSource == "zf_term" && cachedArray != null &&
            cachedArray.size() == blocks.size)) && valid.size == blocks.distinct().size
        val selection = selectSchedule(valid, termStart, imported, now)
        val nowMillis = now.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val lastSyncAt = store.selectedIndependentSchedule?.savedAt ?: cache.getLong("last_sync_at", 0L)
        val bound = power.getString("account", "").orEmpty().isNotBlank() &&
            power.getInt("code", 0) > 0 && power.getString("verify", "").orEmpty().isNotBlank()
        // 校验缓存所属绑定，换宿舍后不能显示旧宿舍的电量。
        val reading = runCatching { PowerWorker.cachedReading(context) }.getOrNull()
        val sampleAt = reading?.updatedAt ?: 0L
        return WidgetData(
            week = selection.week,
            scope = selection.scope,
            scopeDate = selection.date,
            lessons = selection.lessons,
            odd = reading?.detail?.odd,
            roomName = power.getString("name", null).orEmpty(),
            warnTh = cache.getFloat("warn_th", 20f),
            lastSampleAt = sampleAt,
            termStart = termStart,
            now = now,
            scheduleLastSyncAt = lastSyncAt,
            powerBound = bound,
            scheduleImported = imported,
            scheduleStale = imported && lastSyncAt > 0L && nowMillis - lastSyncAt > 24L * 60 * 60 * 1000,
            hasScheduleCache = imported || valid.isNotEmpty(),
            powerStale = reading != null && nowMillis - sampleAt > 24L * 60 * 60 * 1000,
        )
    }

    data class ScheduleSelection(
        val week: Int?,
        val scope: WidgetData.Scope,
        val date: LocalDate,
        val lessons: List<CourseBlock>,
    )

    /** 课后预览明天；周快照缺少明日数据时，不据此断言明日无课。 */
    fun selectSchedule(
        blocks: List<CourseBlock>,
        termStart: LocalDate?,
        imported: Boolean,
        now: LocalDateTime,
    ): ScheduleSelection {
        val today = now.toLocalDate()
        fun selection(date: LocalDate, scope: WidgetData.Scope, lessons: List<CourseBlock> = emptyList()) =
            ScheduleSelection(
                termStart?.takeIf { !date.isBefore(it) }?.let { ScheduleTimes.weekAt(it, date) },
                scope, date, lessons,
            )
        if (blocks.isEmpty() && !imported) return selection(today, WidgetData.Scope.NO_DATA)
        val todayAll = blocks.filter { ScheduleTimes.lessonDate(it, termStart) == today }.sortedBy { it.startSection }
        val remaining = todayAll.filter { !ScheduleTimes.isPast(it, termStart, now) }
        if (remaining.isNotEmpty()) {
            val scope = if (remaining.size == todayAll.size) WidgetData.Scope.TODAY_ALL else WidgetData.Scope.TODAY_REST
            return selection(today, scope, remaining)
        }
        val tomorrow = today.plusDays(1)
        val tomorrowLessons = blocks.filter { ScheduleTimes.lessonDate(it, termStart) == tomorrow }.sortedBy { it.startSection }
        if (tomorrowLessons.isNotEmpty()) return selection(tomorrow, WidgetData.Scope.TOMORROW, tomorrowLessons)
        if (!imported) return selection(today, WidgetData.Scope.INCOMPLETE)
        return if (todayAll.isNotEmpty() || now.hour >= 18) selection(tomorrow, WidgetData.Scope.TOMORROW_EMPTY)
        else selection(today, WidgetData.Scope.NO_LESSONS)
    }
}
