package com.menglimi.gdust.v2.ui.screens

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.TemporalAdjusters

/** 更换年月时收拢到有效日期，避免 31 日和闰日落入不存在的日期。 */
internal object DateWheelSelection {
    fun parse(value: String): LocalDate? = runCatching { LocalDate.parse(value.trim()) }.getOrNull()

    fun changeMonth(date: LocalDate, year: Int = date.year, month: Int = date.monthValue): LocalDate {
        val target = YearMonth.of(year, month)
        return target.atDay(date.dayOfMonth.coerceAtMost(target.lengthOfMonth()))
    }

    fun monday(date: LocalDate): LocalDate = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

    fun validTermStart(value: String): Boolean =
        value.isBlank() || parse(value)?.dayOfWeek == DayOfWeek.MONDAY
}
