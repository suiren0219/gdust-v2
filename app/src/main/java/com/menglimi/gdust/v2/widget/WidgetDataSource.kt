package com.menglimi.gdust.v2.widget

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.menglimi.gdust.v2.data.CalendarInfo
import com.menglimi.gdust.v2.data.CourseBlock
import com.menglimi.gdust.v2.ui.schedule.ScheduleTimes
import java.time.Instant
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

/** 小组件数据快照：全部来自本机缓存，不打网络。 */
data class WidgetData(
    val week: Int,
    /** today = 今天还没下课的课；tomorrow = 今天课程已结束/无课时回退显示明天。 */
    val scope: Scope,
    val lessons: List<CourseBlock>,
    val odd: Double?,
    val roomName: String,
    val snapshotAt: LocalDateTime,
    val sourceLabel: String,
    val powerSampleAt: LocalDateTime?,
    val ongoingLessons: Set<CourseBlock>,
) {
    enum class Scope { TODAY_REST, TODAY_ALL, TOMORROW, EMPTY }

    fun title(): String = when (scope) {
        Scope.TODAY_REST -> "今天剩余" + weekLabel()
        Scope.TODAY_ALL -> "今天" + weekLabel()
        Scope.TOMORROW -> "明天 · ${ScheduleTimes.dayNames[snapshotAt.toLocalDate().plusDays(1).dayOfWeek.value - 1]}"
        Scope.EMPTY -> "课表待同步"
    }

    private fun weekLabel(): String = if (week > 0) " · 第 $week 周" else ""

    fun nextTitle(): String = when {
        lessons.isEmpty() -> if (scope == Scope.EMPTY) "课表待同步" else "明天 · 暂无缓存课程"
        lessons.first() in ongoingLessons -> "正在上课"
        scope == Scope.TOMORROW -> "下一节 · 明天"
        else -> "下一节 · 今天"
    }

    fun emptyTitle(): String = if (scope == Scope.EMPTY) "暂无可用课表" else "明天暂无缓存课程"

    fun powerSourceLabel(): String {
        if (odd == null) return "暂无电量缓存 · 打开应用更新"
        val sampled = powerSampleAt ?: return "本机缓存 · 采样时间未知"
        val pattern = when {
            sampled.toLocalDate() == snapshotAt.toLocalDate() -> "'今天' HH:mm"
            sampled.year == snapshotAt.year -> "M/d HH:mm"
            else -> "yyyy/M/d"
        }
        return "采样于 ${sampled.format(DateTimeFormatter.ofPattern(pattern))}"
    }
}

internal object WidgetDataSource {
    private val scheduleKeys = setOf("blocks", "calendar", "term_start", "schedule_source")
    private val powerKeys = setOf("prev_odd", "last_sample", "name")

    /** Glance 活跃会话的 updateAll 不会重跑 provideGlance，组合期间直接观察缓存。 */
    fun observe(context: Context): Flow<WidgetData> = callbackFlow {
        val cache = context.getSharedPreferences("gdust_cache", Context.MODE_PRIVATE)
        val power = context.getSharedPreferences("gdust_power", Context.MODE_PRIVATE)
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { prefs, key ->
            if (key == null || (prefs === cache && key in scheduleKeys) || (prefs === power && key in powerKeys)) {
                trySend(Unit)
            }
        }
        cache.registerOnSharedPreferenceChangeListener(listener)
        power.registerOnSharedPreferenceChangeListener(listener)
        // 注册后再读，避免首次读取与监听之间丢失更新。
        trySend(Unit)
        awaitClose {
            cache.unregisterOnSharedPreferenceChangeListener(listener)
            power.unregisterOnSharedPreferenceChangeListener(listener)
        }
    }.conflate().map { load(context) }.flowOn(Dispatchers.IO)

    fun load(context: Context): WidgetData {
        val cache = context.getSharedPreferences("gdust_cache", Context.MODE_PRIVATE)
        val power = context.getSharedPreferences("gdust_power", Context.MODE_PRIVATE)
        val gson = Gson()
        // JSON null / 损坏的缓存不能让三个小组件一起崩溃。
        val blocks = runCatching {
            gson.fromJson<List<CourseBlock?>>(
                cache.getString("blocks", "[]"),
                object : TypeToken<List<CourseBlock?>>() {}.type,
            ).orEmpty().filterNotNull()
        }.getOrDefault(emptyList())
        val cal = runCatching {
            gson.fromJson(cache.getString("calendar", null), CalendarInfo::class.java)
        }.getOrNull()
        val zone = ZoneId.systemDefault()
        val now = LocalDateTime.now(zone)
        val sampleAt = power.getLong("last_sample", 0L).takeIf { it > 0L }?.let {
            LocalDateTime.ofInstant(Instant.ofEpochMilli(it), zone)
        }
        return snapshot(
            blocks = blocks,
            calendar = cal,
            termStart = cache.getString("term_start", null),
            source = cache.getString("schedule_source", "legacy").orEmpty(),
            now = now,
            odd = power.getFloat("prev_odd", -1f).toDouble(),
            roomName = power.getString("name", null).orEmpty(),
            powerSampleAt = sampleAt,
        )
    }

    /** 可注入时间的纯快照计算；课程日期与 App 使用同一套作息规则。 */
    internal fun snapshot(
        blocks: List<CourseBlock>,
        calendar: CalendarInfo?,
        termStart: String?,
        source: String,
        now: LocalDateTime,
        odd: Double? = null,
        roomName: String = "",
        powerSampleAt: LocalDateTime? = null,
    ): WidgetData {
        val start = ScheduleTimes.parseDate(termStart) ?: ScheduleTimes.parseDate(calendar?.startDate)
        val today = now.toLocalDate()
        val week = start?.let { ScheduleTimes.weekAt(it, today) } ?: 0
        val label = when (source) {
            "zf_term" -> "教务课表 · 本机缓存"
            "portal_week" -> "门户周课表 · 本机缓存"
            else -> "课表 · 本机缓存"
        }
        // 旧缓存曾复制单周课程，不能仅凭 week/day 把复制条目当作真实排课。
        val legacyStart = ScheduleTimes.parseDate(calendar?.startDate)
        val datedLessons = blocks.mapNotNull { block ->
            runCatching {
                if (block.name.isBlank() || block.startSection < 1 || block.endSection < block.startSection) {
                    return@runCatching null
                }
                val date = ScheduleTimes.lessonDate(block, start) ?: return@runCatching null
                if (source == "legacy" || source.isBlank()) {
                    if (block.date.isBlank() || legacyStart == null || date.dayOfWeek.value != block.day ||
                        ScheduleTimes.weekAt(legacyStart, date) != block.week
                    ) return@runCatching null
                }
                block to date
            }.getOrNull()
        }
        val allToday = datedLessons.filter { it.second == today }.map { it.first }
        // 下课时刻起移除；未知节次保留原始节次，不编造时刻。
        val restToday = allToday.filter { block ->
            val end = ScheduleTimes.end(block.endSection)
            end.isEmpty() || now.toLocalTime().isBefore(LocalTime.parse(end))
        }
        val tomorrow = today.plusDays(1)
        val scope = when {
            datedLessons.isEmpty() -> WidgetData.Scope.EMPTY
            restToday.isEmpty() -> WidgetData.Scope.TOMORROW
            restToday.size == allToday.size -> WidgetData.Scope.TODAY_ALL
            else -> WidgetData.Scope.TODAY_REST
        }
        val lessons = (if (scope == WidgetData.Scope.TOMORROW) {
            datedLessons.filter { it.second == tomorrow }.map { it.first }
        } else restToday).sortedBy { it.startSection }
        val displayDate = if (scope == WidgetData.Scope.TOMORROW) tomorrow else today
        return WidgetData(
            week = start?.let { ScheduleTimes.weekAt(it, displayDate) } ?: week,
            scope = scope,
            lessons = lessons,
            odd = odd?.takeIf { it.isFinite() && it >= 0 },
            roomName = roomName,
            snapshotAt = now,
            sourceLabel = label,
            powerSampleAt = powerSampleAt,
            ongoingLessons = lessons.filter { ScheduleTimes.isOngoing(it, start, now) }.toSet(),
        )
    }
}
