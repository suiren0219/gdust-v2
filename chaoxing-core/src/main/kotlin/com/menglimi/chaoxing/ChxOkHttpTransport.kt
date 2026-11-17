package com.menglimi.chaoxing

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * 可直接用于 JVM 的传输层；CookieJar 由接入方管理，并须支持并发访问。
 * 每跳重新按目标 URL 选择 Cookie，忽略请求上预附的 Cookie，不自动跟随任何重定向。
 * checkSession 可在账号切换后抛 CancellationException；持久 Cookie 的原子隔离由 CookieJar 负责。
 * 注入的 client 仍由接入方持有；拦截器不得自行跟随跳转或改变认证请求的目标。
 */
class ChxOkHttpTransport(
    private val cookieJar: CookieJar,
    client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .callTimeout(45, TimeUnit.SECONDS)
        .build(),
    private val isAllowedUrl: (HttpUrl) -> Boolean = ChxApi::isAllowedUrl,
    private val checkSession: () -> Unit = {},
) {
    // 手工 Cookie 读写保证被取消/失效的响应不经 OkHttp 的 BridgeInterceptor 写回 Cookie。
    private val client = client.newBuilder()
        .followRedirects(false)
        .followSslRedirects(false)
        .retryOnConnectionFailure(false)
        .cookieJar(CookieJar.NO_COOKIES)
        .build()

    suspend fun execute(request: Request): ChxResponse {
        require(isAllowedUrl(request.url)) { "不支持的学习通服务地址" }
        currentCoroutineContext().ensureActive()
        checkSession()
        val cookies = cookieJar.loadForRequest(request.url).filter {
            it.matches(request.url) && it.expiresAt > System.currentTimeMillis()
        }
        val authenticated = request.newBuilder().removeHeader("Cookie")
            .apply {
                if (cookies.isNotEmpty()) header("Cookie", cookies.joinToString("; ") { "${it.name}=${it.value}" })
            }.build()
        currentCoroutineContext().ensureActive()
        checkSession()
        val response = client.newCall(authenticated).readResponse()
        currentCoroutineContext().ensureActive()
        checkSession()
        if (response.cookies.isNotEmpty()) cookieJar.saveFromResponse(request.url, response.cookies)
        currentCoroutineContext().ensureActive()
        checkSession()
        return response.result
    }

    private data class Received(val result: ChxResponse, val cookies: List<Cookie>)

    private suspend fun Call.readResponse(): Received = suspendCancellableCoroutine { continuation ->
        continuation.invokeOnCancellation { cancel() }
        enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (continuation.isActive) continuation.resumeWithException(e)
            }

            override fun onResponse(call: Call, response: Response) {
                try {
                    val result = response.use {
                        // 不截断后继续解析，避免把非预期大页面误认为有效清单。
                        val body = it.peekBody(4L * 1024 * 1024 + 1).bytes()
                        if (body.size > 4 * 1024 * 1024) {
                            throw ChxCourseReadException("服务返回的数据过大，请在网页查看。")
                        }
                        val charset = it.body?.contentType()?.charset(Charsets.UTF_8) ?: Charsets.UTF_8
                        Received(ChxResponse(it.code, body.toString(charset), it.header("Location"),
                            it.header("Retry-After"), it.header("Date")),
                            Cookie.parseAll(it.request.url, it.headers))
                    }
                    if (continuation.isActive) continuation.resume(result)
                } catch (e: Exception) {
                    if (continuation.isActive) continuation.resumeWithException(e)
                }
            }
        })
    }
}
