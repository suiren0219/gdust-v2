package com.menglimi.gdust.v2.data

import org.jsoup.Jsoup
import java.time.LocalDate
import java.time.MonthDay

/** 只持久化月日，不保存出生年份或由身份证推算年龄。 */
internal object Birthday {
    fun parseDate(value: String, today: LocalDate = LocalDate.now()): MonthDay? {
        val text = value.trim()
        val normalized = when {
            text.matches(Regex("[0-9]{4}[-/][0-9]{1,2}[-/][0-9]{1,2}")) -> text.replace('/', '-')
            text.matches(Regex("[0-9]{4}年[0-9]{1,2}月[0-9]{1,2}日")) -> text.replace('年', '-').replace('月', '-').removeSuffix("日")
            else -> return null
        }
        val parts = normalized.split('-').map { it.toIntOrNull() ?: return null }
        val date = runCatching { LocalDate.of(parts[0], parts[1], parts[2]) }.getOrNull() ?: return null
        if (date.year < 1900 || date.isAfter(today)) return null
        return MonthDay.from(date)
    }

    fun saved(value: String): MonthDay? = runCatching { MonthDay.parse(value) }.getOrNull()

    /** 闰日生日在平年按二月最后一天庆祝，不影响原始保存月日。 */
    fun isToday(monthDay: MonthDay, today: LocalDate): Boolean = monthDay.atYear(today.year) == today

    fun shouldShow(value: String, lastShown: String, today: LocalDate): Boolean =
        saved(value)?.let { isToday(it, today) && lastShown != today.toString() } == true

    /** 只识别显式出生日期项；页面中的开学日、更新时间和其他日期不作为生日。 */
    fun fromTeachingHtml(html: String, expectedAccount: String, today: LocalDate = LocalDate.now()): MonthDay? {
        val doc = Jsoup.parse(html)
        if (doc.select("input[type=password]").isNotEmpty()) return null
        val accountFields = doc.select("input#xh, input[name=xh], input#studentId, input[name=studentId]")
            .map { it.attr("value").trim() }.filter { it.isNotBlank() }.distinct()
        if (accountFields.size > 1 || (expectedAccount.isNotBlank() && accountFields.any { it != expectedAccount })) return null
        val candidates = mutableListOf<String>()
        doc.select("input#csrq, input[name=csrq]").forEach { candidates += it.attr("value").trim() }
        for (label in doc.select("label, th, td, dt")) {
            if (label.text().trim().trimEnd(':', '：').trim() != "出生日期") continue
            val forId = label.attr("for")
            if (forId.isNotBlank()) doc.getElementById(forId)?.let { field ->
                candidates += if (field.tagName() == "input") field.attr("value") else field.text()
            }
            label.nextElementSibling()?.let { cell ->
                candidates += if (cell.tagName() == "input") cell.attr("value") else cell.text()
                cell.select("input[value]").forEach { candidates += it.attr("value") }
            }
        }
        val nonempty = candidates.map(String::trim).filter(String::isNotBlank).distinct()
        if (nonempty.isEmpty()) return null
        val parsed = nonempty.map { parseDate(it, today) ?: return null }.distinct()
        return parsed.singleOrNull()
    }
}
