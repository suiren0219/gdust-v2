package com.menglimi.gdust.v2.data

import android.webkit.CookieManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.Call
import okhttp3.Callback
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

internal data class SessionResponse(val status: Int, val body: String, val location: String?)

/** 只向调用方许可的地址发送会话；跳转交业务层判断，取消时关闭请求与响应流。 */
internal class SessionHttpClient(
    private val allowedUrl: (HttpUrl) -> Boolean,
    private val checkSession: () -> Unit,
    private val cookieJar: CookieJar? = null,
    private val webCookieMutex: Mutex = Mutex(),
) {
    private val client = OkHttpClient.Builder()
        .followRedirects(false).followSslRedirects(false)
        .cookieJar(cookieJar ?: CookieJar.NO_COOKIES)
        .connectTimeout(15, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS)
        .callTimeout(45, TimeUnit.SECONDS).build()

    suspend fun execute(request: Request): SessionResponse {
        require(allowedUrl(request.url)) { "不支持的服务地址" }
        checkSession()
        val authenticated = if (cookieJar == null) withContext(Dispatchers.Main.immediate) {
            webCookieMutex.withLock {
                checkSession()
                val cookie = CookieManager.getInstance().getCookie(request.url.toString()).orEmpty()
                request.newBuilder().removeHeader("Cookie")
                    .apply { if (cookie.isNotBlank()) header("Cookie", cookie) }.build()
            }
        } else request.newBuilder().removeHeader("Cookie").build()
        val response = client.newCall(authenticated).readResponse()
        if (cookieJar == null && response.cookies.isNotEmpty()) {
            withContext(NonCancellable + Dispatchers.Main.immediate) {
                webCookieMutex.withLock {
                    checkSession()
                    val manager = CookieManager.getInstance()
                    // 与退出清理共用锁；已发起的 Cookie 写入完成后，退出才能清空。
                    withTimeout(5_000L) {
                        response.cookies.forEach { cookie ->
                            checkSession()
                            suspendCancellableCoroutine<Unit> { continuation ->
                                manager.setCookie(request.url.toString(), cookie) {
                                    if (continuation.isActive) continuation.resume(Unit)
                                }
                            }
                        }
                    }
                    manager.flush()
                    checkSession()
                }
            }
        }
        checkSession()
        return response.result
    }

    private data class Received(val result: SessionResponse, val cookies: List<String>)

    private suspend fun Call.readResponse(): Received = suspendCancellableCoroutine { continuation ->
        continuation.invokeOnCancellation { cancel() }
        enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (continuation.isActive) continuation.resumeWithException(e)
            }

            override fun onResponse(call: Call, response: Response) {
                try {
                    val result = response.use {
                        // 列表响应过大通常是非预期页面；不要截断后当作成功的空列表。
                        val body = it.peekBody(4L * 1024 * 1024 + 1).bytes()
                        check(body.size <= 4 * 1024 * 1024) { "服务返回的数据过大，请在网页查看" }
                        val charset = it.body?.contentType()?.charset(Charsets.UTF_8) ?: Charsets.UTF_8
                        Received(SessionResponse(it.code, body.toString(charset), it.header("Location")),
                            it.headers("Set-Cookie"))
                    }
                    if (continuation.isActive) continuation.resume(result)
                } catch (e: Exception) {
                    if (continuation.isActive) continuation.resumeWithException(e)
                }
            }
        })
    }
}
