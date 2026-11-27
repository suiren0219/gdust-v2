package com.menglimi.gdust.v2.data

import com.google.gson.JsonObject
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response

internal data class CloudMcpConfig(val url: String = "", val token: String = "", val enabled: Boolean = false) {
    override fun toString() = "CloudMcpConfig(enabled=$enabled)"
}

internal data class CloudMcpStatus(val serverName: String, val hasSchedule: Boolean, val revision: Long)
internal data class CloudUploadPreview(
    val toolName: String, val pendingId: String, val baseRevision: Long, val courseCount: Int,
    val replaces: Boolean, val contentHash: String, val summary: String,
    val configHash: String, val expiresAt: Long,
) {
    override fun toString() = "CloudUploadPreview(baseRevision=$baseRevision, courseCount=$courseCount)"
}
internal data class CloudDownloadPreview(
    val revision: Long, val scheduleName: String, val courseCount: Int, val document: String,
) {
    override fun toString() = "CloudDownloadPreview(revision=$revision, courseCount=$courseCount)"
}

/** 定向连接用户的 Nexio JSON MCP；包括注入的测试客户端也禁止重定向与自动重试。 */
internal class CloudMcpClient(config: CloudMcpConfig, http: OkHttpClient = defaultClient) {
    private val http = http.newBuilder().followRedirects(false).followSslRedirects(false).retryOnConnectionFailure(false)
        .connectTimeout(5, TimeUnit.SECONDS).readTimeout(10, TimeUnit.SECONDS).callTimeout(20, TimeUnit.SECONDS).build()
    private val endpoint = normalizeEndpoint(config.url)
    private val bearer = config.token.trim().also { require(it.length in 1..4096 && it.all { c -> c.code in 33..126 }) }
    private var protocolVersion = "2025-11-25"

    init { require(config.enabled) { "云端连接已关闭" } }

    suspend fun initialize(): String {
        val result = rpc("initialize", JsonObject().apply {
            addProperty("protocolVersion", protocolVersion)
            add("capabilities", JsonObject())
            add("clientInfo", JsonObject().apply { addProperty("name", "gdust-android-cloud-control"); addProperty("version", "1") })
        })
        val negotiated = McpJson.text(result, "protocolVersion", 32)
        require(negotiated in VERSIONS) { "云端 MCP 协议版本不受支持" }
        protocolVersion = negotiated
        notifyInitialized()
        val info = result.getAsJsonObject("serverInfo") ?: error("云端服务信息缺失")
        return McpJson.text(info, "name", 80)
    }

    suspend fun tools(): Set<String> {
        val list = rpc("tools/list").getAsJsonArray("tools") ?: error("云端未返回工具列表")
        require(list.size() <= 256)
        return list.map { McpJson.text(it.asJsonObject, "name", 128) }.toSet()
    }

    suspend fun callTool(name: String, args: JsonObject = JsonObject()): JsonObject {
        val result = rpc("tools/call", JsonObject().apply { addProperty("name", name); add("arguments", args) })
        check(!result.has("isError") || !McpJson.bool(result, "isError")) { "云端拒绝了请求，请重新预览" }
        val content = result.getAsJsonArray("content") ?: error("云端返回格式不正确")
        val text = content.firstOrNull { it.isJsonObject && McpJson.text(it.asJsonObject, "type", 40) == "text" }
            ?.asJsonObject ?: error("云端未返回 JSON 文本")
        return McpJson.objectFrom(McpJson.text(text, "text", MAX_RESPONSE_BYTES))
    }

    private suspend fun notifyInitialized() {
        val payload = JsonObject().apply {
            addProperty("jsonrpc", "2.0"); addProperty("method", "notifications/initialized"); add("params", JsonObject())
        }
        execute(request(payload)) { response ->
            check(response.code in setOf(200, 202, 204)) { "云端初始化通知失败" }
        }
    }

    private suspend fun rpc(method: String, params: JsonObject = JsonObject()): JsonObject {
        val id = ids.incrementAndGet()
        val payload = JsonObject().apply {
            addProperty("jsonrpc", "2.0"); addProperty("id", id); addProperty("method", method); add("params", params)
        }
        return execute(request(payload)) { response ->
            check(response.isSuccessful) { "云端请求未成功" }
            check(response.header("Content-Type")?.substringBefore(';')?.trim()?.lowercase() == "application/json") {
                "当前客户端只支持 JSON，不支持 SSE 返回"
            }
            val body = response.body ?: error("云端返回空响应")
            require(body.contentLength() <= MAX_RESPONSE_BYTES)
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            body.byteStream().use { stream ->
                while (true) {
                    val count = stream.read(buffer)
                    if (count < 0) break
                    check(output.size() + count <= MAX_RESPONSE_BYTES) { "云端响应超过 1 MiB" }
                    output.write(buffer, 0, count)
                }
            }
            val root = McpJson.objectFrom(output.toString("UTF-8"))
            check(McpJson.text(root, "jsonrpc", 8) == "2.0" && McpJson.long(root, "id") == id.toLong()) { "云端响应标识不匹配" }
            check(!root.has("error")) { "云端 MCP 调用失败" }
            root.getAsJsonObject("result") ?: error("云端 MCP 结果缺失")
        }
    }

    private fun request(payload: JsonObject) = Request.Builder().url(endpoint).header("Authorization", "Bearer $bearer")
        .header("MCP-Protocol-Version", protocolVersion).header("Accept", "application/json, text/event-stream")
        .post(payload.toString().toRequestBody(JSON)).build()

    private suspend fun <T> execute(request: Request, read: (Response) -> T): T = suspendCancellableCoroutine { continuation ->
        val call = http.newCall(request)
        val activeResponse = AtomicReference<Response?>()
        continuation.invokeOnCancellation { call.cancel(); activeResponse.get()?.close() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (continuation.isActive) continuation.resumeWithException(IOException("无法连接个人课表云，请检查网络与 HTTPS 地址"))
            }
            override fun onResponse(call: Call, response: Response) {
                activeResponse.set(response)
                response.use {
                    if (!continuation.isActive) return
                    try {
                        check(response.code !in 300..399)
                        val value = read(response)
                        if (continuation.isActive) continuation.resume(value)
                    } catch (_: Exception) {
                        if (continuation.isActive) continuation.resumeWithException(
                            IllegalStateException("个人云请求未完成，请检查配置、网络或返回格式"))
                    } finally { activeResponse.set(null) }
                }
            }
        })
    }

    companion object {
        private val ids = AtomicInteger()
        private val JSON = "application/json; charset=utf-8".toMediaType()
        private val VERSIONS = setOf("2025-03-26", "2025-06-18", "2025-11-25")
        private const val MAX_RESPONSE_BYTES = 1024 * 1024
        private val defaultClient = OkHttpClient()

        fun normalizeEndpoint(value: String): String {
            require(value.length <= 2048) { "云端地址过长" }
            val url = value.trim().toHttpUrlOrNull() ?: throw IllegalArgumentException("请输入完整的 HTTPS 地址")
            require(url.isHttps && url.username.isEmpty() && url.password.isEmpty() && url.query == null && url.fragment == null) {
                "云端地址必须使用 HTTPS，且不能包含账号、查询参数或片段"
            }
            require(url.encodedPath in setOf("", "/", "/mcp")) { "云端接口路径必须是 /mcp" }
            return url.newBuilder().encodedPath("/mcp").build().toString()
        }
    }
}
