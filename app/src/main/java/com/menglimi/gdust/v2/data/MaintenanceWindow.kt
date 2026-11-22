package com.menglimi.gdust.v2.data

import java.time.LocalTime
import java.time.format.DateTimeFormatter

/** 教务维护时段仅是用户设置的参考提醒，不能据此判断学校是否停服。 */
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

    /** start == end 视为未配置（不提示）；跨午夜（start > end）按两段判断。 */
    fun isWithin(now: LocalTime, startMin: Int, endMin: Int): Boolean {
        if (startMin !in 0..1439 || endMin !in 0..1439 || startMin == endMin) return false
        val minute = now.hour * 60 + now.minute
        return if (startMin < endMin) minute in startMin until endMin
        else minute >= startMin || minute < endMin
    }

    /** 仅供实际教务读取失败时使用；先展示 [cause]，再追加非确定性的时段提醒。 */
    fun notice(now: LocalTime, startMin: Int, endMin: Int, cause: String?): String? {
        if (!isWithin(now, startMin, endMin)) return null
        val text = "当前处于设置的维护提醒时段（${format(startMin)}–${format(endMin)}），教务可能正在维护。" +
            "该时段仅供参考，不代表教务停服，可继续重试或换入口。"
        return if (cause.isNullOrBlank()) text else "$cause\n\n$text"
    }
}
