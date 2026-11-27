package com.menglimi.gdust.v2.service

import com.google.gson.JsonNull
import com.google.gson.JsonObject
import com.menglimi.gdust.v2.data.McpJson
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketTimeoutException
import java.security.MessageDigest
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.Semaphore

/** 有界的本机 HTTP 服务；shutdown 会同时关闭监听器和所有正在读取的连接。 */
internal class LocalMcpServer(
    requestedPort: Int,
    private val token: () -> String,
    private val tools: LocalMcpTools,
) : AutoCloseable {
    private val server = ServerSocket(requestedPort, 8, InetAddress.getByName("127.0.0.1"))
    val port: Int get() = server.localPort
    val bindAddress: String get() = server.inetAddress.hostAddress.orEmpty()
    private val clients = ConcurrentHashMap.newKeySet<Socket>()
    private val permits = Semaphore(4)
    private val workers = Executors.newFixedThreadPool(4) { task -> Thread(task, "gdust-mcp-request").apply { isDaemon = true } }
    private val acceptor = Thread({ accept() }, "gdust-mcp-listen").apply { isDaemon = true; start() }

    private fun accept() {
        while (!server.isClosed) {
            val client = try { server.accept() } catch (_: Exception) { break }
            if (!permits.tryAcquire()) { client.close(); continue }
            clients.add(client)
            try {
                workers.execute {
                    try { client.use { handle(it) } } catch (_: Exception) {
                        // 请求错误不记录正文，也不能终止监听线程。
                    } finally { clients.remove(client); permits.release() }
                }
            } catch (_: Exception) {
                clients.remove(client); client.close(); permits.release()
            }
        }
    }

    override fun close() {
        server.close()
        clients.forEach { runCatching { it.close() } }
        workers.shutdownNow()
        if (Thread.currentThread() != acceptor) acceptor.join(1000)
    }

    private fun handle(client: Socket) {
        client.soTimeout = 3000
        val output = BufferedOutputStream(client.getOutputStream())
        fun reply(code: Int, body: String = "") = LocalMcpContract.writeResponse(output, code, body)
        if (!client.inetAddress.isLoopbackAddress) { reply(403); return }
        val input = BufferedInputStream(client.getInputStream())
        val headers = readHeaders(input) ?: run { reply(400); return }
        val request = headers.first().split(' ')
        if (request.size != 3 || request[2] !in setOf("HTTP/1.1", "HTTP/1.0")) { reply(400); return }
        val values = linkedMapOf<String, String>()
        for (line in headers.drop(1)) {
            val colon = line.indexOf(':')
            if (colon <= 0 || line.first().isWhitespace()) { reply(400); return }
            val key = line.substring(0, colon).lowercase(Locale.ROOT)
            if (values.put(key, line.substring(colon + 1).trim()) != null) { reply(400); return }
        }
        val hosts = setOf("127.0.0.1:$port", "localhost:$port")
        if (values["host"] !in hosts) { reply(403); return }
        val origin = values["origin"]
        if (origin != null && origin !in hosts.map { "http://$it" }) { reply(403); return }
        if (values["transfer-encoding"] != null) { reply(400); return }
        if (request[1] != "/mcp") { reply(404); return }
        if (request[0] != "POST") { reply(405); return }
        val expected = token()
        if (expected.isNotEmpty()) {
            val header = values["authorization"].orEmpty()
            val provided = if (header.startsWith("Bearer ")) header.removePrefix("Bearer ") else ""
            if (!MessageDigest.isEqual(expected.toByteArray(), provided.toByteArray())) { reply(401); return }
        }
        val length = LocalMcpContract.readRequestBodyLength(values["content-length"])
        if (length == null) { reply(413); return }
        if (values["content-type"]?.substringBefore(';')?.trim() != "application/json") { reply(415); return }
        val bytes = ByteArray(length)
        var offset = 0
        val deadline = System.nanoTime() + 5_000_000_000L
        while (offset < length) {
            if (System.nanoTime() > deadline) throw SocketTimeoutException()
            val read = input.read(bytes, offset, length - offset)
            if (read < 0) { reply(400); return }
            offset += read
        }
        val rpc = try { McpJson.objectFrom(bytes.toString(Charsets.UTF_8)) } catch (_: Exception) {
            reply(400, error(null, -32700, "无法解析 JSON").toString()); return
        }
        val id = rpc["id"]
        val method = try {
            require(McpJson.text(rpc, "jsonrpc", 8) == "2.0")
            if (id != null) {
                require(id.isJsonPrimitive)
                if (id.asJsonPrimitive.isString) require(McpJson.text(rpc, "id", 128).isNotEmpty())
                else McpJson.long(rpc, "id")
            }
            if (rpc.has("params")) require(rpc["params"].isJsonObject)
            McpJson.text(rpc, "method", 80)
        } catch (_: Exception) {
            reply(400, error(null, -32600, "无效 JSON-RPC 请求").toString()); return
        }
        // 无 id 的 notification 不执行任何工具；不接受 null id 冒充可确认请求。
        if (id == null) { reply(202); return }
        val params = rpc["params"]?.asJsonObject ?: JsonObject()
        val response = try {
            val value = when (method) {
                "initialize" -> LocalMcpContract.initialize(params)
                "ping" -> JsonObject()
                "tools/list" -> JsonObject().apply { add("tools", LocalMcpContract.tools()) }
                "tools/call" -> tools.call(params)
                else -> throw UnsupportedOperationException()
            }
            JsonObject().apply { addProperty("jsonrpc", "2.0"); add("id", id); add("result", value) }
        } catch (_: UnsupportedOperationException) { error(id, -32601, "未知只读工具或方法") }
        catch (_: Exception) { error(id, -32602, "参数无效或本机数据不可读") }
        val body = response.toString()
        if (body.toByteArray().size > 1024 * 1024) { reply(500, error(id, -32603, "结果过大，请缩小查询范围").toString()) }
        else reply(200, body)
    }

    private fun readHeaders(input: BufferedInputStream): List<String>? {
        val bytes = ByteArrayOutputStream()
        var matched = 0
        val ending = intArrayOf(13, 10, 13, 10)
        val deadline = System.nanoTime() + 5_000_000_000L
        while (bytes.size() < LocalMcpContract.MAX_HEADER_BYTES) {
            if (System.nanoTime() > deadline) throw SocketTimeoutException()
            val next = input.read()
            if (next < 0) return null
            bytes.write(next)
            matched = if (next == ending[matched]) matched + 1 else if (next == 13) 1 else 0
            if (matched == 4) return bytes.toString("US-ASCII").dropLast(4).split("\r\n")
        }
        return null
    }

    private fun error(id: com.google.gson.JsonElement?, code: Int, message: String) = JsonObject().apply {
        addProperty("jsonrpc", "2.0"); add("id", id ?: JsonNull.INSTANCE)
        add("error", JsonObject().apply { addProperty("code", code); addProperty("message", message) })
    }
}
