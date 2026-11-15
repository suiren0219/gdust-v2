package com.menglimi.gdust.v2.work

import com.menglimi.gdust.v2.data.CourseBlock
import com.menglimi.gdust.v2.ui.schedule.ScheduleTimes
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/** 半开区间 [startMillis, endMillis)：下课时刻立即退出，不依赖课前通知是否发出。 */
internal data class ClassDndWindow(val startMillis: Long, val endMillis: Long)

internal data class ClassDndPlan(val active: ClassDndWindow?, val nextAtMillis: Long?)

/** 规划整个本机课表，跨午休/晚饭的连堂课分段；缺失有效日期或作息的课程不猜测。 */
internal fun classDndWindows(
    blocks: List<CourseBlock>,
    termStart: LocalDate?,
    zone: ZoneId = ZoneId.systemDefault(),
): List<ClassDndWindow> = blocks.flatMap { block ->
    val date = ScheduleTimes.lessonDate(block, termStart) ?: return@flatMap emptyList()
    if (block.startSection < 1 || block.endSection < block.startSection ||
        ScheduleTimes.start(block.startSection).isEmpty() || ScheduleTimes.end(block.endSection).isEmpty()
    ) return@flatMap emptyList()
    ScheduleTimes.segments(block.startSection, block.endSection).mapNotNull { sections ->
        val start = runCatching { LocalTime.parse(ScheduleTimes.start(sections.first)) }.getOrNull()
            ?: return@mapNotNull null
        val end = runCatching { LocalTime.parse(ScheduleTimes.end(sections.last)) }.getOrNull()
            ?: return@mapNotNull null
        if (!end.isAfter(start)) return@mapNotNull null
        ClassDndWindow(
            date.atTime(start).atZone(zone).toInstant().toEpochMilli(),
            date.atTime(end).atZone(zone).toInstant().toEpochMilli(),
        )
    }
}.distinct().sortedBy { it.startMillis }

/** 合并重叠/相接课程，第一门下课时不会误关仍在进行的另一门课。 */
internal fun mergeClassDndWindows(windows: List<ClassDndWindow>): List<ClassDndWindow> {
    val merged = mutableListOf<ClassDndWindow>()
    for (window in windows.filter { it.endMillis > it.startMillis }.sortedBy { it.startMillis }) {
        val previous = merged.lastOrNull()
        if (previous != null && window.startMillis <= previous.endMillis) {
            merged[merged.lastIndex] = previous.copy(endMillis = maxOf(previous.endMillis, window.endMillis))
        } else {
            merged += window
        }
    }
    return merged
}

/** 手动请求必须仍属于本机课表；清课表/换账号后不会留下一个独立的旧课时。 */
internal fun planClassDnd(
    windows: List<ClassDndWindow>,
    automatic: Boolean,
    manual: ClassDndWindow?,
    skippedUntil: Long,
    nowMillis: Long,
): ClassDndPlan {
    val selected = if (automatic) windows else windows.filter { it == manual }
    val effective = mergeClassDndWindows(selected).mapNotNull { window ->
        val start = maxOf(window.startMillis, skippedUntil)
        if (start < window.endMillis) window.copy(startMillis = start) else null
    }
    val active = effective.firstOrNull { nowMillis >= it.startMillis && nowMillis < it.endMillis }
    return ClassDndPlan(active, active?.endMillis ?: effective.firstOrNull { it.startMillis > nowMillis }?.startMillis)
}
