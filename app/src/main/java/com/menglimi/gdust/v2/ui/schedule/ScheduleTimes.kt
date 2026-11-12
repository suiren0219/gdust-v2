// SPDX-License-Identifier: AGPL-3.0-only
package com.menglimi.gdust.v2.ui.schedule

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import com.menglimi.gdust.v2.data.CourseBlock
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/** 广科作息（第 1 节 08:30 起，共 10 节；额外节次保留刻度，不编造时间）。 */
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
    const val TOTAL_SECTIONS = 12
    val dayNames = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")
    private val times = slots.map { (start, end) -> LocalTime.parse(start) to LocalTime.parse(end) }

    fun start(s: Int) = slots.getOrNull(s - 1)?.first.orEmpty()
    fun end(s: Int) = slots.getOrNull(s - 1)?.second.orEmpty()

    /** 半开区间：下课整分钟起不再标记“进行中”。 */
    fun currentSection(now: LocalTime): Int? =
        times.indexOfFirst { (start, end) -> !now.isBefore(start) && now.isBefore(end) }
            .takeIf { it >= 0 }?.plus(1)

    fun sectionProgress(section: Int, now: LocalTime): Float? {
        val (start, end) = times.getOrNull(section - 1) ?: return null
        if (now.isBefore(start) || !now.isBefore(end)) return null
        return (now.toSecondOfDay() - start.toSecondOfDay()).toFloat() /
            (end.toSecondOfDay() - start.toSecondOfDay())
    }

    fun parseDate(value: String?): LocalDate? {
        val text = value?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        // Keep the date stated by the portal; an offset must not shift a class into another day.
        return runCatching {
            if (text.length == 10) LocalDate.parse(text)
            else {
                // The portal also emits SQL-style yyyy-MM-dd HH:mm:ss values.
                val iso = if (text.getOrNull(10) == ' ') text.replaceRange(10, 11, "T") else text
                LocalDate.from(DateTimeFormatter.ISO_DATE_TIME.parse(iso))
            }
        }.getOrNull()
    }

    fun dateAt(termStart: LocalDate?, week: Int, day: Int): LocalDate? =
        if (week > 0 && day in 1..7) termStart?.plusDays((week - 1L) * 7 + day - 1L) else null

    fun weekAt(termStart: LocalDate, date: LocalDate): Int =
        Math.floorDiv(ChronoUnit.DAYS.between(termStart, date), 7L).toInt() + 1

    /** 实际排课日期优先；只有未提供日期时才使用校历。 */
    fun lessonDate(block: CourseBlock, termStart: LocalDate?): LocalDate? =
        if (block.date.isBlank()) dateAt(termStart, block.week, block.day) else parseDate(block.date)

    fun isOngoing(block: CourseBlock, termStart: LocalDate?, now: LocalDateTime): Boolean {
        if (lessonDate(block, termStart) != now.toLocalDate()) return false
        if (times.getOrNull(block.startSection - 1) == null || times.getOrNull(block.endSection - 1) == null) return false
        val time = now.toLocalTime()
        return segments(block.startSection, block.endSection).any { segment ->
            val start = times[segment.first - 1].first
            val end = times[segment.last - 1].second
            !time.isBefore(start) && time.isBefore(end)
        }
    }

    fun isPast(block: CourseBlock, termStart: LocalDate?, now: LocalDateTime): Boolean {
        val date = lessonDate(block, termStart) ?: return false
        if (date != now.toLocalDate()) return date.isBefore(now.toLocalDate())
        val end = times.getOrNull(block.endSection - 1)?.second ?: return false
        return !now.toLocalTime().isBefore(end)
    }

    /** Adapted from Nexio DayColumn.kt: courses crossing break dividers are separate visual segments. */
    fun segments(start: Int, end: Int): List<IntRange> = buildList {
        if (start < 1 || end < start) return@buildList
        var first = start
        for (boundary in listOf(4, 8)) {
            if (first <= boundary && boundary < end) {
                add(first..boundary)
                first = boundary + 1
            }
        }
        if (first <= end) add(first..end)
    }
}

/**
 * Adapted from NexioSchedule revision 28a99a3 (AGPL-3.0):
 * core/src/commonMain/kotlin/com/haooz/chedule/data/model/Course.kt (11 course colors),
 * ui-shared/src/portable/kotlin/com/haooz/chedule/ui/activities/classschedule/CourseCard.kt,
 * and ui-shared/src/portable/kotlin/com/haooz/chedule/ui/theme/ScheduleColors.kt
 * (0.12 surface alpha, wallpaper tint adjustment, 0.74 monochrome text).
 * Course names supply stable colors because GDUST CourseBlock has no colorRes field.
 */
object CourseColors {
    private val palette = listOf(
        0xFF4CAF50, 0xFF2196F3, 0xFFFF9800, 0xFFF44336, 0xFFE6B422,
        0xFFE91E63, 0xFF00BCD4, 0xFF3F51B5, 0xFFAB47BC, 0xFF009688, 0xFF673AB7,
    )

    fun tint(name: String): Color {
        val index = Math.floorMod(name.sumOf { it.code }, palette.size)
        return Color(palette[index])
    }

    @Suppress("UNUSED_PARAMETER")
    fun bg(name: String, isDark: Boolean): Color = tint(name).copy(alpha = 0.12f)

    fun text(name: String, isDark: Boolean, onWallpaper: Boolean, monochrome: Boolean): Color {
        if (monochrome) return (if (isDark) Color.White else Color.Black).copy(alpha = 0.74f)
        val tint = tint(name)
        if (!onWallpaper) return tint
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(tint.toArgb(), hsv)
        hsv[1] = (hsv[1] * if (isDark) 0.5f else 0.84f).coerceIn(0f, 1f)
        hsv[2] = (hsv[2] + if (isDark) 0.4f else 0.5f).coerceIn(0f, 1f)
        return Color(android.graphics.Color.HSVToColor(hsv))
    }

    /** Nexio ScheduleColors.kt contrast fallback, including translucent preferred text. */
    fun readableText(preferred: Color, background: Color): Color {
        val backgroundLuminance = background.luminance()
        val foregroundLuminance = preferred.compositeOver(background).luminance()
        val contrast = (maxOf(backgroundLuminance, foregroundLuminance) + 0.05f) /
            (minOf(backgroundLuminance, foregroundLuminance) + 0.05f)
        if (contrast >= 4.5f) return preferred
        val blackContrast = (backgroundLuminance + 0.05f) / 0.05f
        val whiteContrast = 1.05f / (backgroundLuminance + 0.05f)
        return if (blackContrast >= whiteContrast) Color.Black else Color.White
    }

    /** 兼容旧 ARGB 签名；Compose Color.value 是打包色空间值，不能当作 ARGB 截断。 */
    fun bg(name: String): Long = tint(name).toArgb().toLong() and 0xFFFFFFFFL

    fun bgDark(name: String): Long {
        val color = bg(name)
        return 0xFF000000L or (((color shr 16 and 0xFF) / 2) shl 16) or
            (((color shr 8 and 0xFF) / 2) shl 8) or ((color and 0xFF) / 2)
    }
}
