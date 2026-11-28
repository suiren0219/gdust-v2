package com.menglimi.kedian.learning

import android.webkit.CookieManager
import com.menglimi.chaoxing.ChxResponse
import com.menglimi.chaoxing.ChxSso
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.CookieJar
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** 所有快照、Cookie 读写及清除使用同一把锁；代际检查也传给核心读取器。 */
internal class LearningSessionGuard {
    val mutex = Mutex()
    @Volatile private var generation = 0L

    fun current(): Long = generation

    fun check(expected: Long) {
        if (generation != expected) throw CancellationException("学习通会话已变更")
    }

    /** 仅在 mutex 内调用，防止 Set-Cookie 已提交但清除先完成。 */
    fun invalidate(): Long = (++generation)
}

internal class LearningAccountChanged : Exception("学习通账号已变化，请重新检查登录状态。")

/** UID 只作为缓存归属指纹，绝不是已登录证据；原始 Cookie 不进入业务缓存。 */
internal fun learningAccountFingerprint(header: String?): String? {
    val identities = ChxSso.cookiePairs(header)
        .filter { it.first in setOf("_uid", "UID") && it.second.isNotBlank() }
        .map { it.second }.distinct()
    if (identities.size != 1) return null
    return MessageDigest.getInstance("SHA-256").digest(identities.single().toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
}

internal fun currentLearningAccount(): String? =
    learningAccountFingerprint(CookieManager.getInstance().getCookie(ChxSso.HOME))

internal class LearningSessionHttpClient(
    private val guard: LearningSessionGuard,
    private val generation: Long,
    private val account: String?,
) {
    suspend fun execute(request: Request): ChxResponse {
        require(LearningUrls.allowedRequest(request.url)) { "不支持的学习通服务地址" }
        val caller = currentCoroutineContext()
        fun checkSession() {
            caller.ensureActive()
            guard.check(generation)
        }
        checkSession()
        val authenticated = withContext(Dispatchers.Main.immediate) {
            guard.mutex.withLock {
                checkSession()
                checkAccount()
                // 为本次 URL 单独取 Cookie，绝不把其他主机或路径的 Cookie 拼进请求。
                val cookie = CookieManager.getInstance().getCookie(request.url.toString()).orEmpty()
                request.newBuilder().removeHeader("Cookie")
                    .apply { if (cookie.isNotBlank()) header("Cookie", cookie) }.build()
            }
        }
        val received = client.newCall(authenticated).readResponse()
        checkSession()
        if (received.cookies.isNotEmpty()) {
            withContext(Dispatchers.Main.immediate) {
                guard.mutex.withLock {
                    checkSession()
                    checkAccount()
                    val manager = CookieManager.getInstance()
                    try {
                        received.cookies.forEach { cookie ->
                            checkSession()
                            // 写入一旦交给 WebView，必须等回调；退出只能在同锁下随后清除。
                            withContext(NonCancellable) {
                                suspendCancellableCoroutine<Unit> { continuation ->
                                    manager.setCookie(request.url.toString(), cookie) {
                                        if (continuation.isActive) continuation.resume(Unit)
                                    }
                                }
                            }
                        }
                    } finally {
                        manager.flush()
                    }
                    checkSession()
                    checkAccount()
                }
            }
        }
        checkSession()
        return received.response
    }

    private fun checkAccount() {
        if (currentLearningAccount() != account) throw LearningAccountChanged()
    }

    private data class Received(val response: ChxResponse, val cookies: List<String>)

    private suspend fun Call.readResponse(): Received = suspendCancellableCoroutine { continuation ->
        continuation.invokeOnCancellation { cancel() }
        enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (continuation.isActive) continuation.resumeWithException(e)
            }

            override fun onResponse(call: Call, response: Response) {
                try {
                    val received = response.use {
                        val body = it.peekBody(MAX_BODY_BYTES + 1).bytes()
                        if (body.size > MAX_BODY_BYTES) throw IOException("学习通响应过大")
                        val charset = it.body?.contentType()?.charset(Charsets.UTF_8) ?: Charsets.UTF_8
                        Received(ChxResponse(it.code, body.toString(charset), it.header("Location")), it.headers("Set-Cookie"))
                    }
                    if (continuation.isActive) continuation.resume(received)
                } catch (failure: Exception) {
                    if (continuation.isActive) continuation.resumeWithException(failure)
                }
            }
        })
    }

    private companion object {
        const val MAX_BODY_BYTES = 4L * 1024 * 1024
        val client: OkHttpClient = OkHttpClient.Builder()
            .followRedirects(false)
            .followSslRedirects(false)
            .cookieJar(CookieJar.NO_COOKIES)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .callTimeout(45, TimeUnit.SECONDS)
            .build()
    }
}
