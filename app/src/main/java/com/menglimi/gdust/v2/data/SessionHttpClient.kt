package com.menglimi.gdust.v2.data

import android.webkit.CookieManager
import com.menglimi.chaoxing.ChxCookieWriteException
import com.menglimi.chaoxing.ChxHttpExchange
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

internal data class SessionResponse(
    val status: Int,
    val body: String,
    val location: String?,
    val retryAfter: String? = null,
    val serverDate: String? = null,
)

/** 只向调用方许可的地址发送会话；跳转交业务层判断，取消时关闭请求与响应流。 */
internal class SessionHttpClient(
    private val allowedUrl: (HttpUrl) -> Boolean,
    private val checkSession: () -> Unit,
    private val cookieJar: CookieJar? = null,
    private val webCookieMutex: Mutex = Mutex(),
    private val requestControl: ChxRequestControl? = null,
    private val requestOwner: Any? = null,
) {
    // 学习通不允许 OkHttp 隐式重试；明确的 GET 传输恢复由共享 ChxHttpExchange 控制。
    private val client = OkHttpClient.Builder()
        .followRedirects(false).followSslRedirects(false).retryOnConnectionFailure(false)
        .cookieJar(CookieJar.NO_COOKIES)
        .connectTimeout(15, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS)
        .callTimeout(45, TimeUnit.SECONDS).build()

    suspend fun execute(request: Request): SessionResponse {
        require(allowedUrl(request.url)) { "不支持的服务地址" }
        val owner = requestOwner ?: Any()
        val send = suspend {
            checkSession()
            requestControl?.check(owner)
            val authenticated = withContext(Dispatchers.Main.immediate) {
                webCookieMutex.withLock {
                    checkSession()
                    requestControl?.check(owner)
                    val cookie = if (cookieJar == null) {
                        CookieManager.getInstance().getCookie(request.url.toString()).orEmpty()
                    } else cookieJar.loadForRequest(request.url).joinToString("; ") { "${it.name}=${it.value}" }
                    request.newBuilder().removeHeader("Cookie")
                        .apply { if (cookie.isNotBlank()) header("Cookie", cookie) }.build()
                }
            }
            val exchange = ChxHttpExchange(
                client = client,
                checkSession = checkSession,
                onResponseReceived = { response ->
                    val received = SessionResponse(response.status, response.body, response.location,
                        response.retryAfter, response.serverDate)
                    // 只锁存暂停；同一响应的 Set-Cookie 仍须写回，验证页可能依赖它。
                    requestControl?.recordResponse(owner, received)
                },
            )
            val received = exchange.execute(authenticated)
            if (received.cookies.isNotEmpty()) {
                try {
                    withContext(NonCancellable + Dispatchers.Main.immediate) {
                        webCookieMutex.withLock {
                            checkSession()
                            if (cookieJar == null) {
                                val manager = CookieManager.getInstance()
                                withTimeout(5_000L) {
                                    received.cookies.forEach { cookie ->
                                        checkSession()
                                        val accepted = suspendCancellableCoroutine<Boolean> { continuation ->
                                            manager.setCookie(request.url.toString(), cookie) {
                                                if (continuation.isActive) continuation.resume(it == true)
                                            }
                                        }
                                        if (!accepted) throw ChxCookieWriteException()
                                    }
                                }
                                manager.flush()
                            } else {
                                val headers = okhttp3.Headers.Builder().apply {
                                    received.cookies.forEach { add("Set-Cookie", it) }
                                }.build()
                                cookieJar.saveFromResponse(request.url, okhttp3.Cookie.parseAll(request.url, headers))
                            }
                            checkSession()
                        }
                    }
                } catch (failure: ChxCookieWriteException) {
                    requestControl?.recordFailure(owner, failure)
                    throw failure
                }
            }
            // 暂停响应仍先完成 Cookie 写回，再阻止业务结果继续传播。
            requestControl?.check(owner)
            SessionResponse(received.response.status, received.response.body, received.response.location,
                received.response.retryAfter, received.response.serverDate)
        }
        return requestControl?.execute(owner, checkSession, send) ?: send()
    }
}
