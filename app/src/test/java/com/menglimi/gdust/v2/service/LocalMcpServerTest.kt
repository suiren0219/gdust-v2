package com.menglimi.gdust.v2.service

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.menglimi.gdust.v2.data.CalendarInfo
import com.menglimi.gdust.v2.data.CourseBlock
import org.junit.Assert.*
import org.junit.Test
import java.net.Socket
import java.net.SocketException
import java.nio.charset.StandardCharsets.UTF_8

class LocalMcpServerTest {
    private val data = McpReadSnapshot(CalendarInfo(1, 4, "2026-09-07", "2026", "1"),
        listOf(CourseBlock(1, 1, 1, 1, "Synthetic Course", "Synthetic Room", "Synthetic Teacher", "2026-09-07")),
        "cloud_import", emptyList(), "2026-09-07", 1)

    @Test fun actualLoopbackListenerServesCatalogAndCurrentSchedule() {
        LocalMcpServer(0, { "" }, LocalMcpTools { data }).use { server ->
            assertEquals("127.0.0.1", server.bindAddress)
            val initialized = json(request(server, rpc("initialize")))
            assertEquals("gdust-local-mcp", initialized.getAsJsonObject("result").getAsJsonObject("serverInfo")["name"].asString)
            val tools = json(request(server, rpc("tools/list"))).getAsJsonObject("result").getAsJsonArray("tools")
            assertEquals(5, tools.size())
            assertTrue(tools.all { it.asJsonObject.getAsJsonObject("annotations")["readOnlyHint"].asBoolean })
            val response = json(request(server, rpc("tools/call", """{"name":"get_week_courses","arguments":{"week":1}}""")))
            val text = response.getAsJsonObject("result").getAsJsonArray("content")[0].asJsonObject["text"].asString
            assertEquals("Synthetic Course", JsonParser.parseString(text).asJsonArray[0].asJsonObject["name"].asString)
        }
    }

    @Test fun bearerHostAndOriginAreCheckedByActualSocket() {
        LocalMcpServer(0, { "synthetic-token" }, LocalMcpTools { data }).use { server ->
            assertStatus(401, request(server, rpc("ping")))
            assertStatus(401, request(server, rpc("ping"), authorization = "wrong"))
            assertStatus(401, request(server, rpc("ping"), authorization = "synthetic-token"))
            assertStatus(200, request(server, rpc("ping"), authorization = "Bearer synthetic-token"))
            assertStatus(403, request(server, rpc("ping"), authorization = "Bearer synthetic-token", host = "evil.test:${server.port}"))
            assertStatus(403, request(server, rpc("ping"), authorization = "Bearer synthetic-token", origin = "https://evil.test"))
            assertStatus(200, request(server, rpc("ping"), authorization = "Bearer synthetic-token", origin = "http://localhost:${server.port}"))
        }
    }

    @Test fun malformedAndCoercedRequestsCannotInvokeTools() {
        var reads = 0
        LocalMcpServer(0, { "" }, LocalMcpTools { reads++; data }).use { server ->
            listOf("""{"jsonrpc":"2.0","id":null,"method":"tools/call"}""",
                """{"jsonrpc":"2.0","id":1.5,"method":"ping"}""",
                """{"jsonrpc":"2.0","id":true,"method":"ping"}""",
                """{"jsonrpc":"2.0","id":1,"id":2,"method":"ping"}""").forEach {
                assertStatus(400, request(server, it))
            }
            assertStatus(202, request(server, """{"jsonrpc":"2.0","method":"tools/call","params":{"name":"get_todos"}}"""))
            assertEquals(0, reads)
            listOf("""{"name":"get_week_courses","arguments":{"week":1.5}}""",
                """{"name":"get_todos","arguments":{"include_done":"true"}}""",
                """{"name":"get_week_courses","arguments":{"extra":1}}""").forEach {
                val response = json(request(server, rpc("tools/call", it)))
                assertEquals(-32602, response.getAsJsonObject("error")["code"].asInt)
            }
        }
    }

    @Test fun shutdownClosesIdleClientsAndReleasesThePort() {
        val server = LocalMcpServer(0, { "" }, LocalMcpTools { data })
        val port = server.port
        Socket("127.0.0.1", port).use { idle ->
            idle.soTimeout = 2000
            server.close()
            val outcome = runCatching { idle.getInputStream().read() }
            assertTrue(outcome.getOrNull() == -1 || outcome.exceptionOrNull() is SocketException)
        }
        assertThrows(SocketException::class.java) { Socket("127.0.0.1", port).close() }
        server.close()
    }

    private fun rpc(method: String, params: String = "{}") = """{"jsonrpc":"2.0","id":1,"method":"$method","params":$params}"""
    private fun json(response: String): JsonObject = JsonParser.parseString(response.substringAfter("\r\n\r\n")).asJsonObject
    private fun assertStatus(code: Int, response: String) { assertTrue(response.substringBefore("\r\n"), response.startsWith("HTTP/1.1 $code ")) }
    private fun request(server: LocalMcpServer, body: String, authorization: String? = null,
        host: String = "127.0.0.1:${server.port}", origin: String? = null): String = Socket("127.0.0.1", server.port).use { socket ->
        socket.soTimeout = 3000
        val bytes = body.toByteArray(UTF_8)
        val header = "POST /mcp HTTP/1.1\r\nHost: $host\r\nContent-Type: application/json\r\nContent-Length: ${bytes.size}\r\n" +
            (authorization?.let { "Authorization: $it\r\n" } ?: "") + (origin?.let { "Origin: $it\r\n" } ?: "") + "\r\n"
        socket.getOutputStream().apply { write(header.toByteArray(UTF_8)); write(bytes); flush() }
        socket.getInputStream().readBytes().toString(UTF_8)
    }
}
