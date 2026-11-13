package com.menglimi.gdust.v2.work

import com.menglimi.gdust.v2.data.CourseBlock
import com.menglimi.gdust.v2.ui.schedule.ScheduleTimes
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/** 提醒类型：课前 / 快下课 / 次日汇总。 */
internal enum class ReminderKind { BEFORE_CLASS, IN_CLASS, NEXT_DAY }

/**
 * 一个「计划中的提醒时刻」。纯数据，不依赖 Android Context，便于单测。
 * [date] 对 NEXT_DAY 表示被汇总的那一天；[block] 对 NEXT_DAY 为 null。
 */
internal data class ReminderEvent(
    val key: String,
    val atMillis: Long,
    val kind: ReminderKind,
    val date: LocalDate,
    val block: CourseBlock? = null,
    val nextDayBlocks: List<CourseBlock> = emptyList(),
    val startText: String = "",
    val endText: String = "",
    /** 该提醒所依附的自然日（发出通知的那天），用于生成兼容旧版的去重 key。 */
    val emitDate: LocalDate = date,
)

/** 提醒规则快照：全部来自本机 prefs / 校历，纯数据。 */
internal data class ReminderRules(
    val termStart: LocalDate?,
    val calendarWeek: Int,
    val leadMinutes: Int,
    val inClassEnabled: Boolean,
    val inClassLeadMinutes: Int,
    val nextDayEnabled: Boolean,
    val nextDayHour: Int,
)

/**
 * 按课表算出「今天 + 明天」两天的全部提醒时刻（含已过去的，由调用方过滤）。
 *
 * 精确定时取代旧版 15 分钟轮询：课前 = 上课时刻 - lead，课中 = 下课时刻 - inClassLead，
 * 次日汇总 = 当日 nextDayHour 整点。课程日期优先用服务端下发的 `CourseBlock.date`，
 * 缺失时才按「学期第一周周一 + week/day」回推，且用 floorDiv 正确处理学期开始前。
 */
internal fun planReminders(
    blocks: List<CourseBlock>,
    rules: ReminderRules,
    nowMillis: Long,
    zone: ZoneId = ZoneId.systemDefault(),
): List<ReminderEvent> {
    val now = Instant.ofEpochMilli(nowMillis).atZone(zone)
    val today = now.toLocalDate()

    fun weekOf(date: LocalDate): Int =
        rules.termStart?.let { ScheduleTimes.weekAt(it, date) } ?: rules.calendarWeek

    fun blocksOn(date: LocalDate): List<CourseBlock> = blocks.filter { b ->
        if (b.startSection < 1 || b.endSection < b.startSection) return@filter false
        if (b.date.isNotBlank()) {
            // 服务端下发日期时以它为准；无效日期直接丢弃，不回退到 week/day 编造
            ScheduleTimes.parseDate(b.date) == date
        } else {
            b.week == weekOf(date) && b.day == date.dayOfWeek.value
        }
    }.sortedWith(compareBy({ it.startSection }, { it.name }))

    fun atMillis(date: LocalDate, time: LocalTime): Long =
        date.atTime(time).atZone(zone).toInstant().toEpochMilli()

    val result = mutableListOf<ReminderEvent>()
    // 只规划今天与明天：闹钟最多指向明天，6 小时兜底也足够覆盖这个窗口
    for (offset in 0L..1L) {
        val date = today.plusDays(offset)
        for (b in blocksOn(date)) {
            val start = ScheduleTimes.start(b.startSection)
                .takeIf { it.isNotEmpty() }?.let { runCatching { LocalTime.parse(it) }.getOrNull() }
                ?: continue
            val end = ScheduleTimes.end(b.endSection)
                .takeIf { it.isNotEmpty() }?.let { runCatching { LocalTime.parse(it) }.getOrNull() }
                ?: continue
            result += ReminderEvent(
                key = "lesson-$date-${b.startSection}-${b.name}",
                atMillis = atMillis(date, start) - rules.leadMinutes * 60_000L,
                kind = ReminderKind.BEFORE_CLASS,
                date = date,
                block = b,
                startText = start.toString(),
                endText = end.toString(),
                emitDate = date,
            )
            if (rules.inClassEnabled) {
                result += ReminderEvent(
                    key = "inclass-$date-${b.startSection}-${b.name}",
                    atMillis = atMillis(date, end) - rules.inClassLeadMinutes * 60_000L,
                    kind = ReminderKind.IN_CLASS,
                    date = date,
                    block = b,
                    startText = start.toString(),
                    endText = end.toString(),
                    emitDate = date,
                )
            }
        }
        if (rules.nextDayEnabled) {
            val hour = rules.nextDayHour.coerceIn(0, 23)
            val tomorrow = date.plusDays(1)
            val lessons = blocksOn(tomorrow)
            if (lessons.isNotEmpty()) {
                result += ReminderEvent(
                    key = "nextday-$date",
                    atMillis = atMillis(date, LocalTime.of(hour, 0)),
                    kind = ReminderKind.NEXT_DAY,
                    date = tomorrow,
                    nextDayBlocks = lessons,
                    emitDate = date,
                )
            }
        }
    }
    return result.sortedBy { it.atMillis }
}
