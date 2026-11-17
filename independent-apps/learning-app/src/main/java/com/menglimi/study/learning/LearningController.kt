package com.menglimi.study.learning

import android.content.Context
import com.menglimi.chaoxing.ChxAuthFailed
import com.menglimi.chaoxing.ChxCourseOverview
import com.menglimi.chaoxing.ChxWorkCourse
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** 作业与课程独立刷新；会话代际、账号和请求版本在提交 Cookie/缓存/UI 前同时检查。 */
class LearningController internal constructor(
    private val store: LearningPersistence,
    private val accountProvider: () -> String?,
    private val clearWebData: suspend () -> Unit,
    private val backendFactory: (LearningSessionGuard, LearningRequest, String?) -> LearningBackend,
    private val mainDispatcher: CoroutineDispatcher = Dispatchers.Main.immediate,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val now: () -> Long = System::currentTimeMillis,
) {
    constructor(context: Context) : this(LearningStore(context), ::currentLearningAccount,
        { clearLearningWebData(context.applicationContext) }, ::LearningCoreBackend)

    private val mutableState = MutableStateFlow(LearningUiState())
    val state = mutableState.asStateFlow()
    private val mutableCourses = MutableStateFlow(CoursesUiState())
    val courses = mutableCourses.asStateFlow()
    private val guard = LearningSessionGuard()
    private val active = mutableMapOf<LearningLane, Job>()
    private val browsers = mutableSetOf<() -> Unit>()
    private var cacheLoaded = false
    private var snapshot: LearningSnapshot? = null
    private var accountKnown = false
    private var boundAccount: String? = null

    /** 只读本地，不发探活或真实登录请求；缓存不等于登录成功。 */
    suspend fun reload() = withContext(mainDispatcher) {
        val generation = guard.current()
        guard.mutex.withLock {
            guard.check(generation)
            synchronizeAccountLocked()
            loadCacheLocked()
        }
    }

    suspend fun verifyFromWeb(courseProgress: Boolean): String? {
        if (courseProgress) {
            refreshCourses()
            val result = courses.value
            return result.error ?: result.progressError ?: if (result.connected) null
                else "尚未确认课程进度，请在网页完成登录或验证后重试。"
        }
        refresh()
        val result = state.value
        return result.error ?: if (result.connected && result.failedCourses == 0) null
            else "作业尚未读取完整，请在对应课程网页完成验证后重试。"
    }

    suspend fun refresh(): Unit = refreshWorks(null)
    suspend fun retryCourse(courseKey: String): Unit = refreshWorks(courseKey)

    private suspend fun refreshWorks(retryKey: String?): Unit = coroutineScope {
        withContext(mainDispatcher) {
            val operation = currentCoroutineContext()
            val generation = guard.current()
            var token: LearningRequest? = null
            var account: String? = null
            var verified = false
            try {
                var retry: ChxWorkCourse? = null
                guard.mutex.withLock {
                    operation.ensureActive()
                    guard.check(generation)
                    synchronizeAccountLocked()
                    loadCacheLocked()
                    if (retryKey != null) {
                        retry = mutableState.value.failures.firstOrNull { it.course.cacheKey() == retryKey }?.course
                        if (retry == null) return@withContext
                    }
                    account = accountProvider()
                    token = beginLocked(LearningLane.WORK, operation[Job])
                    mutableState.value = mutableState.value.copy(loading = true, retryingCourse = retryKey, error = null)
                }
                val request = checkNotNull(token)
                val backend = backendFactory(guard, request, account)
                // 单课重试也先验证当前首页及课程身份，绝不根据 Cookie 存在判登录成功。
                val discovered = backend.workCourses()
                operation.ensureActive()
                guard.check(request)
                verified = true
                val targets = if (retry == null || account == null) discovered else {
                    listOf(discovered.firstOrNull { it.cacheKey() == retry?.cacheKey() }
                        ?: throw com.menglimi.chaoxing.ChxCourseReadException("该课程已不在当前课程页，请刷新完整作业列表。"))
                }
                val results = backend.works(targets)
                guard.mutex.withLock {
                    operation.ensureActive()
                    guard.check(request)
                    checkAccount(account)
                    val old = if (account != null) snapshot?.courses.orEmpty() else emptyList()
                    val merged = mergeHomework(old, results, now(), partial = retryKey != null && account != null)
                    val authFailed = merged.courses.any { it.authRequired }
                    snapshot = account?.let { (snapshot ?: LearningSnapshot(it)).copy(courses = merged.courses, workLoaded = true) }
                    val cacheError = saveLocked()
                    operation.ensureActive()
                    guard.check(request)
                    checkAccount(account)
                    mutableState.value = LearningUiState(
                        connected = !authFailed, works = merged.works, failedCourses = merged.failedCourses,
                        failures = merged.failures, courseCount = merged.courses.size,
                        error = listOfNotNull(when {
                            authFailed -> "学习通登录已失效，请重新登录。未更新的课程保留上次记录。"
                            merged.failedCourses > 0 -> "${merged.failedCourses} 门课程读取失败，未读课程不能确认是否有作业。"
                            else -> null
                        }, cacheError).takeIf { it.isNotEmpty() }?.joinToString("\n"),
                        updatedAt = merged.updatedAt, hasSnapshot = true,
                        fromCache = merged.courses.any { it.failure != null && it.updatedAt > 0 },
                    )
                    if (authFailed) mutableCourses.update { it.copy(connected = false) }
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) {
                token?.let { request -> guard.mutex.withLock {
                    operation.ensureActive()
                    guard.check(request)
                    if (failure is LearningAccountChanged || accountProvider() != account) {
                        accountChangedLocked()
                    } else {
                        mutableState.update { it.copy(
                            connected = failure !is ChxAuthFailed && (verified || it.connected),
                            error = learningFailureMessage(failure), fromCache = it.hasSnapshot,
                        ) }
                        if (failure is ChxAuthFailed) mutableCourses.update { it.copy(connected = false) }
                    }
                } }
            } finally {
                withContext(NonCancellable + mainDispatcher) { guard.mutex.withLock {
                    token?.takeIf(guard::isCurrent)?.let {
                        active.remove(LearningLane.WORK)
                        mutableState.update { value -> value.copy(loading = false, retryingCourse = null) }
                    }
                } }
            }
        }
    }

    suspend fun refreshCourses(): Unit = coroutineScope {
        withContext(mainDispatcher) {
            val operation = currentCoroutineContext()
            val generation = guard.current()
            var token: LearningRequest? = null
            var account: String? = null
            try {
                guard.mutex.withLock {
                    operation.ensureActive()
                    guard.check(generation)
                    synchronizeAccountLocked()
                    loadCacheLocked()
                    account = accountProvider()
                    token = beginLocked(LearningLane.COURSES, operation[Job])
                    mutableCourses.update { it.copy(loading = true, error = null) }
                }
                val request = checkNotNull(token)
                val overview = backendFactory(guard, request, account).courses()
                guard.mutex.withLock {
                    operation.ensureActive()
                    guard.check(request)
                    checkAccount(account)
                    snapshot = account?.let { (snapshot ?: LearningSnapshot(it)).copy(overview = overview) }
                    val cacheError = saveLocked()
                    operation.ensureActive()
                    guard.check(request)
                    checkAccount(account)
                    mutableCourses.value = coursesState(overview, cached = false).copy(error = cacheError)
                    if (overview.progressAuthRequired) mutableState.update { it.copy(connected = false) }
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) {
                token?.let { request -> guard.mutex.withLock {
                    operation.ensureActive()
                    guard.check(request)
                    if (failure is LearningAccountChanged || accountProvider() != account) {
                        accountChangedLocked()
                    } else {
                        mutableCourses.update { it.copy(connected = false, fromCache = it.hasSnapshot,
                            error = learningFailureMessage(failure)) }
                        if (failure is ChxAuthFailed) mutableState.update { it.copy(connected = false) }
                    }
                } }
            } finally {
                withContext(NonCancellable + mainDispatcher) { guard.mutex.withLock {
                    token?.takeIf(guard::isCurrent)?.let {
                        active.remove(LearningLane.COURSES)
                        mutableCourses.update { value -> value.copy(loading = false) }
                    }
                } }
            }
        }
    }

    suspend fun prepareLogin() = clearData(removeCookies = false, resetAccount = true)
    suspend fun clearCache() = clearData(removeCookies = false)
    suspend fun disconnect() = clearData(removeCookies = true)

    /** 清除一旦开始完整执行；退出后 Cookie callback、flush、WebStorage 和快照都已处理。 */
    private suspend fun clearData(removeCookies: Boolean, resetAccount: Boolean = false) {
        val caller = currentCoroutineContext()
        caller.ensureActive()
        withContext(NonCancellable + mainDispatcher) {
            guard.mutex.withLock {
                invalidateLocked()
                snapshot = null
                cacheLoaded = true
                mutableState.value = LearningUiState()
                mutableCourses.value = CoursesUiState()
                val errors = mutableListOf<String>()
                clearStoreLocked()?.let(errors::add)
                if (removeCookies) {
                    try { clearWebData() }
                    catch (cancelled: CancellationException) { throw cancelled }
                    catch (_: Exception) { errors += "网页登录数据或网页缓存未能完全清理，请再次退出。" }
                }
                boundAccount = accountProvider()
                accountKnown = !resetAccount
                val error = errors.takeIf { it.isNotEmpty() }?.joinToString("\n")
                mutableState.value = LearningUiState(error = error)
                mutableCourses.value = CoursesUiState(error = error)
            }
        }
        caller.ensureActive()
    }

    fun clearError() { mutableState.update { it.copy(error = null) } }
    fun clearCoursesError() { mutableCourses.update { it.copy(error = null) } }
    internal fun attachBrowser(close: () -> Unit): Long { browsers += close; return guard.current() }
    internal fun detachBrowser(close: () -> Unit) { browsers -= close }
    internal fun browserIsCurrent(generation: Long): Boolean = guard.current() == generation

    private fun beginLocked(lane: LearningLane, job: Job?): LearningRequest {
        val token = guard.begin(lane)
        active.remove(lane)?.cancel(CancellationException("学习通请求已被替换"))
        if (job != null) active[lane] = job
        return token
    }
    private fun checkAccount(expected: String?) {
        if (accountProvider() != expected) throw LearningAccountChanged()
    }
    private fun invalidateLocked() {
        guard.invalidate()
        active.values.forEach { it.cancel(CancellationException("学习通会话已变更")) }
        active.clear()
        val oldBrowsers = browsers.toList()
        browsers.clear()
        oldBrowsers.forEach { close ->
            try { close() } catch (cancelled: CancellationException) { throw cancelled } catch (_: Exception) { }
        }
    }
    private suspend fun synchronizeAccountLocked() {
        val current = accountProvider()
        if (accountKnown && boundAccount != current) accountChangedLocked()
        boundAccount = current
        accountKnown = true
    }
    private suspend fun accountChangedLocked() = withContext(NonCancellable) {
        // 当前请求可能也在 active 中；取消它后仍须原子清除两份状态与磁盘缓存。
        invalidateLocked()
        snapshot = null
        cacheLoaded = true
        mutableState.value = LearningUiState()
        mutableCourses.value = CoursesUiState()
        val error = clearStoreLocked()
        boundAccount = accountProvider()
        accountKnown = true
        val message = listOfNotNull("学习通账号已变化，请重新检查登录状态。", error).joinToString("\n")
        mutableState.value = LearningUiState(error = message)
        mutableCourses.value = CoursesUiState(error = message)
    }
    private suspend fun loadCacheLocked() {
        if (cacheLoaded) return
        cacheLoaded = true
        try {
            val loaded = withContext(ioDispatcher) { store.read() }
            if (loaded != null && loaded.account != accountProvider()) {
                snapshot = null
                val error = clearStoreLocked()
                mutableState.value = LearningUiState(error = error)
                mutableCourses.value = CoursesUiState(error = error)
                return
            }
            snapshot = loaded
            loaded?.let {
                if (it.workLoaded) {
                    val merged = HomeworkMerge(it.courses, it.courses.count { row -> row.failure != null })
                    mutableState.value = LearningUiState(works = merged.works, failedCourses = merged.failedCourses,
                        failures = merged.failures, courseCount = it.courses.size, updatedAt = merged.updatedAt,
                        hasSnapshot = true, fromCache = true)
                }
                it.overview?.let { overview -> mutableCourses.value = coursesState(overview, cached = true) }
            }
        } catch (cancelled: CancellationException) { cacheLoaded = false; throw cancelled }
        catch (_: Exception) {
            snapshot = null
            mutableState.value = LearningUiState(error = "加密缓存暂时无法读取，请重新刷新。")
            mutableCourses.value = CoursesUiState(error = "加密缓存暂时无法读取，请重新刷新。")
        }
    }
    private suspend fun saveLocked(): String? {
        val current = snapshot ?: return null
        return try { withContext(ioDispatcher) { store.write(current) }; null }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { "本次结果未能保存到加密缓存。" }
    }
    private suspend fun clearStoreLocked(): String? = try {
        withContext(ioDispatcher) { store.clear() }; null
    } catch (cancelled: CancellationException) { throw cancelled }
    catch (_: Exception) { "旧学习缓存未能完全清理，本次不会显示旧缓存。" }
}
