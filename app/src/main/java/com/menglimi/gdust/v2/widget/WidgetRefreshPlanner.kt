package com.menglimi.gdust.v2.widget

import com.menglimi.gdust.v2.data.CourseBlock
import com.menglimi.gdust.v2.ui.schedule.ScheduleTimes
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/** 只排下一次本机刷新；不联网、不申请精确闹钟。 */
internal object WidgetRefreshPlanner {
    fun nextRefreshAt(
        blocks: List<CourseBlock>,
        termStart: LocalDate?,
        now: LocalDateTime,
    ): LocalDateTime {
        val today = now.toLocalDate()
        val candidates = mutableListOf(today.plusDays(1).atStartOfDay(), today.atTime(18, 0))
        blocks.filter { ScheduleTimes.lessonDate(it, termStart) == today }.forEach { block ->
            if (block.startSection !in 1..ScheduleTimes.TOTAL_SECTIONS ||
                block.endSection !in block.startSection..ScheduleTimes.TOTAL_SECTIONS) return@forEach
            ScheduleTimes.segments(block.startSection, block.endSection).forEach { segment ->
                listOf(ScheduleTimes.start(segment.first), ScheduleTimes.end(segment.last)).forEach { value ->
                    runCatching { LocalTime.parse(value) }.getOrNull()?.let { candidates += today.atTime(it) }
                }
            }
        }
        return candidates.filter { it.isAfter(now) }.minOrNull() ?: today.plusDays(1).atStartOfDay()
    }
}
