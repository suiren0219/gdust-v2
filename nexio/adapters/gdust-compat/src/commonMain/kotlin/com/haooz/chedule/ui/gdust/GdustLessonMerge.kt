package com.haooz.chedule.ui.gdust

// Extracted unchanged from gdust-app@68a638da6dc69f06be3782c0835b116a69a87832 GdustSyncScreen.kt.
internal fun mergeConsecutiveLessons(lessons: List<GdustCourseData>): List<GdustCourseData> {
    val result = mutableListOf<GdustCourseData>()
    for ((_, dayLessons) in lessons.groupBy { it.dayOfWeek }) {
        var cur: GdustCourseData? = null
        for (l in dayLessons.sortedBy { it.startSection }) {
            val last = cur
            val canMerge = last != null &&
                last.name == l.name &&
                last.classroom == l.classroom &&
                last.teacher == l.teacher &&
                last.weeks == l.weeks &&
                last.endSection + 1 == l.startSection
            if (canMerge) {
                // 取 l 的末节：门户若返回多节记录（start..end 跨节）也不会截断
                cur = last!!.copy(endSection = l.endSection)
            } else {
                last?.let { result.add(it) }
                cur = l
            }
        }
        cur?.let { result.add(it) }
    }
    return result
}
