package com.menglimi.gdust.v2.ui.schedule

import com.menglimi.gdust.v2.data.CourseBlock

/** 广科作息（第 1 节 08:30 起，共 10 节）。 */
object ScheduleTimes {
    val slots = listOf(
        "08:30" to "09:15",
        "09:20" to "10:05",
        "10:25" to "11:10",
        "11:15" to "12:00",
        "14:40" to "15:25",
        "15:30" to "16:15",
        "16:30" to "17:15",
        "17:20" to "18:05",
        "19:30" to "20:15",
        "20:20" to "21:05",
    )
    const val TOTAL_SECTIONS = 12       // 刻度展示 12 行，广科实际 10 节
    val dayNames = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")

    fun start(s: Int) = slots.getOrNull(s - 1)?.first ?: ""
    fun end(s: Int) = slots.getOrNull(s - 1)?.second ?: ""

    /** 当前节次（在上课时间内），无则 null。 */
    fun currentSection(now: java.time.LocalTime): Int? =
        slots.indexOfFirst { (a, b) ->
            !now.isBefore(java.time.LocalTime.parse(a)) && !now.isAfter(java.time.LocalTime.parse(b))
        }.takeIf { it >= 0 }?.plus(1)
}

/** 课程卡色板（马卡龙，按课名稳定取色）。 */
object CourseColors {
    private val palette = listOf(
        0xFFFDE7C7, 0xFFDCE9FB, 0xFFF9DCDC, 0xFFE2F0DC,
        0xFFEBDDF2, 0xFFDFF2F1, 0xFFF3E8D3, 0xFFE8E4F5,
    )

    fun bg(name: String): Long {
        val idx = (name.sumOf { it.code } % palette.size + palette.size) % palette.size
        return palette[idx]
    }

    /** 深色模式下卡片用同色系低饱和版本。 */
    fun bgDark(name: String): Long {
        val c = bg(name)
        return 0xFF000000 or ((c and 0xFF) / 2)
    }
}
