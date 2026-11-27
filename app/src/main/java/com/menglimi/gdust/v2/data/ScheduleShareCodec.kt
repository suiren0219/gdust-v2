package com.menglimi.gdust.v2.data

import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.menglimi.gdust.v2.ui.schedule.ScheduleTimes
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** Conservative Nexio-compatible file conversion; refuses data that cannot be represented faithfully. */
object ScheduleShareCodec {
    private val gson = Gson()
    private val periods = listOf("morning", "afternoon", "evening")
    private val sectionPeriod = (1..10).associateWith { section ->
        when (section) { in 1..4 -> "morning"; in 5..8 -> "afternoon"; else -> "evening" }
    }
    private val sectionRelative = (1..10).associateWith { section ->
        when (section) { in 1..4 -> section; in 5..8 -> section - 4; else -> section - 8 }
    }

    fun export(calendar: CalendarInfo?, blocks: List<CourseBlock>, name: String = "广科课表"): String {
        require(blocks.isNotEmpty()) { "当前没有课程，不能上传空课表" }
        require(name.isNotBlank() && name.length <= 128)
        val start = LocalDate.parse(calendar?.startDate ?: inferredStart(blocks))
        require(start.dayOfWeek.value == 1) { "开学日期不是周一，拒绝导出" }
        val totalWeeks = calendar?.allWeek ?: blocks.maxOf { it.week }
        require(totalWeeks in 1..60) { "广科最多支持 60 个学期周，不能导出为无损云端课表" }
        require(blocks.all { it.week in 1..totalWeeks && it.day in 1..7 && it.startSection in 1..10 && it.endSection in it.startSection..10 }) {
            "课程包含广科未支持的周次、星期或节次，拒绝导出"
        }
        require(blocks.all { block ->
            block.date.isNotBlank() && runCatching {
                LocalDate.parse(block.date) == start.plusDays((block.week - 1L) * 7 + block.day - 1L)
            }.getOrDefault(false)
        }) { "课程含单日调课或日期与校历不一致；Nexio 文件格式无法无损表达，拒绝上传" }
        val currentWeek = (calendar?.week ?: 1).coerceIn(1, totalWeeks)
        val counts = mapOf("morning" to 4, "afternoon" to 4, "evening" to 2)
        val timeMaps = periods.associateWith { period ->
            sectionPeriod.filterValues { it == period }.map { (section, _) ->
                val relative = sectionRelative.getValue(section)
                val slot = ScheduleTimes.slots.getOrNull(section - 1) ?: error("缺少第 $section 节的已验证作息时间")
                relative.toString() to "${slot.first}-${slot.second}"
            }.toMap()
        }
        val courses = JsonArray()
        blocks.groupBy { listOf(it.name, it.room, it.teacher, it.day, it.startSection, it.endSection) }
            .values.forEach { occurrences ->
                val first = occurrences.first()
                require(sectionPeriod[first.startSection] == sectionPeriod[first.endSection]) {
                    "跨早/午/晚时段连堂无法用 Nexio 课表格式无损表达，拒绝上传"
                }
                val weeks = occurrences.map { it.week }.distinct().sorted()
                require(weeks.isNotEmpty())
                val parity = when (first.weekParity) { 1 -> 1; 2 -> 2; else -> 0 }
                courses.add(JsonObject().apply {
                    addProperty("name", first.name); addProperty("classroom", first.room); addProperty("teacher", first.teacher)
                    addProperty("dayOfWeek", first.day)
                    // Nexio Course uses global section ids; time maps use period-relative ids.
                    addProperty("startSection", first.startSection); addProperty("endSection", first.endSection)
                    addProperty("isCustomTime", false); addProperty("customStartTime", ""); addProperty("customEndTime", "")
                    addProperty("startWeek", weeks.first()); addProperty("endWeek", weeks.last())
                    addProperty("weekType", parity)
                    add("selectedWeeks", JsonArray().apply { weeks.forEach(::add) })
                })
            }
        return gson.toJson(JsonObject().apply {
            addProperty("schedule_name", name)
            add("settings", JsonObject().apply {
                addProperty("class_start_time", start.toString()); addProperty("current_week", currentWeek)
                addProperty("total_weeks", totalWeeks)
                periods.forEach { addProperty("${it}_sections", counts.getValue(it)) }
                addProperty("smart_weekend", true); addProperty("show_non_current_week", true)
                add("teaching_week_reorganizations", JsonArray())
            })
            add("times", JsonObject().apply {
                periods.forEach { period -> add(period, JsonObject().apply {
                    timeMaps.getValue(period).forEach { (key, value) -> addProperty(key, value) }
                }) }
                add("section_names", JsonObject())
            })
            add("courses", courses); add("routines", JsonArray())
            add("holiday", JsonObject().apply { add("holiday_entries", JsonObject()) })
        })
    }

    fun inspect(document: String): ScheduleShareInfo = parse(document).let { ScheduleShareInfo(it.name, it.courseCount) }

    internal fun parse(document: String): ParsedSchedule {
        val root = McpJson.objectFrom(document, MAX_DOCUMENT_BYTES)
        val name = McpJson.text(root, "schedule_name", 128).trim().also { require(it.isNotEmpty()) }
        val settings = root.getAsJsonObject("settings") ?: error("课表设置缺失")
        val courses = root.getAsJsonArray("courses") ?: error("课程列表缺失")
        require(courses.size() in 1..MAX_COURSES) { "课程列表为空或数量超限" }
        val start = LocalDate.parse(McpJson.text(settings, "class_start_time", 10).replace('/', '-'))
        require(start.dayOfWeek.value == 1 && start.year in 1970..2100) { "开学日期必须为有效周一" }
        val totalWeeks = McpJson.int(settings, "total_weeks", 1..60)
        val times = root.getAsJsonObject("times") ?: error("作息时间表缺失")
        periods.forEach { period ->
            val sections = sectionPeriod.filterValues { it == period }.keys
            require(McpJson.int(settings, "${period}_sections", 0..10) == sections.size) { "仅支持广科 4+4+2 节作息" }
            val map = times.getAsJsonObject(period) ?: error("作息时间段缺失")
            require(map.keySet() == sections.map { sectionRelative.getValue(it).toString() }.toSet()) { "作息节次不完整" }
            sections.forEach { section ->
                val slot = ScheduleTimes.slots[section - 1]
                require(McpJson.text(map, sectionRelative.getValue(section).toString(), 11) == "${slot.first}-${slot.second}") {
                    "该课表作息不同于广科；当前无法无损导入，请保留 JSON 使用原客户端"
                }
            }
        }
        require(!root.has("cloud_extensions")) { "暂不支持云端单日例外" }
        for (owner in listOf(root, settings)) {
            val rules = owner["teaching_week_reorganizations"]
            if (rules != null) require(rules.isJsonArray && rules.asJsonArray.size() == 0) { "暂不支持教学周重组规则" }
        }
        root["routines"]?.let { require(it.isJsonArray && it.asJsonArray.size() == 0) { "暂不支持多套作息" } }
        root["holiday"]?.let { holiday ->
            require(holiday.isJsonObject)
            holiday.asJsonObject.getAsJsonObject("holiday_entries")?.entrySet()?.forEach { (_, value) ->
                val entries = if (value.isJsonPrimitive && value.asJsonPrimitive.isString)
                    McpJson.objectFrom("{\"items\":${value.asString}}")["items"] else value
                require(entries.isJsonArray && entries.asJsonArray.size() == 0) { "暂不支持节假日/调休日历，拒绝有损导入" }
            }
            for (key in listOf("holiday_end_course_exclusion", "holiday_before_course_exclusion"))
                require(!holiday.asJsonObject.has(key)) { "暂不支持假期节次排除" }
        }
        val blocks = mutableListOf<CourseBlock>()
        courses.forEach { element ->
            val course = element.asJsonObject
            val courseName = McpJson.text(course, "name", 256).also { require(it.isNotBlank()) }
            val day = McpJson.int(course, "dayOfWeek", 1..7)
            val first = McpJson.int(course, "startSection", 1..10)
            val last = McpJson.int(course, "endSection", first..10)
            require(sectionPeriod[first] == sectionPeriod[last]) { "暂不支持跨早/午/晚连堂" }
            val firstWeek = McpJson.int(course, "startWeek", 1..totalWeeks)
            val lastWeek = McpJson.int(course, "endWeek", firstWeek..totalWeeks)
            val parity = McpJson.int(course, "weekType", 0..2)
            require(!course.has("isCustomTime") || !McpJson.bool(course, "isCustomTime")) { "暂不支持自定义时间课程" }
            require(!course.has("_occurrence_date") && !course.has("_cancelled_dates")) { "暂不支持单日例外" }
            val selected = course.getAsJsonArray("selectedWeeks") ?: error("指定周次缺失")
            val weeks = selected.map { McpJson.int(JsonObject().apply { add("week", it) }, "week", 1..totalWeeks) }
            require(weeks.distinct().size == weeks.size)
            val active = weeks.ifEmpty { (firstWeek..lastWeek).filter { parity == 0 || it % 2 == parity % 2 } }
            require(active.isNotEmpty() && blocks.size + active.size <= 20_000) { "展开课程超出限制" }
            val room = if (course.has("classroom")) McpJson.text(course, "classroom", 512) else ""
            val teacher = if (course.has("teacher")) McpJson.text(course, "teacher", 256) else ""
            active.forEach { week -> blocks += CourseBlock(week, day, first, last, courseName, room, teacher,
                start.plusDays((week - 1L) * 7 + day - 1L).toString(), parity) }
        }
        val current = if (settings.has("current_week")) McpJson.int(settings, "current_week", -104..104).coerceIn(1, totalWeeks) else 1
        return ParsedSchedule(name, CalendarInfo(current, totalWeeks, start.toString(), start.year.toString(), "云端"),
            blocks.distinct(), courses.size(), root.toString())
    }
    private fun inferredStart(blocks: List<CourseBlock>): String {
        val block = blocks.firstOrNull { it.date.isNotBlank() } ?: error("课表没有校历开学日或可核验课程日期，拒绝导出")
        val date = LocalDate.parse(block.date)
        require(block.day in 1..7 && block.week > 0)
        return date.minusDays((block.day - 1).toLong()).minusWeeks((block.week - 1).toLong()).toString()
    }

    internal data class ParsedSchedule(val name: String, val calendar: CalendarInfo, val blocks: List<CourseBlock>,
        val courseCount: Int, val document: String)
    data class ScheduleShareInfo(val name: String, val courseCount: Int)
    private const val MAX_DOCUMENT_BYTES = 2 * 1024 * 1024
    private const val MAX_COURSES = 5000
}
