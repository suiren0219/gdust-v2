package com.menglimi.kedian.learning

import android.content.Context
import android.webkit.CookieManager
import android.webkit.WebStorage
import com.menglimi.chaoxing.ChxApi
import com.menglimi.chaoxing.ChxAuthFailed
import com.menglimi.chaoxing.ChxCourseReadException
import com.menglimi.chaoxing.ChxCourseWorkResult
import com.menglimi.chaoxing.ChxWorkReader
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

class LearningController(context: Context) {
    private val mutableState = MutableStateFlow(LearningUiState())
    val state: StateFlow<LearningUiState> = mutableState.asStateFlow()

    private val guard = LearningSessionGuard()
    private val refreshMutex = Mutex()
    private val store = LearningStore(context)
    private var cacheLoaded = false
    private var snapshot: LearningSnapshot? = null
    private var activeRefresh: Job? = null
    private val browsers = mutableSetOf<() -> Unit>()

    /** 仅读本地加密快照，不发请求，也不把历史 connected 当成有效登录。 */
    suspend fun reload() = withContext(Dispatchers.Main.immediate) {
        val generation = guard.current()
        guard.mutex.withLock {
            guard.check(generation)
            loadCacheLocked()
            discardOtherAccountLocked(currentLearningAccount())
        }
    }

    /** 打开登录页前调用。切断旧请求/旧快照，但保留 Cookie 让正常网页自行登录或换号。 */
    suspend fun prepareLogin() = withContext(NonCancellable + Dispatchers.Main.immediate) {
        guard.mutex.withLock {
            invalidateLocked()
            mutableState.value = LearningUiState()
            val cacheError = clearCacheLocked()
            if (cacheError != null) mutableState.value = mutableState.value.copy(error = cacheError)
        }
    }

    suspend fun refresh(): Unit = coroutineScope {
        withContext(Dispatchers.Main.immediate) {
            val generation = guard.current()
            refreshMutex.withLock {
                guard.check(generation)
                val operation = currentCoroutineContext()
                var account: String? = null
                var courseSessionVerified = false
                try {
                    guard.mutex.withLock {
                        operation.ensureActive()
                        guard.check(generation)
                        activeRefresh = operation[Job]
                        loadCacheLocked()
                        account = currentLearningAccount()
                        discardOtherAccountLocked(account)
                        mutableState.value = mutableState.value.copy(connected = false, loading = true, error = null)
                    }
                    val http = LearningSessionHttpClient(guard, generation, account)
                    val api = ChxApi(execute = http::execute)
                    // 首页正向证据 + 可识别课程列表独立探活；URL 变化、Cookie 存在均不算登录成功。
                    val courses = api.courses()
                    operation.ensureActive()
                    guard.check(generation)
                    courseSessionVerified = true
                    val results = ChxWorkReader(api, checkSession = {
                        operation.ensureActive()
                        guard.check(generation)
                    }).read(courses)
                    guard.mutex.withLock {
                        operation.ensureActive()
                        guard.check(generation)
                        if (currentLearningAccount() != account) throw LearningAccountChanged()
                        val merged = mergeHomework(snapshot?.courses.orEmpty(), results, System.currentTimeMillis())
                        val authFailed = results.any { it is ChxCourseWorkResult.Failure && it.error is ChxAuthFailed }
                        var error: String? = when {
                            authFailed -> "学习通登录已失效，请重新登录。已保留的作业可能不是最新结果。"
                            merged.failedCourses > 0 -> "${merged.failedCourses} 门课程读取失败，已保留这些课程上次读取的结果；未读课程不能确认是否有作业。"
                            else -> null
                        }
                        // 无唯一账号指纹时只保留内存结果，不跨启动保存或合并身份不明的数据。
                        snapshot = account?.let { LearningSnapshot(it, merged.courses, merged.failedCourses) }
                        if (snapshot != null) {
                            try {
                                withContext(Dispatchers.IO) { store.write(checkNotNull(snapshot)) }
                            } catch (cancelled: CancellationException) {
                                throw cancelled
                            } catch (_: Exception) {
                                error = listOfNotNull(error, "本次作业未能保存到加密缓存。").joinToString("\n")
                            }
                        }
                        operation.ensureActive()
                        guard.check(generation)
                        mutableState.value = LearningUiState(
                            connected = !authFailed,
                            works = merged.works,
                            failedCourses = merged.failedCourses,
                            error = error,
                            updatedAt = merged.updatedAt,
                        )
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (failure: Exception) {
                    guard.mutex.withLock {
                        operation.ensureActive()
                        guard.check(generation)
                        if (currentLearningAccount() != account || failure is LearningAccountChanged) {
                            clearCacheLocked()
                            mutableState.value = LearningUiState(error = "学习通账号已变化，请重新检查登录状态。")
                        } else {
                            mutableState.value = mutableState.value.copy(
                                connected = failure !is ChxAuthFailed && (courseSessionVerified || mutableState.value.connected),
                                error = when (failure) {
                                    is ChxAuthFailed -> "学习通登录已失效，请重新登录。当前显示的是上次读取的结果。"
                                    is ChxCourseReadException -> failure.message ?: "暂时无法读取学习通，请稍后重试。"
                                    else -> "暂时无法读取学习通，请检查网络后重试。已保留上次读取的结果。"
                                },
                            )
                        }
                    }
                } finally {
                    withContext(NonCancellable + Dispatchers.Main.immediate) {
                        guard.mutex.withLock {
                            if (guard.current() == generation) {
                                activeRefresh = null
                                mutableState.value = mutableState.value.copy(loading = false)
                            }
                        }
                    }
                }
            }
        }
    }

    /** 清理是一个不可中断的事务，返回时 Cookie 清除 callback、flush 与 WebStorage 清理均已执行。 */
    suspend fun disconnect() = withContext(NonCancellable + Dispatchers.Main.immediate) {
        guard.mutex.withLock {
            invalidateLocked()
            mutableState.value = LearningUiState()
            val errors = mutableListOf<String>()
            clearCacheLocked()?.let(errors::add)
            try {
                val cookies = CookieManager.getInstance()
                suspendCancellableCoroutine<Unit> { continuation ->
                    cookies.removeAllCookies { if (continuation.isActive) continuation.resume(Unit) }
                }
                cookies.flush()
            } catch (_: Exception) {
                errors += "网页登录数据未能完全清除，请再次退出。"
            }
            try {
                WebStorage.getInstance().deleteAllData()
            } catch (_: Exception) {
                errors += "网页存储未能完全清除，请再次退出。"
            }
            mutableState.value = LearningUiState(error = errors.takeIf { it.isNotEmpty() }?.joinToString("\n"))
        }
    }

    fun clearError() {
        mutableState.update { it.copy(error = null) }
    }

    /** 仅供本模块原生 WebView 注册退出清理；主线程调用。 */
    internal fun attachBrowser(onInvalidated: () -> Unit): Long {
        browsers += onInvalidated
        return guard.current()
    }

    internal fun detachBrowser(onInvalidated: () -> Unit) {
        browsers -= onInvalidated
    }

    internal fun browserIsCurrent(generation: Long): Boolean = guard.current() == generation

    private fun invalidateLocked() {
        guard.invalidate()
        activeRefresh?.cancel(CancellationException("学习通会话已变更"))
        activeRefresh = null
        val previousBrowsers = browsers.toList()
        browsers.clear()
        previousBrowsers.forEach { close -> runCatching { close() } }
    }

    private suspend fun loadCacheLocked() {
        if (cacheLoaded) return
        cacheLoaded = true
        try {
            snapshot = withContext(Dispatchers.IO) { store.read() }
            if (snapshot != null && snapshot?.account != currentLearningAccount()) {
                val error = clearCacheLocked()
                mutableState.value = LearningUiState(error = error)
                return
            }
            snapshot?.let {
                val merged = HomeworkMerge(it.courses, it.failedCourses)
                mutableState.value = LearningUiState(
                    works = merged.works,
                    failedCourses = it.failedCourses,
                    updatedAt = merged.updatedAt,
                    error = "当前为上次读取的结果，刷新后确认学习通登录状态。",
                )
            }
        } catch (cancelled: CancellationException) {
            cacheLoaded = false
            throw cancelled
        } catch (_: Exception) {
            snapshot = null
            mutableState.value = LearningUiState(error = "加密缓存暂时无法读取，请重新刷新。")
        }
    }

    private suspend fun discardOtherAccountLocked(account: String?) {
        if (snapshot != null && snapshot?.account != account) {
            val error = clearCacheLocked()
            mutableState.value = LearningUiState(error = error)
        }
    }

    private suspend fun clearCacheLocked(): String? {
        snapshot = null
        cacheLoaded = true
        return try {
            withContext(Dispatchers.IO) { store.clear() }
            null
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            "旧作业缓存未能完全清理，本次不会显示旧缓存。"
        }
    }
}
