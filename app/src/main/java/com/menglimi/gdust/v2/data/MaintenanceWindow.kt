package com.menglimi.gdust.v2.data

import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * 教务夜间关闭提醒：学校教务在夜间维护时段停止服务，同步失败时给出明确提示，
 * 而不是笼统的「网络失败」。学习通（超星）与电费查询不受学校教务关闭影响。
 */
object MaintenanceWindow {
    /** 默认 23:00–06:30；可在设置页修改，分钟数取 0..1439。 */
    const val DEFAULT_START_MIN: Int = 23 * 60
    const val DEFAULT_END_MIN: Int = 6 * 60 + 30

    private val HH_MM: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    fun format(minuteOfDay: Int): String = HH_MM.format(LocalTime.ofSecondOfDay((minuteOfDay.coerceIn(0, 1439)) * 60L))

    fun parse(text: String): Int? {
        val value = text.trim()
        val match = Regex("^(\\d{1,2}):(\\d{2})$").find(value) ?: return null
        val hour = match.groupValues[1].toInt()
        val minute = match.groupValues[2].toInt()
        if (hour !in 0..23 || minute !in 0..59) return null
        return hour * 60 + minute
    }

    /** start == end 表示全天关闭视为未配置（不提示）；跨午夜（start > end）按两段判断。 */
    fun isWithin(now: LocalTime, startMin: Int, endMin: Int): Boolean {
        if (startMin !in 0..1439 || endMin !in 0..1439 || startMin == endMin) return false
        val minute = now.hour * 60 + now.minute
        return if (startMin < endMin) minute in startMin until endMin
        else minute >= startMin || minute < endMin
    }

    /**
     * 处于关闭时段时返回给用户看的提示；[cause] 为原始失败原因，追加在提示之后。
     */
    fun notice(now: LocalTime, startMin: Int, endMin: Int, cause: String?): String? {
        if (!isWithin(now, startMin, endMin)) return null
        val text = "教务系统夜间关闭（约 ${format(startMin)}–${format(endMin)}）：学校教务此时段停止服务，" +
            "课表与成绩暂时无法同步。学习通与电量查询不受影响，请白天再试。"
        return if (cause.isNullOrBlank()) text else "$text\n\n具体错误：${cause.take(160)}"
    }
}
