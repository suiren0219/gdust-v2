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

/** 课程卡色板：Nexio 官方 11 色（Course.kt courseColors），按课名稳定取色，同名同色。 */
object CourseColors {
    private val palette = listOf(
        0xFF4CAF50, 0xFF2196F3, 0xFFFF9800, 0xFFF44336, 0xFFE6B422,
        0xFFE91E63, 0xFF00BCD4, 0xFF3F51B5, 0xFFAB47BC, 0xFF009688, 0xFF673AB7,
    )

    fun tint(name: String): androidx.compose.ui.graphics.Color {
        val idx = (name.sumOf { it.code } % palette.size + palette.size) % palette.size
        return androidx.compose.ui.graphics.Color(palette[idx])
    }

    /** 格子背景：tint 低透明度（Nexio 规则：暗 0.13 / 亮 0.15），叠在页面底色上。 */
    fun bg(name: String, isDark: Boolean): androidx.compose.ui.graphics.Color =
        tint(name).copy(alpha = if (isDark) 0.13f else 0.15f)

    /** 兼容旧 Long 签名。 */
    fun bg(name: String): Long {
        val c = bg(name, isDark = false).value.toLong() and 0xFFFFFFFFL
        return c or 0xFF000000L
    }

    /** 深色模式低饱和版（保留给非 Nexio 路径）。 */
    fun bgDark(name: String): Long {
        val c = bg(name)
        return 0xFF000000 or ((c and 0xFF) / 2)
    }
}
