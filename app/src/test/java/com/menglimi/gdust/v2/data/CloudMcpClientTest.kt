package com.menglimi.gdust.v2.data

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import okio.Buffer

class CloudMcpClientTest {
    @Test fun disabledCloudConfigDoesNotCreateClientOrNetworkRequest() = runBlocking {
        var requests = 0
        val http = fixture { request -> requests++; response(request, 500, "") }
        assertThrows(IllegalArgumentException::class.java) {
            CloudMcpClient(CloudMcpConfig("https://example.test/mcp", "synthetic-token", false), http)
        }
        assertEquals(0, requests)
    }

    @Test fun endpointOnlyAllowsFinalHttpsMcpAddress() {
        assertEquals("https://example.test/mcp", CloudMcpClient.normalizeEndpoint("https://example.test"))
        listOf("http://example.test/mcp", "https://user:pass@example.test/mcp", "https://example.test/mcp?q=1",
            "https://example.test/custom").forEach { value ->
            assertThrows(IllegalArgumentException::class.java) { CloudMcpClient.normalizeEndpoint(value) }
        }
    }

    @Test fun requestUsesBearerHeaderAndNeverFollowsRedirect() = runBlocking {
        var seen: okhttp3.Request? = null
        val http = fixture { request ->
            seen = request
            response(request, 302, "")
        }
        val client = CloudMcpClient(CloudMcpConfig("https://example.test/mcp", "synthetic-token", true), http)
        val error = runCatching { client.initialize() }.exceptionOrNull()
        assertTrue(error is IllegalStateException)
        assertEquals("Bearer synthetic-token", seen?.header("Authorization"))
        assertFalse(seen?.url.toString().contains("synthetic-token"))
    }

    @Test fun statusAndEmptySnapshotUploadPreviewUseMcpToolContracts() = runBlocking {
        val requests = mutableListOf<JsonObject>()
        val http = fixture { request ->
            val body = JsonParser.parseString(Buffer().also { request.body!!.writeTo(it) }.readUtf8()).asJsonObject
            requests += body
            val id = body.get("id")?.asInt
            val method = body.get("method").asString
            val result = when (method) {
                "initialize" -> JsonObject().apply {
                    addProperty("protocolVersion", "2025-11-25")
                    add("serverInfo", JsonObject().apply { addProperty("name", "synthetic-cloud") })
                }
                "notifications/initialized" -> return@fixture response(request, 202, "")
                "tools/list" -> JsonObject().apply { add("tools", com.google.gson.JsonArray().apply {
                    listOf("get_sync_status", "import_schedule_file", "update_schedule_file", "export_schedule_file").forEach { name ->
                        add(JsonObject().apply { addProperty("name", name) })
                    }
                }) }
                "tools/call" -> {
                    val params = body.getAsJsonObject("params")
                    val name = params.get("name").asString
                    val args = params.getAsJsonObject("arguments")
                    val content = when (name) {
                        "get_sync_status" -> """{"hasSchedule":false,"revision":4}"""
                        "import_schedule_file" -> {
                            if (args.get("confirm")?.asBoolean == true) {
                                assertEquals(setOf("confirm", "pending_id"), args.keySet())
                                """{"ok":true,"hasSchedule":true,"revision":5}"""
                            } else {
                                assertTrue(args.get("content").asString.isNotBlank())
                                assertEquals(4, args.get("expected_revision").asInt)
                                """{"requiresConfirmation":true,"baseRevision":4,"pendingId":"synthetic-pending-id","summary":"将导入 1 门合成课程"}"""
                            }
                        }
                        else -> error("unexpected tool")
                    }
                    JsonObject().apply { add("content", com.google.gson.JsonArray().apply {
                        add(JsonObject().apply { addProperty("type", "text"); addProperty("text", content) })
                    }) }
                }
                else -> error("unexpected method")
            }
            response(request, 200, JsonObject().apply {
                addProperty("jsonrpc", "2.0"); if (id != null) addProperty("id", id); add("result", result)
            }.toString())
        }
        val repo = CloudMcpRepository(CloudMcpPrefsFake(), { CloudMcpClient(it, http) })
        val config = CloudMcpConfig("https://example.test/mcp", "synthetic-token", true)
        val document = ScheduleShareCodec.export(CalendarInfo(1, 4, "2026-09-07", "2026", "1"),
            listOf(CourseBlock(1, 1, 1, 1, "Synthetic", "Room", "Teacher", "2026-09-07")))
        val preview = repo.previewUpload(config, document)
        assertEquals("synthetic-pending-id", preview.pendingId)
        assertEquals(listOf("get_sync_status", "import_schedule_file"), requests.filter { it.get("method")?.asString == "tools/call" }
            .map { it.getAsJsonObject("params").get("name").asString })
        repo.confirmUpload(config, preview, document)
        val confirmation = requests.filter { it.get("method")?.asString == "tools/call" }.last()
            .getAsJsonObject("params").getAsJsonObject("arguments")
        assertEquals(2, confirmation.size())
        assertEquals(true, confirmation.get("confirm").asBoolean)
        assertEquals("synthetic-pending-id", confirmation.get("pending_id").asString)
    }

    @Test fun responseBodyLimitIsEnforced() = runBlocking {
        val huge = "x".repeat(1024 * 1024 + 1)
        val http = fixture { request -> response(request, 200, huge) }
        val error = runCatching {
            CloudMcpClient(CloudMcpConfig("https://example.test/mcp", "synthetic-token", true), http).initialize()
        }.exceptionOrNull()
        assertTrue(error != null)
    }

    private fun fixture(handler: (okhttp3.Request) -> Response): OkHttpClient = OkHttpClient.Builder()
        .followRedirects(false).followSslRedirects(false).addInterceptor(Interceptor { chain -> handler(chain.request()) }).build()

    private fun response(request: okhttp3.Request, code: Int, body: String) = Response.Builder().request(request)
        .protocol(Protocol.HTTP_1_1).code(code).message("synthetic")
        .header("Content-Type", "application/json")
        .body(body.toResponseBody("application/json".toMediaType())).build()
}

private class CloudMcpPrefsFake : CloudMcpConfigStore {
    private var value = CloudMcpConfig()
    override suspend fun load() = value
    override suspend fun save(config: CloudMcpConfig) { value = config }
}
