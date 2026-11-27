package com.menglimi.study.learning

import com.menglimi.chaoxing.ChxCookieWriteException
import com.menglimi.chaoxing.ChxHttpResult
import com.menglimi.chaoxing.requestPause
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

internal suspend fun awaitLearningCookieWrite(register: ((Boolean) -> Unit) -> Unit): Unit =
    suspendCancellableCoroutine { continuation ->
        register { success ->
            if (continuation.isActive) {
                if (success) continuation.resume(Unit)
                else continuation.resumeWithException(ChxCookieWriteException())
            }
        }
    }

/** 在宿主会话锁内调用；提交失败仍携带同一响应的验证码与冷却要求。 */
internal suspend fun commitLearningResponseCookies(
    received: ChxHttpResult,
    checkSession: () -> Unit,
    writeCookie: (String, (Boolean) -> Unit) -> Unit,
    flush: () -> Unit,
    now: Long = System.currentTimeMillis(),
) {
    if (received.cookies.isEmpty()) return
    fun cookieFailure(error: Exception): Exception = when (error) {
        is CancellationException, is LearningAccountChanged -> error
        else -> received.response.requestPause(now).let { pause ->
            ChxCookieWriteException(pause?.verificationRequired == true, pause?.retryAt ?: 0, error)
        }
    }
    var failure: Exception? = null
    try {
        received.cookies.forEach { cookie ->
            checkSession()
            // 写入交给 WebView 后等回调，退出在同锁下随后清除。
            withContext(NonCancellable) {
                try { awaitLearningCookieWrite { callback -> writeCookie(cookie, callback) } }
                catch (error: Exception) { throw cookieFailure(error) }
            }
        }
    } catch (error: Exception) {
        failure = error
        throw error
    } finally {
        try { flush() }
        catch (error: Exception) {
            if (error is CancellationException || error is LearningAccountChanged) throw error
            val primary = failure
            if (primary == null) throw cookieFailure(error)
            if (primary !== error) primary.addSuppressed(error)
        }
    }
}
