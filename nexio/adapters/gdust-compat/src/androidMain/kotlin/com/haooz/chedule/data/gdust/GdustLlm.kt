package com.haooz.chedule.data.gdust

import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * OpenAI 兼容端点客户端（用户自填中转站），用于学校通知的要点总结。
 * 非流式；max_tokens 给足（中转站后端多为推理型模型，首 token 可能慢）。
 * 配置（Base/Key/模型名）全部用户自填，无内置默认，只存本机。
 */
class GdustLlm(
    private val baseUrl: String,
    private val apiKey: String,
    private val model: String,
) {

    class LlmException(message: String) : Exception(message)

    suspend fun chat(system: String, user: String): String = withContext(Dispatchers.IO) {
        val body = buildString {
            append("{\"model\":")
            append(jsonStr(model))
            append(",\"messages\":[{\"role\":\"system\",\"content\":")
            append(jsonStr(system))
            append("},{\"role\":\"user\",\"content\":")
            append(jsonStr(user))
            append("}],\"max_tokens\":2048,\"temperature\":0.3}")
        }
        val req = Request.Builder()
            .url(baseUrl.trimEnd('/') + "/chat/completions")
            .header("Authorization", "Bearer $apiKey")
            .post(body.toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()
        GdustHttp.client.newCall(req).execute().use { resp ->
            val text = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) throw LlmException("HTTP ${resp.code}: ${text.take(160)}")
            val obj = runCatching { JsonParser.parseString(text).asJsonObject }.getOrElse {
                throw LlmException("响应非 JSON: ${text.take(160)}")
            }
            obj.getAsJsonArray("choices")?.firstOrNull()?.asJsonObject
                ?.getAsJsonObject("message")?.get("content")?.asString
                ?: throw LlmException("响应缺少 choices: ${text.take(160)}")
        }
    }

    private fun jsonStr(s: String): String =
        "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
}
