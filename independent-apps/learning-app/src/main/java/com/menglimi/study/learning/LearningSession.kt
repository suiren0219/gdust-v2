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
import com.menglimi.chaoxing.ChxHttpExchange
import com.menglimi.chaoxing.ChxResponse
import com.menglimi.chaoxing.ChxSso
import com.menglimi.chaoxing.ChxWorkCourse
import com.menglimi.chaoxing.ChxWorkReader
import com.menglimi.chaoxing.requestPause
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.CookieJar
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

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
    suspend fun execute(request: Request): ChxResponse = guard.requests.execute(token, { guard.check(token) }) {
        executeOnce(request)
    }

    private suspend fun executeOnce(request: Request): ChxResponse {
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
        val received = ChxHttpExchange(client, checkSession = ::checkSession, onResponseReceived = { response ->
            if (guard.isCurrent(token)) response.requestPause()?.let { guard.requests.record(it.exception(), token) }
        }).execute(authenticated)
        checkSession()
        withContext(Dispatchers.Main.immediate) {
            guard.mutex.withLock {
                checkSession()
                checkAccount()
                if (received.cookies.isNotEmpty()) {
                    val manager = CookieManager.getInstance()
                    commitLearningResponseCookies(received, ::checkSession,
                        writeCookie = { cookie, callback ->
                            manager.setCookie(request.url.toString(), cookie) { callback(it) }
                        }, flush = { manager.flush() })
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
    private companion object {
        val client: OkHttpClient = OkHttpClient.Builder().followRedirects(false).followSslRedirects(false)
            .retryOnConnectionFailure(false).cookieJar(CookieJar.NO_COOKIES).connectTimeout(15, TimeUnit.SECONDS)
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
