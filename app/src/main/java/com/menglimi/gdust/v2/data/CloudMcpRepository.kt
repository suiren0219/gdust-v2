package com.menglimi.gdust.v2.data

import com.google.gson.JsonObject
import java.security.MessageDigest

internal class CloudMcpRepository(
    private val prefs: CloudMcpConfigStore,
    private val newClient: (CloudMcpConfig) -> CloudMcpClient = { CloudMcpClient(it) },
    private val now: () -> Long = System::currentTimeMillis,
) {
    suspend fun loadConfig(): CloudMcpConfig = prefs.load()

    suspend fun saveConfig(url: String, token: String, enabled: Boolean): CloudMcpConfig {
        val normalized = if (url.isBlank() && token.isBlank() && !enabled) "" else CloudMcpClient.normalizeEndpoint(url)
        val bearer = token.trim()
        require(!enabled || bearer.isNotEmpty()) { "启用云端连接前请填写 Token" }
        require(bearer.length <= 4096 && bearer.all { it.code in 33..126 }) { "Token 格式不正确" }
        return CloudMcpConfig(normalized, bearer, enabled).also { prefs.save(it) }
    }

    suspend fun inspect(config: CloudMcpConfig): CloudMcpStatus {
        val client = newClient(config)
        val server = client.initialize()
        val tools = client.tools()
        require("get_sync_status" in tools && "export_schedule_file" in tools &&
            "import_schedule_file" in tools && "update_schedule_file" in tools) { "个人课表文件同步工具不完整" }
        return status(server, client.callTool("get_sync_status"))
    }

    suspend fun previewUpload(config: CloudMcpConfig, document: String): CloudUploadPreview {
        val info = ScheduleShareCodec.inspect(document)
        val client = newClient(config)
        client.initialize()
        val current = status("", client.callTool("get_sync_status"))
        val tool = if (current.hasSchedule) "update_schedule_file" else "import_schedule_file"
        val result = client.callTool(tool, JsonObject().apply {
            addProperty("filename", "广科课表.json"); addProperty("content", document)
            addProperty("expected_revision", current.revision)
        })
        require(McpJson.bool(result, "requiresConfirmation")) { "云端未生成确认预览" }
        require(McpJson.long(result, "baseRevision") == current.revision) { "云端版本已变化，请重新预览" }
        require(McpJson.text(result, "summary", 4096).isNotBlank()) { "云端预览缺少变更摘要" }
        val pending = McpJson.text(result, "pendingId", 256).also {
            require(it.length >= 8 && it.all { c -> c.code in 33..126 })
        }
        val ttl = if (result.has("expiresInSeconds")) McpJson.int(result, "expiresInSeconds", 1..300) else 300
        return CloudUploadPreview(tool, pending, current.revision, info.courseCount, current.hasSchedule,
            hash(document), "${info.courseCount} 门课程", configHash(config), now() + ttl * 1000L)
    }

    suspend fun confirmUpload(config: CloudMcpConfig, preview: CloudUploadPreview, document: String): CloudMcpStatus {
        require(preview.toolName in setOf("import_schedule_file", "update_schedule_file"))
        require(configHash(config) == preview.configHash) { "连接配置已变化，请重新预览" }
        require(now() < preview.expiresAt) { "预览已过期，请重新生成" }
        require(hash(document) == preview.contentHash) { "课表在预览后已变更，请重新预览" }
        val client = newClient(config)
        val server = client.initialize()
        val result = client.callTool(preview.toolName, JsonObject().apply {
            addProperty("confirm", true); addProperty("pending_id", preview.pendingId)
        })
        require(McpJson.bool(result, "ok")) { "云端未确认保存结果" }
        return status(server, result).also { require(it.hasSchedule) }
    }

    suspend fun previewDownload(config: CloudMcpConfig): CloudDownloadPreview {
        val client = newClient(config)
        client.initialize()
        require("export_schedule_file" in client.tools()) { "云端没有课表导出工具" }
        val result = client.callTool("export_schedule_file", JsonObject().apply { addProperty("format", "nexio") })
        val current = status("", result)
        require(current.hasSchedule) { "云端还没有课表" }
        if (result.has("format")) require(McpJson.text(result, "format", 32) == "nexio")
        val content = McpJson.text(result, "content", 2 * 1024 * 1024).also { require(it.isNotBlank()) }
        val parsed = ScheduleShareCodec.parse(content)
        return CloudDownloadPreview(current.revision, parsed.name, parsed.courseCount, parsed.document)
    }

    private fun status(server: String, value: JsonObject) = CloudMcpStatus(server,
        McpJson.bool(value, "hasSchedule"), McpJson.long(value, "revision").also { require(it >= 0) })

    private fun configHash(config: CloudMcpConfig) = hash("${CloudMcpClient.normalizeEndpoint(config.url)}\u0000${config.token}\u0000${config.enabled}")
    private fun hash(value: String) = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it.toInt() and 0xff) }
}
