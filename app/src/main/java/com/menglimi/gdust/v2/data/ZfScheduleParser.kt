package com.menglimi.gdust.v2.data

import com.google.gson.JsonElement
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeParseException

object ZfScheduleParser {
    fun parse(json: String, calendar: CalendarInfo): List<CourseBlock> {
        require(calendar.allWeek in 1..60) { "学期总周数必须在 1..60 之间" }
        require(calendar.week in 1..calendar.allWeek) { "当前周必须在 1..${calendar.allWeek} 之间" }
        val start = try {
            LocalDate.parse(calendar.startDate)
        } catch (e: DateTimeParseException) {
            throw IllegalArgumentException("学期开始日期无效：${calendar.startDate}", e)
        }
        require(start.dayOfWeek == DayOfWeek.MONDAY) { "学期开始日期必须是周一：${calendar.startDate}" }

        val root = try {
            JsonParser.parseString(json)
        } catch (e: RuntimeException) {
            throw IllegalArgumentException("课表 JSON 格式无效", e)
        }
        require(root.isJsonObject) { "课表 JSON 顶层必须是对象" }
        val list = root.asJsonObject.get("kbList")
        require(list != null && list !is JsonNull && list.isJsonArray) { "课表缺少有效的 kbList" }
        require(list.asJsonArray.size() > 0) { "kbList 为空，无课" }

        val result = mutableListOf<CourseBlock>()
        list.asJsonArray.forEachIndexed { index, element ->
            val label = "第 ${index + 1} 条课程"
            require(element.isJsonObject) { "$label 格式无效，必须是对象" }
            val item = element.asJsonObject
            val name = requiredText(item, "kcmc", label)
            val day = requiredInt(item, "xqj", label)
            require(day in 1..7) { "$label 的星期 xqj 必须在 1..7 之间" }
            val (first, last) = parseSections(requiredText(item, "jcs", label), label)
            val weeksText = requiredText(item, "zcd", label)
            val weeks = try {
                parseWeeks(weeksText, calendar.allWeek)
            } catch (e: IllegalArgumentException) {
                throw IllegalArgumentException("$label 的周次 zcd 无效：${e.message}", e)
            }
            val teacher = optionalText(item, "xm")
            val room = optionalText(item, "cdmc")
            weeks.forEach { week ->
                result += CourseBlock(
                    week = week,
                    day = day,
                    startSection = first,
                    endSection = last,
                    name = name,
                    room = room,
                    teacher = teacher,
                    date = start.plusDays((week - 1L) * 7 + day - 1L).toString(),
                    weekParity = 0,
                )
            }
        }
        return result.distinct()
    }

    fun parseWeeks(value: String, totalWeeks: Int): List<Int> {
        require(totalWeeks in 1..60) { "学期总周数必须在 1..60 之间" }
        val normalized = value.trim()
            .replace('，', ',')
            .replace('（', '(')
            .replace('）', ')')
            .replace("周次", "周")
            .replace("周", "")
            .replace(Regex("\\s+"), "")
        require(normalized.isNotEmpty()) { "周次为空" }

        val weeks = sortedSetOf<Int>()
        normalized.split(',').forEach { part ->
            require(part.isNotEmpty()) { "周次中存在空项" }
            val match = Regex("^(\\d+)(?:-(\\d+))?(?:\\((单|双)\\))?$").matchEntire(part)
                ?: throw IllegalArgumentException("无法识别周次项“$part”")
            val first = match.groupValues[1].toIntOrNull()
                ?: throw IllegalArgumentException("周次数字无效：$part")
            val last = match.groupValues[2].takeIf { it.isNotEmpty() }?.toIntOrNull() ?: first
            require(first <= last) { "周次范围起点大于终点：$part" }
            require(first >= 1 && last <= totalWeeks) { "周次 $first-$last 超出学期范围 1..$totalWeeks" }
            val parity = match.groupValues[3]
            (first..last).filter { parity.isEmpty() || (parity == "单") == (it % 2 == 1) }.forEach(weeks::add)
        }
        require(weeks.isNotEmpty()) { "周次规则没有匹配到学期内周次" }
        return weeks.toList()
    }

    private fun parseSections(value: String, label: String): Pair<Int, Int> {
        val normalized = value.trim().replace('－', '-').replace('—', '-').replace("节", "")
        val match = Regex("^(\\d+)(?:-(\\d+))?$").matchEntire(normalized)
            ?: throw IllegalArgumentException("$label 的节次 jcs 无效：$value")
        val first = match.groupValues[1].toIntOrNull()
            ?: throw IllegalArgumentException("$label 的节次 jcs 无效：$value")
        val last = match.groupValues[2].takeIf { it.isNotEmpty() }?.toIntOrNull() ?: first
        require(first >= 1 && last >= first) { "$label 的节次范围无效：$value" }
        return first to last
    }

    private fun requiredText(item: JsonObject, key: String, label: String): String {
        val value = item.get(key)
        require(value != null && value !is JsonNull && value.isJsonPrimitive && value.asJsonPrimitive.isString) {
            "$label 缺少必填字段 $key"
        }
        return value.asString.trim().also { require(it.isNotEmpty()) { "$label 的必填字段 $key 为空" } }
    }

    private fun requiredInt(item: JsonObject, key: String, label: String): Int {
        val value: JsonElement? = item.get(key)
        require(value != null && value !is JsonNull && value.isJsonPrimitive) {
            "$label 缺少有效数字字段 $key"
        }
        val primitive = value.asJsonPrimitive
        val text = when {
            primitive.isNumber -> primitive.asString
            primitive.isString -> primitive.asString.trim()
            else -> ""
        }
        return text.toIntOrNull() ?: throw IllegalArgumentException("$label 的字段 $key 无效")
    }

    private fun optionalText(item: JsonObject, key: String): String {
        val value = item.get(key) ?: return ""
        if (value is JsonNull || !value.isJsonPrimitive || !value.asJsonPrimitive.isString) return ""
        return value.asString.trim()
    }
}
