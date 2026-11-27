package com.menglimi.gdust.v2.data

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Authenticator
import okhttp3.Call
import okhttp3.Callback
import okhttp3.CookieJar
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okio.Buffer
import java.io.IOException
import java.io.InterruptedIOException
import java.net.URI
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** 只携带本地生成的提示；不附带响应原文、URL、密钥或原始异常链。 */
class LlmException internal constructor(message: String) : Exception(message)

data class LlmProbeResult(val success: Boolean, val latencyMs: Long, val error: String? = null)

internal const val MAX_LLM_RESPONSE_BYTES = 1024L * 1024L

/** 根 host 默认 /v1；已填写的自定义前缀和完整资源路径不再补版本号。 */
internal fun normalizeLlmEndpoint(base: String): String {
    val raw = base.trim()
    val uri = try { URI(raw) } catch (_: Exception) { null }
    val url = raw.toHttpUrlOrNull()
    if (uri == null || url == null || uri.isOpaque || uri.rawAuthority == null ||
        uri.rawUserInfo != null || uri.rawAuthority.contains('@') ||
        uri.rawQuery != null || uri.rawFragment != null ||
        url.username.isNotEmpty() || url.password.isNotEmpty() ||
        raw.any { it.isISOControl() } || '\\' in raw
    ) {
        throw LlmException("API 地址须为 http(s) 地址，不能包含账号密码、查询参数或片段。")
    }
    val path = url.encodedPath.trimEnd('/')
    val rootPath = when {
        path.endsWith("/chat/completions") -> path.removeSuffix("/chat/completions")
        path.endsWith("/models") -> path.removeSuffix("/models")
        path.isEmpty() -> "/v1"
        else -> path
    }
    return url.newBuilder().encodedPath(rootPath.ifEmpty { "/" }).build().toString().trimEnd('/')
}

private fun llmKey(key: String): String = key.trim().also { value ->
    if (value.isEmpty() || value.length > 8192 || value.any { it.code !in 33..126 }) {
        throw LlmException("请填写有效的 API Key，不能包含空格、换行或控制字符。")
    }
}

private fun llmModel(model: String): String = model.trim().also { value ->
    if (value.isEmpty() || value.length > 256 || value.any { it.isISOControl() }) {
        throw LlmException("请填写有效的模型名称（最多 256 个字符）。")
    }
}

/** 保存前仅校验，不创建网络请求。 */
internal fun validateLlmConfiguration(base: String, key: String, model: String) {
    normalizeLlmEndpoint(base)
    llmKey(key)
    llmModel(model)
}

internal class LlmClient(baseClient: OkHttpClient) {
    private val gson = Gson()
    private val http = baseClient.newBuilder()
        .followRedirects(false).followSslRedirects(false).retryOnConnectionFailure(false)
        .authenticator(Authenticator.NONE).proxyAuthenticator(Authenticator.NONE)
        .cookieJar(CookieJar.NO_COOKIES).cache(null)
        .connectTimeout(10, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS).callTimeout(30, TimeUnit.SECONDS)
        .addNetworkInterceptor { chain ->
            val response = chain.proceed(chain.request())
            // OkHttp 会单独重放 Retry-After: 0 的 503；测试请求也只允许发送一次。
            if (response.code == 503 && response.header("Retry-After")?.toLongOrNull() == 0L) {
                response.newBuilder().header("Retry-After", "1").build()
            } else response
        }.build()

    suspend fun models(base: String, key: String): List<String> {
        val token = llmKey(key)
        val request = request(base, token, "models").get().build()
        return execute(request) { json ->
            val data = json.get("data")?.takeIf { it.isJsonArray }?.asJsonArray
                ?: throw LlmException("模型列表格式不兼容（需要 data 数组），仍可手动填写模型名称。")
            data.mapNotNull { item ->
                val id = item.takeIf { it.isJsonObject }?.asJsonObject?.get("id")
                    ?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString?.trim()
                id?.takeIf { it.isNotEmpty() && it.length <= 256 &&
                    it.none { char -> char.isISOControl() } && !it.contains(token) }
            }.distinct().sorted()
        }
    }

    suspend fun complete(base: String, key: String, model: String, messages: List<AiChatMessage>): String {
        if (messages.isEmpty()) throw LlmException("对话内容不能为空。")
        val request = chatRequest(base, key, model, messages, maxTokens = 2048, probe = false)
        return execute(request, timeoutSeconds = 90) { completion(it) }
    }

    /** 仅合成一条短消息，验证所选模型的真实生成能力，不发送任何课表或聊天记录。 */
    suspend fun probe(base: String, key: String, model: String): LlmProbeResult {
        val started = System.nanoTime()
        fun elapsed() = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started).coerceAtLeast(0)
        return try {
            val request = chatRequest(base, key, model,
                listOf(AiChatMessage("user", "Reply OK.")), maxTokens = 8, probe = true)
            execute(request, maxBytes = 64L * 1024L) { completion(it); Unit }
            LlmProbeResult(success = true, latencyMs = elapsed())
        } catch (error: LlmException) {
            LlmProbeResult(success = false, latencyMs = elapsed(), error = error.message)
        }
    }

    private fun request(base: String, key: String, resource: String): Request.Builder =
        Request.Builder().url("${normalizeLlmEndpoint(base)}/$resource")
            .header("Authorization", "Bearer $key").header("Accept", "application/json")

    private fun chatRequest(base: String, key: String, model: String, messages: List<AiChatMessage>,
        maxTokens: Int, probe: Boolean): Request {
        val fields = linkedMapOf<String, Any>(
            "model" to llmModel(model),
            "messages" to messages.map { mapOf("role" to it.role, "content" to it.content) },
            "max_tokens" to maxTokens,
            "stream" to false,
        )
        if (!probe) fields["temperature"] = 0.4
        return request(base, llmKey(key), "chat/completions")
            .post(gson.toJson(fields).toRequestBody("application/json; charset=utf-8".toMediaType())).build()
    }

    private fun completion(json: JsonObject): String {
        val choice = json.get("choices")?.takeIf { it.isJsonArray }?.asJsonArray?.firstOrNull()
            ?.takeIf { it.isJsonObject }?.asJsonObject
        val message = choice?.get("message")?.takeIf { it.isJsonObject }?.asJsonObject
        return message?.get("content")?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }
            ?.asString?.takeIf { it.isNotBlank() }
            ?: throw LlmException("服务响应缺少有效的 Chat Completions 文本，请检查模型是否支持该接口。")
    }

    private suspend fun <T> execute(request: Request, maxBytes: Long = MAX_LLM_RESPONSE_BYTES,
        timeoutSeconds: Long = 30, decode: (JsonObject) -> T): T = suspendCancellableCoroutine { continuation ->
        val call = http.newCall(request)
        call.timeout().timeout(timeoutSeconds, TimeUnit.SECONDS)
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (continuation.isActive) continuation.resumeWithException(networkError(e))
            }

            override fun onResponse(call: Call, response: Response) {
                try {
                    val result = response.use {
                        if (!continuation.isActive) return
                        if (!it.isSuccessful) throw httpError(it.code)
                        val body = it.body ?: throw LlmException("服务返回了空响应。")
                        if (body.contentLength() > maxBytes) throw LlmException("服务响应超过大小限制，已停止读取。")
                        val buffer = Buffer()
                        val source = body.source()
                        while (true) {
                            if (!continuation.isActive) return
                            val count = source.read(buffer, minOf(8192L, maxBytes + 1 - buffer.size))
                            if (buffer.size > maxBytes) throw LlmException("服务响应超过大小限制，已停止读取。")
                            if (count == -1L) break
                        }
                        val element = JsonParser.parseString(buffer.readString(body.contentType()?.charset(Charsets.UTF_8)
                            ?: Charsets.UTF_8))
                        if (!element.isJsonObject) throw LlmException("服务响应不是兼容的 JSON 对象，请检查 API 地址。")
                        decode(element.asJsonObject)
                    }
                    if (continuation.isActive) continuation.resume(result)
                } catch (error: Exception) {
                    val safe = when (error) {
                        is LlmException -> error
                        is IOException -> networkError(error)
                        else -> LlmException("无法解析服务响应，请确认地址和模型兼容 OpenAI Chat Completions。")
                    }
                    if (continuation.isActive) continuation.resumeWithException(safe)
                }
            }
        })
    }

    private fun httpError(code: Int): LlmException = LlmException(when (code) {
        in 300..399 -> "服务要求重定向（HTTP $code），已阻止携带密钥跳转，请直接填写最终 API 地址。"
        400, 422 -> "请求不被服务接受（HTTP $code），请检查模型名称及 Chat Completions 兼容性。"
        401 -> "认证失败（HTTP 401），请检查 API Key。"
        402 -> "服务要求付费（HTTP 402），请检查账户余额。"
        403 -> "访问被拒绝（HTTP 403），请检查密钥权限及模型授权。"
        404 -> "接口或模型不存在（HTTP 404），请检查 API 前缀和模型名称。"
        405 -> "服务不支持该请求方式（HTTP 405），请检查 API 地址。"
        408, 504 -> "服务请求超时（HTTP $code），请稍后重试。"
        429 -> "服务限流或额度不足（HTTP 429），请检查配额后重试。"
        in 500..599 -> "模型服务暂时不可用（HTTP $code），请稍后重试。"
        else -> "模型服务请求失败（HTTP $code）。"
    })

    private fun networkError(error: IOException): LlmException = LlmException(when (error) {
        is InterruptedIOException -> "连接模型服务超时，请检查网络或稍后重试。"
        is UnknownHostException -> "无法解析模型服务地址，请检查 API 地址和网络。"
        is SSLException -> "模型服务的 TLS 证书验证失败，请检查服务地址和证书。"
        else -> "连接模型服务失败，请检查网络和 API 地址。"
    })
}
