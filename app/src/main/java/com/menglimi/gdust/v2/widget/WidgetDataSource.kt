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
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

/** 小组件数据快照：全部来自本机缓存，不打网络；标题与摘要口径见 [WidgetPresentation]。 */
data class WidgetData(
    /** 展示日期所在周次；开学前/无校历时为 null，不硬编第 1 周。 */
    val week: Int?,
    val scope: Scope,
    /** 当前展示日期（今天或明天），标题与星期都取它。 */
    val scopeDate: LocalDate,
    /** 排好序的课程：TODAY_* 为今天还没结束的课，TOMORROW 为明天全天。 */
    val lessons: List<CourseBlock>,
    val odd: Double?,
    val roomName: String,
    /** 低电量阈值（gdust_cache "warn_th"，默认 20 度），用于小部件危险色。 */
    val warnTh: Float = 20f,
    /** 最近一次电量采样时间（epoch ms），0 = 从未采样。 */
    val lastSampleAt: Long = 0L,
    /** 学期起点（可能为 null：无校历且未设置开学日）；进行中判定用。 */
    val termStart: LocalDate? = null,
    /** 本次加载的时间快照，UI 全部沿用，跨午夜不读第二次时钟。 */
    val now: LocalDateTime = LocalDateTime.now(),
) {
    enum class Scope { TODAY_REST, TODAY_ALL, TOMORROW, NO_LESSONS, NO_DATA }
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

    /** 一次渲染只取一个时间快照：标题、筛选与进行中判定都用同一 now，跨午夜不读第二次时钟。 */
    fun load(context: Context, now: LocalDateTime = LocalDateTime.now()): WidgetData {
        val cache = context.getSharedPreferences("gdust_cache", Context.MODE_PRIVATE)
        val power = context.getSharedPreferences("gdust_power", Context.MODE_PRIVATE)
        val gson = Gson()
        val raw = runCatching {
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
        val lastSampleAt = power.getLong("last_sample", 0L)

        // 开学前 / 无校历不算第 1 周；term_start 非法回退有效校历日期
        val termStart = sequenceOf(
            cache.getString("term_start", null)?.takeIf { it.isNotBlank() },
            cal?.startDate,
        ).mapNotNull { it?.let(safeDate) }.firstOrNull()
        val today = now.toLocalDate()
        val week: Int? = termStart?.takeIf { !today.isBefore(it) }?.let { ScheduleTimes.weekAt(it, today) }

        // 排除非法节次与非法显式日期；显式日期优先于 week/day（门户口径）
        val valid = raw.filter { it.startSection >= 1 && ScheduleTimes.lessonDate(it, termStart) != null }
        // 拒绝 legacy 缓存的"同一节课复制到多周"：同日期同名同节次只保留最早的一条
        val blocks = dedupeCopies(valid)

        if (blocks.isEmpty()) {
            return WidgetData(week, WidgetData.Scope.NO_DATA, today, emptyList(), odd, roomName, warnTh, lastSampleAt, termStart, now)
        }

        val todayAll = blocks.filter { ScheduleTimes.lessonDate(it, termStart) == today }.sortedBy { it.startSection }
        // 上课区间 [开始, 结束)：下课整分钟起移除；未知节次 isPast=false 保留但不标进行中
        val restToday = todayAll.filter { !ScheduleTimes.isPast(it, termStart, now) }
        when {
            restToday.isNotEmpty() -> {
                val scope = if (restToday.size == todayAll.size) WidgetData.Scope.TODAY_ALL else WidgetData.Scope.TODAY_REST
                return WidgetData(week, scope, today, restToday, odd, roomName, warnTh, lastSampleAt, termStart, now)
            }
            todayAll.isNotEmpty() || blocks.any { ScheduleTimes.lessonDate(it, termStart) == today.plusDays(1) } -> {
                val tomorrow = today.plusDays(1)
                val tomorrowLessons = blocks.filter { ScheduleTimes.lessonDate(it, termStart) == tomorrow }
                    .sortedBy { it.startSection }
                if (tomorrowLessons.isNotEmpty()) {
                    val tomorrowWeek = termStart?.takeIf { !tomorrow.isBefore(it) }?.let { ScheduleTimes.weekAt(it, tomorrow) }
                    return WidgetData(tomorrowWeek, WidgetData.Scope.TOMORROW, tomorrow, tomorrowLessons, odd, roomName, warnTh, lastSampleAt, termStart, now)
                }
            }
        }
        return WidgetData(week, WidgetData.Scope.NO_LESSONS, today, emptyList(), odd, roomName, warnTh, lastSampleAt, termStart, now)
    }

    private val safeDate: (String) -> LocalDate? = { ScheduleTimes.parseDate(it) }

    /**
     * 同一显式日期 + 同日 + 同名 + 同节次出现多次 = legacy 的整段复制，只留一条。
     * 无显式日期的课不参与（不同周的同类课合法）；日期相同但 day 不同视为调课记录，保留。
     */
    private fun dedupeCopies(blocks: List<CourseBlock>): List<CourseBlock> {
        val seen = HashSet<String>()
        return blocks.mapNotNull { b ->
            if (b.date.isBlank()) return@mapNotNull b
            val key = "${b.date}|${b.day}|${b.name}|${b.startSection}|${b.endSection}"
            if (seen.add(key)) b else null
        }
    }
}
