// SPDX-License-Identifier: AGPL-3.0-only
// Schedule-only GDUST adaptation of the slot/detail model in NexioSchedule revision 28a99a3:
// ui-shared/src/portable/kotlin/com/haooz/chedule/ui/activities/classschedule/DayColumn.kt and
// ui-shared/src/commonMain/kotlin/com/haooz/chedule/ui/activities/classschedule/MainScheduleScreen.kt.
// Pure helpers retain actual imported weeks and section spans; they never expand week parity.
package com.menglimi.gdust.v2.ui.schedule

import com.menglimi.gdust.v2.data.CourseBlock

internal data class CellLesson(
    val name: String,
    val room: String,
    val teacher: String,
    val startSection: Int,
    val endSection: Int,
    val weeks: List<Int>,
)

private fun sameCell(a: CourseBlock, b: CourseBlock): Boolean =
    a.day == b.day && a.startSection <= b.endSection && a.endSection >= b.startSection

/** Input is a single day of a single week; adjacent, non-overlapping lessons stay separate. */
internal fun groupOverlappingLessons(blocks: List<CourseBlock>): List<List<CourseBlock>> {
    val groups = mutableListOf<MutableList<CourseBlock>>()
    for (block in blocks.sortedBy { it.startSection }) {
        val last = groups.lastOrNull()
        if (last != null && block.startSection <= last.maxOf { it.endSection }) last.add(block)
        else groups.add(mutableListOf(block))
    }
    return groups
}

/** Exact slots share a card, partial overlaps share the day width without changing any span. */
internal fun lessonLanes(group: List<CourseBlock>): List<List<List<CourseBlock>>> {
    val slots = group.groupBy { it.startSection to it.endSection }.values.sortedBy { it.first().startSection }
    val lanes = mutableListOf<MutableList<List<CourseBlock>>>()
    slots.forEach { slot ->
        val lane = lanes.firstOrNull { it.last().first().endSection < slot.first().startSection }
        if (lane == null) lanes.add(mutableListOf(slot)) else lane.add(slot)
    }
    return lanes
}

internal fun aggregateCellLessons(selected: CourseBlock, blocks: List<CourseBlock>): List<CellLesson> {
    // Include the whole connected overlap group in this week, then all actual weeks at those slots.
    val currentGroup = groupOverlappingLessons(blocks.filter { it.week == selected.week && it.day == selected.day })
        .firstOrNull { selected in it } ?: listOf(selected)
    return blocks.filter { candidate -> currentGroup.any { sameCell(it, candidate) } }
        .groupBy { listOf(it.name, it.room, it.teacher, it.startSection, it.endSection) }
        .values.map { group ->
            val first = group.first()
            CellLesson(first.name, first.room, first.teacher, first.startSection, first.endSection, group.map { it.week }.distinct().sorted())
        }.sortedWith(compareBy<CellLesson> { it.startSection }.thenBy { it.name })
}

internal fun formatScheduleWeeks(weeks: List<Int>, allWeeks: Int): String {
    val sorted = weeks.distinct().sorted()
    if (sorted.isEmpty()) return ""
    if (sorted == (1..allWeeks).toList()) return "每周"
    val ranges = mutableListOf<String>()
    var first = sorted.first()
    var last = first
    fun flush() { ranges += if (first == last) "$first 周" else "$first–$last 周" }
    sorted.drop(1).forEach { week ->
        if (week != last + 1) { flush(); first = week }
        last = week
    }
    flush()
    return ranges.joinToString("、")
}
