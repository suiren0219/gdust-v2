package com.menglimi.study.learning

import android.content.Context
import com.menglimi.chaoxing.ChxAuthFailed
import com.menglimi.chaoxing.ChxCourseOverview
import com.menglimi.chaoxing.ChxCourseReadException
import com.menglimi.chaoxing.ChxCourseWorkResult
import com.menglimi.chaoxing.ChxTransportFailure
import com.menglimi.chaoxing.ChxRequestGate
import com.menglimi.chaoxing.ChxSso
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
    /** 仅本次失败的可复制诊断；成功刷新后清空，避免复制到上一次的失败。 */
    private val mutableDiagnostic = MutableStateFlow<String?>(null)
    val diagnostic = mutableDiagnostic.asStateFlow()
    private val guard = LearningSessionGuard(ChxRequestGate(now))
    private var readControl = LearningReadControl()
    private var library = LearningLibrary()
    private var workRows = emptyList<CourseHomework>()
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

    suspend fun verifyFromWeb(courseProgress: Boolean): String? = withContext(mainDispatcher) {
        reload()
        val stopped = readControl.pause != null
        val progress = if (stopped) readControl.progressBlocked else courseProgress
        if (progress) {
            refreshCourses(afterWebVerification = true)
            val result = courses.value
            result.error ?: result.progressError ?: if (result.connected && !result.loading && readControl.pause == null) null
                else "尚未确认课程进度，请在网页完成登录或验证后重试。"
        } else {
            refreshWorks(null, afterWebVerification = true)
            val result = state.value
            if (stopped && readControl.pause == null) result.error
            else result.error ?: if (result.connected && !result.loading && result.failedCourses == 0) null
                else "作业尚未读取完整，请在对应课程网页完成验证后重试。"
        }
    }

    suspend fun refresh(): Unit = refreshWorks(null)
    suspend fun retryCourse(courseKey: String): Unit = refreshWorks(courseKey)
    suspend fun resumeReading() {
        if (readControl.pause != null && readControl.progressBlocked) refreshCourses()
        else refreshWorks(null)
    }

    private suspend fun refreshWorks(retryKey: String?, afterWebVerification: Boolean = false): Unit = coroutineScope {
        withContext(mainDispatcher) {
            val operation = currentCoroutineContext()
            val generation = guard.current()
            var token: LearningRequest? = null
            var account: String? = null
            var verified = false
            var recovering = false
            try {
                guard.mutex.withLock {
                    operation.ensureActive()
                    guard.check(generation)
                    synchronizeAccountLocked()
                    loadCacheLocked()
                    if (mutableState.value.loading) return@withContext
                    if (!allowReadLocked(LearningLane.WORK, afterWebVerification, retryKey)) return@withContext
                    account = accountProvider()
                    token = beginLocked(LearningLane.WORK, operation[Job])
                    recovering = readControl.pause != null
                    if (recovering) guard.requests.beginRecovery(checkNotNull(token), afterWebVerification)
                    mutableDiagnostic.value = null
                    mutableState.update { it.copy(loading = true, retryingCourse = retryKey, error = null) }
                }
                val request = checkNotNull(token)
                val backend = backendFactory(guard, request, account)
                // 恢复检查只重查受阻课程；先重新发现当前课程，绝不重用旧签名或跨账号地址。
                val discovered = backend.workCourses()
                operation.ensureActive()
                guard.check(request)
                checkAccount(account)
                verified = true
                val today = todayKey(now())
                // 页面明确标注“课程已结束”或开课时间已过的课程不算作业任务；手动重查仍允许。
                val skipped = discovered.associate { it.cacheKey() to (it.closedAsOf(today) == true) }
                // 手动文件夹与校历学期只收窄本轮读取范围；受阻恢复、待补读不受范围限制。
                val scoped = discovered.filter { library.includes(it.cacheKey(), it.joinedOn) }
                val selectedKey = if (recovering) readControl.blockedCourseKey else retryKey
                val targets = when {
                    selectedKey != null -> listOf(discovered.firstOrNull { it.cacheKey() == selectedKey }
                        ?: throw ChxCourseReadException("受阻课程已不在当前课程页，请确认账号与课程。"))
                    recovering -> discovered.take(1)
                    readControl.pendingCourseKeys.isNotEmpty() -> scoped.filter { it.cacheKey() in readControl.pendingCourseKeys }
                    else -> scoped.filterNot { skipped[it.cacheKey()] == true }
                }
                val targetKeys = targets.map { it.cacheKey() }.toSet()
                guard.mutex.withLock {
                    operation.ensureActive(); guard.check(request); checkAccount(account)
                    val old = workRows.associateBy { it.courseKey }
                    workRows = discovered.map { course ->
                        val key = course.cacheKey()
                        val isEnded = skipped[key] == true
                        // 范围外课程保留上次结果但不计本轮失败；切回范围时不会被误报为读取失败。
                        val inScope = key in targetKeys || library.includes(key, course.joinedOn)
                        old[key]?.copy(course = course, ended = isEnded,
                            failure = if (isEnded || !inScope) null else old[key]?.failure)
                            ?: CourseHomework(key, emptyList(), 0, course,
                                if (isEnded || !inScope) null else "本轮尚未读取，是否有作业仍未知。", ended = isEnded)
                    }
                    val pending = if (recovering && selectedKey == null) discovered.map { it.cacheKey() }
                        else (readControl.pendingCourseKeys + targets.map { it.cacheKey() }).distinct()
                    readControl = readControl.copy(pendingCourseKeys = pending.filter { key ->
                        discovered.any { it.cacheKey() == key } &&
                            (recovering || scoped.any { it.cacheKey() == key })
                    })
                    publishReadControlLocked()
                }
                val results = mutableListOf<ChxCourseWorkResult>()
                for (course in targets) {
                    operation.ensureActive(); guard.check(request)
                    val result = try {
                        guard.requests.check(request)
                        backend.works(listOf(course)).single().also { require(it.course.cacheKey() == course.cacheKey()) }
                    } catch (cancelled: CancellationException) { throw cancelled }
                    catch (failure: Exception) { ChxCourseWorkResult.Failure(course, failure) }
                    results += result
                    val failure = (result as? ChxCourseWorkResult.Failure)?.error as? ChxCourseReadException
                    guard.mutex.withLock {
                        operation.ensureActive(); guard.check(request); checkAccount(account)
                        if (failure?.pausesRequests == true) {
                            recordPauseLocked(failure, request, course)
                        } else {
                            readControl = readControl.copy(pendingCourseKeys = readControl.pendingCourseKeys - course.cacheKey())
                        }
                        workRows = mergeHomework(workRows, listOf(result), now(), partial = true).courses
                        snapshot = account?.let { (snapshot ?: LearningSnapshot(it)).copy(courses = workRows, workLoaded = true) }
                        saveLocked()
                        operation.ensureActive(); guard.check(request); checkAccount(account)
                    }
                    if (failure?.pausesRequests == true) break
                }
                guard.mutex.withLock {
                    operation.ensureActive(); guard.check(request); checkAccount(account)
                    val merged = HomeworkMerge(workRows, workRows.count { it.failure != null })
                    val recovered = recovering && (targets.isEmpty() || results.all { it is ChxCourseWorkResult.Success })
                    guard.requests.finishRecovery(request, recovered)
                    if (recovered && guard.requests.pause.value == null) {
                        readControl = readControl.copy(pause = null, blockedCourseKey = null, progressBlocked = false, verificationUrl = null)
                    }
                    val authFailed = merged.courses.any { it.authRequired }
                    snapshot = account?.let { (snapshot ?: LearningSnapshot(it)).copy(courses = workRows, workLoaded = true) }
                    val cacheError = saveLocked()
                    operation.ensureActive(); guard.check(request); checkAccount(account)
                    mutableState.value = LearningUiState(
                        connected = !authFailed, works = merged.works, failedCourses = merged.failedCourses,
                        failures = merged.failures, courseCount = merged.courses.size,
                        endedCourses = workRows.count { it.ended },
                        error = if (recovered) cacheError else listOfNotNull(readControl.message() ?: when {
                            authFailed -> "学习通登录已失效，请重新登录。未更新的课程保留上次记录。"
                            merged.failedCourses > 0 -> "${merged.failedCourses} 门课程读取失败，未读课程不能确认是否有作业。"
                            else -> null
                        }, cacheError).takeIf { it.isNotEmpty() }?.joinToString("\n"),
                        updatedAt = merged.updatedAt, hasSnapshot = true,
                        fromCache = merged.courses.any { (it.failure != null || it.courseKey in readControl.pendingCourseKeys) && it.updatedAt > 0 },
                        readControl = readControl,
                    )
                    if (authFailed) mutableCourses.update { it.copy(connected = false) }
                    publishReadControlLocked()
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) {
                token?.let { request -> guard.mutex.withLock {
                    operation.ensureActive(); guard.check(request)
                    if (failure is LearningAccountChanged || accountProvider() != account) accountChangedLocked()
                    else {
                        if (failure is ChxCourseReadException && failure.pausesRequests) recordPauseLocked(failure, request)
                        recordDiagnosticLocked(failure)
                        saveLocked()
                        mutableState.update { it.copy(connected = failure !is ChxAuthFailed && (verified || it.connected),
                            error = learningFailureMessage(failure), fromCache = it.hasSnapshot, readControl = readControl) }
                        if (failure is ChxAuthFailed) mutableCourses.update { it.copy(connected = false) }
                    }
                } }
            } finally {
                withContext(NonCancellable + mainDispatcher) { guard.mutex.withLock {
                    token?.let { guard.requests.finishRecovery(it, false) }
                    token?.takeIf(guard::isCurrent)?.let {
                        active.remove(LearningLane.WORK)
                        mutableState.update { value -> value.copy(loading = false, retryingCourse = null) }
                    }
                } }
            }
        }
    }

    suspend fun refreshCourses(): Unit = refreshCourses(afterWebVerification = false)

    private suspend fun refreshCourses(afterWebVerification: Boolean): Unit = coroutineScope {
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
                    if (!allowReadLocked(LearningLane.COURSES, afterWebVerification)) return@withContext
                    account = accountProvider()
                    token = beginLocked(LearningLane.COURSES, operation[Job])
                    if (readControl.pause != null) guard.requests.beginRecovery(checkNotNull(token), afterWebVerification)
                    mutableDiagnostic.value = null
                    mutableCourses.update { it.copy(loading = true, error = null) }
                }
                val request = checkNotNull(token)
                val overview = backendFactory(guard, request, account).courses()
                guard.mutex.withLock {
                    operation.ensureActive()
                    guard.check(request)
                    checkAccount(account)
                    overview.requestPause?.let { recordPauseLocked(it.exception(), request) }
                    if (overview.progressError == null && !overview.progressAuthRequired) {
                        guard.requests.finishRecovery(request, true)
                        if (guard.requests.pause.value == null && readControl.progressBlocked) {
                            readControl = readControl.copy(pause = null, progressBlocked = false, verificationUrl = null)
                        }
                    }
                    snapshot = account?.let { (snapshot ?: LearningSnapshot(it)).copy(overview = overview) }
                    val cacheError = saveLocked()
                    operation.ensureActive()
                    guard.check(request)
                    checkAccount(account)
                    mutableCourses.value = coursesState(overview, cached = false, library).copy(error = cacheError, readControl = readControl)
                    publishReadControlLocked()
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
                        if (failure is ChxCourseReadException && failure.pausesRequests) recordPauseLocked(failure, request)
                        recordDiagnosticLocked(failure)
                        saveLocked()
                        mutableCourses.update { it.copy(connected = false, fromCache = it.hasSnapshot,
                            error = learningFailureMessage(failure), readControl = readControl) }
                        if (failure is ChxAuthFailed) mutableState.update { it.copy(connected = false) }
                    }
                } }
            } finally {
                withContext(NonCancellable + mainDispatcher) { guard.mutex.withLock {
                    token?.let { guard.requests.finishRecovery(it, false) }
                    token?.takeIf(guard::isCurrent)?.let {
                        active.remove(LearningLane.COURSES)
                        mutableCourses.update { value -> value.copy(loading = false) }
                    }
                } }
            }
        }
    }

    private fun allowReadLocked(lane: LearningLane, afterWebVerification: Boolean, courseKey: String? = null): Boolean {
        val stopped = guard.requests.pause.value ?: readControl.pause ?: return true
        val message = when {
            stopped.retryAt > now() -> "尚在服务端要求的冷却期，当前不会发送请求。"
            readControl.progressBlocked != (lane == LearningLane.COURSES) -> "请先重查受阻项，再继续其他读取。"
            courseKey != null && courseKey != readControl.blockedCourseKey -> "请先重查受阻课程，再继续其他课程。"
            stopped.verificationRequired && !afterWebVerification -> "请先在网页完成验证，再点“完成检查”。当前不会发送请求。"
            active.isNotEmpty() -> "仍有读取正在收尾，请稍后再检查。"
            else -> null
        }
        if (message == null) return true
        if (lane == LearningLane.WORK) mutableState.update { it.copy(error = message, readControl = readControl) }
        else mutableCourses.update { it.copy(error = message, readControl = readControl) }
        return false
    }

    private fun recordPauseLocked(failure: ChxCourseReadException, request: LearningRequest, course: ChxWorkCourse? = null) {
        val caused = guard.requests.causedPause(request)
        guard.requests.record(failure, request)
        readControl = if (caused) readControl.copy(pause = guard.requests.pause.value,
            blockedCourseKey = course?.cacheKey() ?: readControl.blockedCourseKey,
            progressBlocked = request.lane == LearningLane.COURSES,
            verificationUrl = course?.url ?: readControl.verificationUrl ?: ChxSso.HOME)
        else readControl.copy(pause = guard.requests.pause.value)
        publishReadControlLocked()
    }

    /** 只保留固定阶段/类型/路由，不含异常原文、Cookie 或带签名的 query。 */
    private fun recordDiagnosticLocked(failure: Exception) {
        val detail = when (failure) {
            is ChxTransportFailure -> "${learningFailureMessage(failure)}\n${failure.diagnostic}"
            else -> learningFailureMessage(failure)
        }
        mutableDiagnostic.value = "学习助手 ${com.menglimi.study.BuildConfig.VERSION_NAME}\n$detail"
    }

    private fun publishReadControlLocked() {
        mutableState.update { it.copy(readControl = readControl) }
        mutableCourses.update { it.copy(readControl = readControl) }
    }

    private fun publishLibraryLocked() {
        mutableCourses.update { it.copy(library = library) }
    }

    suspend fun prepareLogin() {
        reload()
        if (!readControl.active) clearData(removeCookies = false, resetAccount = true)
    }
    suspend fun clearCache() = clearData(removeCookies = false)
    suspend fun disconnect() = clearData(removeCookies = true)

    /**
     * 课程分类只是本地整理，不发起任何学习通请求；返回非空文本表示这次没有保存成功。
     * 手动归属优先于自动学期，刷新课程不会覆盖用户选择。
     */
    suspend fun createFolder(name: String, termId: String?): String? =
        updateLibrary { it.createFolder(name, termId) }

    suspend fun removeFolder(folderId: String): String? = updateLibrary { it.removeFolder(folderId) }

    suspend fun moveCourse(courseKey: String, folderId: String?): String? =
        updateLibrary { it.move(courseKey, folderId) }

    suspend fun selectCourseScope(scope: LearningScope, id: String?): String? =
        updateLibrary { it.select(scope, id) }

    /** 只接受主应用备份 JSON 里的校历字段；其余内容（含绑定信息）不读取、不转存。 */
    suspend fun importCalendar(text: String): String? =
        updateLibrary { it.withTerm(LearningCalendarImport.parse(text)) }

    private suspend fun updateLibrary(transform: (LearningLibrary) -> LearningLibrary): String? =
        withContext(mainDispatcher) {
            val generation = guard.current()
            guard.mutex.withLock {
                guard.check(generation)
                synchronizeAccountLocked()
                loadCacheLocked()
                val account = accountProvider() ?: return@withLock "请先登录学习通再管理课程分类。"
                val updated = try { transform(library) }
                catch (failure: IllegalArgumentException) { return@withLock failure.message ?: "课程分类未能更新。" }
                library = updated
                snapshot = (snapshot ?: LearningSnapshot(account)).copy(library = updated)
                val error = saveLocked()
                publishLibraryLocked()
                error
            }
        }

    /** 清除一旦开始完整执行；退出后 Cookie callback、flush、WebStorage 和快照都已处理。 */
    private suspend fun clearData(removeCookies: Boolean, resetAccount: Boolean = false) {
        val caller = currentCoroutineContext()
        caller.ensureActive()
        withContext(NonCancellable + mainDispatcher) {
            guard.mutex.withLock {
                invalidateLocked()
                snapshot = null
                workRows = emptyList()
                library = LearningLibrary()
                cacheLoaded = true
                if (removeCookies) { readControl = LearningReadControl(); guard.requests.restore(null) }
                mutableDiagnostic.value = null
                mutableState.value = LearningUiState(readControl = readControl)
                mutableCourses.value = CoursesUiState(readControl = readControl)
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
                mutableState.value = LearningUiState(error = error, readControl = readControl)
                mutableCourses.value = CoursesUiState(error = error, readControl = readControl)
                if (!removeCookies && readControl.active) saveLocked()
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
        workRows = emptyList()
        library = LearningLibrary()
        readControl = LearningReadControl()
        guard.requests.restore(null)
        cacheLoaded = true
        mutableDiagnostic.value = null
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
                workRows = it.courses
                readControl = it.readControl
                library = it.library
                guard.requests.restore(readControl.pause)
                if (it.workLoaded) {
                    val merged = HomeworkMerge(it.courses, it.courses.count { row -> row.failure != null })
                    mutableState.value = LearningUiState(works = merged.works, failedCourses = merged.failedCourses,
                        failures = merged.failures, courseCount = it.courses.size, updatedAt = merged.updatedAt,
                        hasSnapshot = true, fromCache = true)
                }
                it.overview?.let { overview -> mutableCourses.value = coursesState(overview, cached = true, library) }
                publishReadControlLocked()
            }
        } catch (cancelled: CancellationException) { cacheLoaded = false; throw cancelled }
        catch (_: Exception) {
            snapshot = null
            mutableState.value = LearningUiState(error = "加密缓存暂时无法读取，请重新刷新。")
            mutableCourses.value = CoursesUiState(error = "加密缓存暂时无法读取，请重新刷新。")
        }
    }
    private suspend fun saveLocked(): String? {
        val current = (snapshot ?: accountProvider()?.let { LearningSnapshot(it) } ?: return null)
            .copy(readControl = readControl, library = library)
        snapshot = current
        return try { withContext(ioDispatcher) { store.write(current) }; null }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { "本次结果未能保存到加密缓存。" }
    }
    private suspend fun clearStoreLocked(): String? = try {
        withContext(ioDispatcher) { store.clear() }; null
    } catch (cancelled: CancellationException) { throw cancelled }
    catch (_: Exception) { "旧学习缓存未能完全清理，本次不会显示旧缓存。" }
}
