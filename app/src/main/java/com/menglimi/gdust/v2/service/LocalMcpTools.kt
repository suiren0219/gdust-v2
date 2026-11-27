package com.menglimi.gdust.v2.service

import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.menglimi.gdust.v2.data.CalendarInfo
import com.menglimi.gdust.v2.data.CourseBlock
import com.menglimi.gdust.v2.data.McpJson
import com.menglimi.gdust.v2.data.Todo
import com.menglimi.gdust.v2.ui.schedule.ScheduleTimes
import java.time.LocalDate

internal data class McpReadSnapshot(
    val calendar: CalendarInfo?, val blocks: List<CourseBlock>, val source: String,
    val todos: List<Todo>, val termStart: String, val currentWeek: Int,
)

/** 唯一依赖是只读快照供应器，没有账户、学校网络或任何写入接口。 */
internal class LocalMcpTools(private val snapshot: () -> McpReadSnapshot) {
    fun call(params: JsonObject): JsonObject {
        val name = McpJson.text(params, "name", 80)
        val spec = LocalMcpContract.tools().firstOrNull { it.asJsonObject["name"].asString == name }
            ?.asJsonObject ?: throw UnsupportedOperationException("未知只读工具")
        val args = params["arguments"]?.also { require(it.isJsonObject) }?.asJsonObject ?: JsonObject()
        val schema = spec.getAsJsonObject("inputSchema")
        require(args.keySet().all { it in schema.getAsJsonObject("properties").keySet() }) { "工具参数无效" }
        val data = snapshot()
        val start = ScheduleTimes.parseDate(data.termStart)
        val selected = when (name) {
            "get_week_courses" -> {
                val week = if (args.has("week")) McpJson.int(args, "week", 1..60) else data.currentWeek
                require(week in 1..60)
                data.blocks.filter { it.week == week }
            }
            "get_courses_on_date" -> {
                val date = LocalDate.parse(McpJson.text(args, "date", 10))
                data.blocks.filter { ScheduleTimes.lessonDate(it, start) == date }
            }
            "search_courses" -> {
                val query = McpJson.text(args, "query", 128).trim()
                require(query.isNotEmpty())
                data.blocks.filter { it.name.contains(query, true) || it.teacher.contains(query, true) || it.room.contains(query, true) }
            }
            "get_semester_info" -> return result(mapOf(
                "week" to data.currentWeek, "totalWeeks" to (data.calendar?.allWeek ?: 0),
                "startDate" to data.termStart, "source" to data.source,
            ))
            "get_todos" -> {
                val includeDone = args.has("include_done") && McpJson.bool(args, "include_done")
                return result(data.todos.filter { includeDone || !it.done })
            }
            else -> throw UnsupportedOperationException("未知只读工具")
        }
        return result(selected.sortedWith(compareBy({ it.week }, { it.day }, { it.startSection })))
    }

    private fun result(value: Any): JsonObject = JsonObject().apply {
        add("content", JsonArray().apply {
            add(JsonObject().apply { addProperty("type", "text"); addProperty("text", Gson().toJson(value)) })
        })
        addProperty("isError", false)
    }
}
