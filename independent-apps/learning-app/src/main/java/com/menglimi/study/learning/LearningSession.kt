package com.menglimi.study.learning

import android.content.Context
import android.webkit.CookieManager
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewDatabase
import com.menglimi.chaoxing.ChxApi
import com.menglimi.chaoxing.ChxCourseApi
import com.menglimi.chaoxing.ChxCourseOverview
import com.menglimi.chaoxing.ChxCourseWorkResult
import com.menglimi.chaoxing.ChxResponse
import com.menglimi.chaoxing.ChxSso
import com.menglimi.chaoxing.ChxWorkCourse
import com.menglimi.chaoxing.ChxWorkReader
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.CookieJar
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

internal fun currentLearningAccount(): String? =
    learningAccountFingerprint(CookieManager.getInstance().getCookie(ChxSso.HOME))

internal interface LearningBackend {
    suspend fun workCourses(): List<ChxWorkCourse>
    suspend fun works(courses: List<ChxWorkCourse>): List<ChxCourseWorkResult>
    suspend fun courses(): ChxCourseOverview
}

internal class LearningCoreBackend(guard: LearningSessionGuard, request: LearningRequest, account: String?) : LearningBackend {
    private val http = LearningSessionHttpClient(guard, request, account)
    private val api = ChxApi(execute = http::execute)
    private val reader = ChxWorkReader(api, checkSession = { guard.check(request) })
    override suspend fun workCourses(): List<ChxWorkCourse> = api.courses()
    override suspend fun works(courses: List<ChxWorkCourse>): List<ChxCourseWorkResult> = reader.read(courses)
    override suspend fun courses(): ChxCourseOverview = ChxCourseApi(execute = http::execute).load()
}

/** 来源逐 URL Cookie 客户端，附加两条数据流各自的请求版本检查。 */
internal class LearningSessionHttpClient(
    private val guard: LearningSessionGuard,
    private val token: LearningRequest,
    private val account: String?,
) {
    suspend fun execute(request: Request): ChxResponse {
        require(LearningUrls.allowedRequest(request.url)) { "不支持的学习通服务地址" }
        val caller = currentCoroutineContext()
        fun checkSession() { caller.ensureActive(); guard.check(token) }
        checkSession()
        val authenticated = withContext(Dispatchers.Main.immediate) {
            guard.mutex.withLock {
                checkSession()
                checkAccount()
                val cookie = CookieManager.getInstance().getCookie(request.url.toString()).orEmpty()
                request.newBuilder().removeHeader("Cookie")
                    .apply { if (cookie.isNotBlank()) header("Cookie", cookie) }.build()
            }
        }
        val received = client.newCall(authenticated).readResponse()
        checkSession()
        withContext(Dispatchers.Main.immediate) {
            guard.mutex.withLock {
                checkSession()
                checkAccount()
                if (received.cookies.isNotEmpty()) {
                    val manager = CookieManager.getInstance()
                    try {
                        received.cookies.forEach { cookie ->
                            checkSession()
                            // 写入交给 WebView 后等回调，退出在同锁下随后清除。
                            withContext(NonCancellable) {
                                suspendCancellableCoroutine<Unit> { continuation ->
                                    manager.setCookie(request.url.toString(), cookie) {
                                        if (continuation.isActive) continuation.resume(Unit)
                                    }
                                }
                            }
                        }
                    } finally { manager.flush() }
                }
                checkSession()
                checkAccount()
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
        val client: OkHttpClient = OkHttpClient.Builder().followRedirects(false).followSslRedirects(false)
            .cookieJar(CookieJar.NO_COOKIES).connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS).callTimeout(45, TimeUnit.SECONDS).build()
    }
}

@Suppress("DEPRECATION")
internal suspend fun clearLearningWebData(context: Context) {
    val failures = mutableListOf<String>()
    try {
        val cookies = CookieManager.getInstance()
        suspendCancellableCoroutine<Unit> { continuation ->
            cookies.removeAllCookies { if (continuation.isActive) continuation.resume(Unit) }
        }
        cookies.flush()
    } catch (cancelled: CancellationException) { throw cancelled }
    catch (_: Exception) { failures += "网页登录数据未能完全清除，请再次退出。" }
    try {
        WebStorage.getInstance().deleteAllData()
        WebViewDatabase.getInstance(context).apply { clearFormData(); clearHttpAuthUsernamePassword() }
        WebView(context).apply { clearCache(true); clearHistory(); clearFormData(); destroy() }
    } catch (cancelled: CancellationException) { throw cancelled }
    catch (_: Exception) { failures += "网页缓存未能完全清除，请再次退出。" }
    if (failures.isNotEmpty()) throw IOException(failures.joinToString("\n"))
}
