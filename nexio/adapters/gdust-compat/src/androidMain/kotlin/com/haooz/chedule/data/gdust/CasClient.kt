package com.haooz.chedule.data.gdust

import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * 广科统一身份认证（CAS）客户端。
 *
 * 协议（2026-11-07 从 CAS 前端 bundle 逆向 + Python 全链实测）：
 * ① GET  /cas-api/cas/loginCode → {code:0, data:{codeUrl:"data:image/jpeg;base64,...", uuid}}
 * ② POST /cas-api/cas/loginByAccount {loginName, loginPwd, code, uuid}（明文 JSON，无加密）
 *    → {code:0, data:"GKCAS..."} data 即 ticket
 * ③ ticket 交给 PortalClient 换 TOKEN（走 query 参数）。
 * 验证码错误返回 code=30001 msg="验证码错误"，不锁号，重取重试即可。
 */
class CasClient(
    private val http: OkHttpClient = defaultHttp(),
) {

    companion object {
        const val BASE = "https://cas.gdust.edu.cn"
        const val CAS_API = "$BASE/cas-api"

        fun defaultHttp(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build()
    }

    data class Captcha(val base64Jpeg: String, val uuid: String)

    /** 取验证码图（base64 内嵌 JPEG）与 uuid，二者成对使用。 */
    suspend fun fetchCaptcha(): Captcha = withContext(Dispatchers.IO) {
        val req = Request.Builder()
            .url("$CAS_API/cas/loginCode")
            .header("Referer", "$BASE/cas/login")
            .get()
            .build()
        http.newCall(req).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            val obj = JsonParser.parseString(body).asJsonObject
            check(obj.get("code").asInt == 0) { "取验证码失败: ${obj.get("msg")?.takeIf { !it.isJsonNull }?.asString}" }
            val data = obj.getAsJsonObject("data")
            val codeUrl = data.get("codeUrl").asString
            Captcha(
                base64Jpeg = codeUrl.substringAfter("base64,", ""),
                uuid = data.get("uuid").asString,
            )
        }
    }

    /**
     * 账号密码登录 CAS，返回 ticket（GKCAS...）。
     * @throws CasException code!=0 时抛出（如验证码错误 30001）
     */
    suspend fun loginByAccount(
        loginName: String,
        loginPwd: String,
        captchaCode: String,
        captchaUuid: String,
    ): String = withContext(Dispatchers.IO) {
        val json = """{"loginName":${jsonStr(loginName)},"loginPwd":${jsonStr(loginPwd)},""" +
            """"code":${jsonStr(captchaCode)},"uuid":${jsonStr(captchaUuid)}}"""
        val req = Request.Builder()
            .url("$CAS_API/cas/loginByAccount")
            .header("Referer", "$BASE/cas/login")
            .header("Origin", BASE)
            .post(json.toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()
        http.newCall(req).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            val obj = JsonParser.parseString(body).asJsonObject
            val code = obj.get("code").asInt
            val msg = obj.get("msg")?.takeIf { !it.isJsonNull }?.asString.orEmpty()
            if (code != 0) throw CasException(code, msg)
            val data = obj.get("data")
            check(data != null && data.isJsonPrimitive) { "登录响应缺少 ticket: $body" }
            data.asString
        }
    }

    /** SSE 扫码登录端点（扫码方案开工时接 OkHttp-SSE）：返回完整订阅 URL。 */
    fun sseUrl(): String = "$CAS_API/sse/subscribe"

    /** 二维码内容：钉钉扫码打开的授权页。 */
    fun mobieAuthUrl(clientId: String): String = "$BASE/cas/mobieAuth?clientId=$clientId"

    /**
     * 订阅 CAS 扫码事件流（OkHttp-SSE），事件转 Flow。
     * HELLO(data=clientId) → 生成二维码跳钉钉授权 → LOGIN_SUCCESS_TICKET(data=ticket)。
     * NEED_TO_BIND 表示钉钉账号未绑定校园账号（引导走授权页绑定）。
     */
    fun subscribeSse(): kotlinx.coroutines.flow.Flow<GdustSseEvent> =
        kotlinx.coroutines.flow.callbackFlow {
            val request = okhttp3.Request.Builder()
                .url("$BASE/cas-api/sse/subscribe")
                .header("Accept", "text/event-stream")
                .header("Referer", "$BASE/cas/login")
                .get()
                .build()
            val listener = object : okhttp3.sse.EventSourceListener() {
                override fun onOpen(eventSource: okhttp3.sse.EventSource, response: okhttp3.Response) {}

                override fun onEvent(
                    eventSource: okhttp3.sse.EventSource,
                    id: String?,
                    type: String?,
                    data: String,
                ) {
                    when (type) {
                        "HELLO" -> trySend(GdustSseEvent.Hello(data))
                        "LOGIN_SUCCESS_TICKET" -> {
                            trySend(GdustSseEvent.Ticket(data))
                            close()
                        }
                        "NEED_TO_BIND" -> trySend(GdustSseEvent.NeedBind(data))
                    }
                }

                override fun onFailure(eventSource: okhttp3.sse.EventSource, t: Throwable?, response: okhttp3.Response?) {
                    trySend(GdustSseEvent.Error(t?.message ?: "SSE 连接失败 HTTP ${response?.code ?: "?"}"))
                    close()
                }

                override fun onClosed(eventSource: okhttp3.sse.EventSource) {
                    close()
                }
            }
            val es = okhttp3.sse.EventSources.createFactory(http).newEventSource(request, listener)
            awaitClose { es.cancel() }
        }

    private fun jsonStr(s: String): String =
        "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
}

class CasException(val code: Int, message: String) : Exception(message)


/** CAS 扫码 SSE 事件。 */
sealed class GdustSseEvent {
    data class Hello(val clientId: String) : GdustSseEvent()
    data class Ticket(val ticket: String) : GdustSseEvent()
    data class NeedBind(val unionId: String) : GdustSseEvent()
    data class Error(val message: String) : GdustSseEvent()
}
