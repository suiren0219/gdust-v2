package com.menglimi.gdust.v2.service

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import java.io.BufferedOutputStream

/** Protocol constants/catalog are pure so read-only and response contracts can be unit tested. */
internal object LocalMcpContract {
    const val PORT = 26621
    const val BIND_ADDRESS = "127.0.0.1"

    fun tools(): JsonArray = JsonArray().apply {
        add(tool("get_week_courses", "读取指定教学周课程；不提供时使用当前周。", listOf("week" to "integer")))
        add(tool("get_courses_on_date", "读取指定日期课程，日期格式 YYYY-MM-DD。", listOf("date" to "string")))
        add(tool("search_courses", "按课程名、教师或地点查找本机课表。", listOf("query" to "string")))
        add(tool("get_semester_info", "读取本机校历和学期信息。", emptyList()))
        add(tool("get_todos", "读取本机待办，默认隐藏已完成项。", listOf("include_done" to "boolean")))
    }

    fun initialize(params: JsonObject): JsonObject {
        val offered = if (params.has("protocolVersion")) com.menglimi.gdust.v2.data.McpJson.text(params, "protocolVersion", 32) else "2025-11-25"
        val version = offered.takeIf { it in setOf("2025-03-26", "2025-06-18", "2025-11-25") } ?: "2025-11-25"
        return JsonObject().apply {
            addProperty("protocolVersion", version)
            add("capabilities", JsonObject().apply { add("tools", JsonObject()) })
            add("serverInfo", JsonObject().apply { addProperty("name", "gdust-local-mcp"); addProperty("version", "1.0") })
        }
    }

    fun readRequestBodyLength(contentLength: String?): Int? =
        contentLength?.toIntOrNull()?.takeIf { it in 1..MAX_REQUEST_BYTES }

    const val MAX_REQUEST_BYTES = 1024 * 1024
    const val MAX_HEADER_BYTES = 16 * 1024

    fun writeResponse(output: BufferedOutputStream, status: Int, body: String) {
        val bytes = body.toByteArray(Charsets.UTF_8)
        val reason = when (status) {
            200 -> "OK"; 202 -> "Accepted"; 400 -> "Bad Request"; 401 -> "Unauthorized"
            403 -> "Forbidden"; 404 -> "Not Found"; 405 -> "Method Not Allowed"
            413 -> "Payload Too Large"; 415 -> "Unsupported Media Type"; 500 -> "Internal Server Error"; else -> "Error"
        }
        output.write(("HTTP/1.1 $status $reason\r\nContent-Type: application/json; charset=utf-8\r\n" +
            "Content-Length: ${bytes.size}\r\nConnection: close\r\n\r\n").toByteArray())
        output.write(bytes)
        output.flush()
    }

    private fun tool(name: String, description: String, fields: List<Pair<String, String>>) = JsonObject().apply {
        addProperty("name", name)
        addProperty("description", description)
        add("inputSchema", JsonObject().apply {
            addProperty("type", "object")
            add("properties", JsonObject().apply {
                fields.forEach { (field, type) -> add(field, JsonObject().apply { addProperty("type", type) }) }
            })
            add("required", JsonArray().apply {
                if (name == "search_courses") add("query")
                if (name == "get_courses_on_date") add("date")
            })
            addProperty("additionalProperties", false)
        })
        add("annotations", JsonObject().apply {
            addProperty("readOnlyHint", true)
            addProperty("destructiveHint", false)
            addProperty("openWorldHint", false)
        })
    }
}
