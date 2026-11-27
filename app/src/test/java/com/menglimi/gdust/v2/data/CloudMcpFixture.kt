package com.menglimi.gdust.v2.data

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import java.util.Collections

/** 应用拦截器返回合成响应，不做 DNS、TLS 或真实服务器请求。 */
internal class CloudMcpFixture {
    val config = CloudMcpConfig("https://example.test/mcp", "synthetic-token", true)
    val document = ScheduleShareCodec.export(CalendarInfo(1, 4, "2026-09-07", "2026", "1"),
        listOf(CourseBlock(1, 1, 1, 1, "Synthetic", "Room", "Teacher", "2026-09-07")), "Synthetic Schedule")
    val requests: MutableList<JsonObject> = Collections.synchronizedList(mutableListOf())
    var saved = config
    var saves = 0
    val prefs = object : CloudMcpConfigStore {
        override suspend fun load() = saved
        override suspend fun save(config: CloudMcpConfig) { saved = config; saves++ }
    }
    val http = OkHttpClient.Builder().addInterceptor { chain ->
        val request = chain.request()
        val body = JsonParser.parseString(Buffer().also { request.body!!.writeTo(it) }.readUtf8()).asJsonObject
        requests.add(body)
        if (body["method"].asString == "notifications/initialized") return@addInterceptor Response.Builder().request(request)
            .protocol(Protocol.HTTP_1_1).code(202).message("synthetic").body("".toResponseBody()).build()
        val result = when (body["method"].asString) {
            "initialize" -> JsonObject().apply {
                addProperty("protocolVersion", "2025-11-25")
                add("serverInfo", JsonObject().apply { addProperty("name", "synthetic-cloud") })
            }
            "tools/list" -> JsonObject().apply { add("tools", JsonArray().apply {
                listOf("get_sync_status", "import_schedule_file", "update_schedule_file", "export_schedule_file").forEach {
                    add(JsonObject().apply { addProperty("name", it) })
                }
            }) }
            "tools/call" -> {
                val params = body.getAsJsonObject("params")
                val toolResult = when (params["name"].asString) {
                    "get_sync_status" -> JsonObject().apply { addProperty("hasSchedule", false); addProperty("revision", 4) }
                    "export_schedule_file" -> JsonObject().apply {
                        addProperty("hasSchedule", true); addProperty("revision", 4); addProperty("format", "nexio")
                        addProperty("filename", "unrelated-filename.json"); addProperty("content", document)
                    }
                    "import_schedule_file", "update_schedule_file" -> JsonObject().apply {
                        if (params.getAsJsonObject("arguments")["confirm"]?.asBoolean == true) {
                            addProperty("ok", true); addProperty("hasSchedule", true); addProperty("revision", 5)
                        } else {
                            addProperty("requiresConfirmation", true); addProperty("baseRevision", 4)
                            addProperty("pendingId", "synthetic-pending-id"); addProperty("summary", "Synthetic preview")
                            addProperty("expiresInSeconds", 300)
                        }
                    }
                    else -> error("Unexpected synthetic tool")
                }
                JsonObject().apply { add("content", JsonArray().apply { add(JsonObject().apply {
                    addProperty("type", "text"); addProperty("text", toolResult.toString())
                }) }) }
            }
            else -> error("Unexpected synthetic method")
        }
        val response = JsonObject().apply { addProperty("jsonrpc", "2.0"); add("id", body["id"]); add("result", result) }
        Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(200).message("synthetic")
            .header("Content-Type", "application/json").body(response.toString().toResponseBody("application/json".toMediaType())).build()
    }.build()
    fun repository(now: () -> Long = System::currentTimeMillis) = CloudMcpRepository(prefs, { CloudMcpClient(it, http) }, now)
}
