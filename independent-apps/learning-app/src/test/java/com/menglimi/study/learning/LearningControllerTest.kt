package com.menglimi.study.learning

import com.menglimi.chaoxing.ChxCourse
import com.menglimi.chaoxing.ChxCourseApi
import com.menglimi.chaoxing.ChxHttpResult
import com.menglimi.chaoxing.ChxResponse
import com.menglimi.chaoxing.ChxCourseOverview
import com.menglimi.chaoxing.ChxCourseProgress
import com.menglimi.chaoxing.ChxCourseWorkResult
import com.menglimi.chaoxing.ChxWorkCourse
import com.menglimi.chaoxing.ChxWorkItem
import com.menglimi.chaoxing.ChxWorkWithDeadline
import com.menglimi.chaoxing.requestPause
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicLong

@OptIn(ExperimentalCoroutinesApi::class)
class LearningControllerTest {
    private val accountA = "a".repeat(64)
    private val accountB = "b".repeat(64)
    private val workCourse = ChxWorkCourse(1, 2, 3, "合成课程", "https://mooc1.chaoxing.com/visit/stucoursemiddle")
    private val course = ChxCourse("2", "1", "3", "合成课程", "合成说明", workCourse.url)
    private val work = Homework("1:2:3:5", "合成课程", "缓存作业", "未交", 0,
        "https://mooc1.chaoxing.com/mooc-ans/mooc2/work/task?workId=5")
    private fun cached() = LearningSnapshot(accountA, listOf(CourseHomework("1:2:3", listOf(work), 100, workCourse)),
        workLoaded = true, overview = ChxCourseOverview(listOf(course.copy(progress = ChxCourseProgress(10, 5, 50.0))), loadedAt = 100))
    private fun success(title: String) = listOf(ChxCourseWorkResult.Success(workCourse, listOf(ChxWorkWithDeadline(
        ChxWorkItem(title, "未交", work.detailUrl, 5, 6, "synthetic", ""), 0))))

    @Test fun courseRefreshLeavesWorkLoadingIndependentAndPreservesWorkSnapshot() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val store = MemoryStore(cached())
        val workResult = CompletableDeferred<List<ChxCourseWorkResult>>()
        val controller = LearningController(store, { accountA }, {}, { _, _, _ -> backend(
            works = { workResult.await() }, courses = { ChxCourseOverview(listOf(course), "任务点未知", 200) }) }, dispatcher, dispatcher)
        controller.reload()
        val loading = launch { controller.refresh() }
        runCurrent()
        assertTrue(controller.state.value.loading)
        assertFalse(controller.courses.value.loading)
        controller.refreshCourses()
        assertTrue(controller.state.value.loading)
        assertFalse(controller.courses.value.loading)
        assertNull(controller.courses.value.courses.single().progress)
        assertEquals("缓存作业", store.value!!.courses.single().works.single().title)
        workResult.complete(success("刷新作业")); loading.join()
        assertEquals(200L, store.value!!.overview!!.loadedAt)
        assertEquals("刷新作业", controller.state.value.works.single().title)
    }
    @Test fun failedCourseRefreshPreservesCacheAndNeverBecomesEmptySuccess() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val controller = LearningController(MemoryStore(cached()), { accountA }, {}, { _, _, _ ->
            backend(courses = { throw java.io.IOException("synthetic network failure") }) }, dispatcher, dispatcher)
        controller.reload(); controller.refreshCourses()
        val state = controller.courses.value
        assertEquals(1, state.courses.size)
        assertEquals(100L, state.updatedAt)
        assertTrue(state.fromCache)
        assertFalse(state.connected)
        assertTrue(state.error != null)
        assertFalse(state.loading)
    }
    @Test fun failedProgressCookieWriteThroughCourseApiPreservesOverviewAndTimestamp() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val initial = cached()
        val store = MemoryStore(initial)
        val clock = AtomicLong(1_000L)
        val paths = mutableListOf<String>()
        var writes = 0
        var flushes = 0
        val controller = LearningController(store, { accountA }, {}, { guard, token, _ -> backend(courses = {
            ChxCourseApi(execute = { request ->
                guard.requests.execute(token, { guard.check(token) }) {
                    val path = request.url.encodedPath
                    paths += path
                    val response = when (path) {
                        "/mooc2-ans/visit/interaction" -> ChxResponse(200, """
                            <input type="hidden" id="stuPageHeader" value="1">
                            <input type="hidden" id="loadStuJobInfo" value="1">
                            <input type="hidden" id="checkUserToken" value="0123456789abcdef0123456789abcdef">
                        """.trimIndent())
                        "/mooc2-ans/visit/courselistdata" -> ChxResponse(200, """
                            <div id="courseList"><li class="course learnCourse" info="2_3">
                                <input type="hidden" class="clazzId" value="2">
                                <input type="hidden" class="courseId" value="1">
                                <input type="hidden" class="role" value="0">
                                <input type="hidden" class="curPersonId" value="3">
                                <a href="https://mooc1.chaoxing.com/visit/stucoursemiddle?courseid=1&amp;clazzid=2&amp;cpi=3&amp;ismooc2=1&amp;v=2">
                                    <div class="course-info"><span class="course-name" title="不应覆盖缓存的新课程">新课程</span></div>
                                </a>
                            </li></div>
                        """.trimIndent())
                        "/mooc2-ans/mycourse/stu-job-info" -> ChxResponse(200,
                            """{"status":true,"jobArray":[{"clazzId":2,"jobCount":10,"jobFinishCount":10,"jobRate":100}]}""")
                        else -> error("意外的合成请求路径")
                    }
                    val received = ChxHttpResult(response,
                        if (path == "/mooc2-ans/mycourse/stu-job-info") listOf("synthetic=value; Path=/; Secure") else emptyList())
                    guard.mutex.withLock {
                        guard.check(token)
                        commitLearningResponseCookies(received, { guard.check(token) },
                            { _, callback -> writes++; callback(false) }, { flushes++ })
                        guard.check(token)
                    }
                    received.response
                }
            }).load()
        }) }, dispatcher, dispatcher, now = { clock.getAndAdd(1_000L) })
        controller.reload(); controller.refreshCourses()
        val state = controller.courses.value
        assertEquals(listOf("/mooc2-ans/visit/interaction", "/mooc2-ans/visit/courselistdata",
            "/mooc2-ans/mycourse/stu-job-info"), paths)
        assertEquals(1, writes)
        assertEquals(1, flushes)
        assertEquals(initial.overview!!.courses, state.courses)
        assertEquals(100L, state.updatedAt)
        assertTrue(state.hasSnapshot)
        assertTrue(state.fromCache)
        assertFalse(state.connected)
        assertEquals("会话更新失败，请重试", state.error)
        assertNull(state.progressError)
        assertFalse(state.loading)
        assertEquals(initial, store.value)
        assertEquals(listOf(work), controller.state.value.works)
    }
    @Test fun failedCookieWriteDuringWorkRefreshPreservesCacheAndTimestamp() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val initial = cached()
        val store = MemoryStore(initial)
        val controller = LearningController(store, { accountA }, {}, { _, _, _ -> backend(works = {
            awaitLearningCookieWrite { callback -> callback(false) }
            success("不应写入的新作业")
        }) }, dispatcher, dispatcher, now = { 200L })
        controller.reload(); controller.refresh()
        val state = controller.state.value
        assertEquals(listOf(work), state.works)
        assertEquals(100L, state.updatedAt)
        assertEquals(1, state.failedCourses)
        assertTrue(state.hasSnapshot)
        assertTrue(state.fromCache)
        assertTrue(state.error != null)
        assertFalse(state.loading)
        assertEquals(initial.courses.single().works, store.value!!.courses.single().works)
        assertEquals(100L, store.value!!.courses.single().updatedAt)
        assertEquals(initial.overview, store.value!!.overview)
    }
    @Test fun logoutDropsLateResponseAndCannotResurrectClearedCache() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val store = MemoryStore(cached())
        var account: String? = accountA
        var webCleared = false
        val response = CompletableDeferred<ChxCourseOverview>()
        val controller = LearningController(store, { account }, { webCleared = true; account = null }, { _, _, _ ->
            backend(courses = { withContext(NonCancellable) { response.await() } }) }, dispatcher, dispatcher)
        controller.reload()
        val refresh = launch { controller.refreshCourses() }; runCurrent()
        controller.disconnect()
        response.complete(ChxCourseOverview(listOf(course), loadedAt = 300)); refresh.join()
        assertTrue(webCleared)
        assertTrue(refresh.isCancelled)
        assertNull(store.value)
        assertTrue(controller.courses.value.courses.isEmpty())
        assertTrue(controller.state.value.works.isEmpty())
    }
    @Test fun accountSwitchDuringRequestClearsBothSnapshotsBeforeCancelledRequestExits() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val store = MemoryStore(cached())
        var account = accountA
        val response = CompletableDeferred<ChxCourseOverview>()
        val controller = LearningController(store, { account }, {}, { _, _, _ -> backend(courses = { response.await() }) }, dispatcher, dispatcher)
        controller.reload()
        val refresh = launch { controller.refreshCourses() }; runCurrent()
        account = accountB
        response.complete(ChxCourseOverview(listOf(course), loadedAt = 400)); refresh.join()
        assertNull(store.value)
        assertTrue(controller.state.value.works.isEmpty())
        assertTrue(controller.courses.value.courses.isEmpty())
        assertTrue(controller.courses.value.error!!.contains("账号已变化"))
    }
    @Test fun olderCourseRequestCannotOverwriteNewerResultEvenIfBackendIgnoresCancellation() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val old = CompletableDeferred<ChxCourseOverview>()
        var call = 0
        val controller = LearningController(MemoryStore(null), { accountA }, {}, { _, _, _ ->
            val index = call++
            backend(courses = { if (index == 0) withContext(NonCancellable) { old.await() }
                else ChxCourseOverview(listOf(course.copy(name = "较新课程")), loadedAt = 500) }) }, dispatcher, dispatcher)
        val first = launch { controller.refreshCourses() }; runCurrent()
        controller.refreshCourses()
        old.complete(ChxCourseOverview(listOf(course.copy(name = "旧课程")), loadedAt = 100)); first.join()
        assertEquals("较新课程", controller.courses.value.courses.single().name)
        assertEquals(500L, controller.courses.value.updatedAt)
        assertFalse(controller.courses.value.loading)
    }
    @Test fun cancellationPropagatesAndDoesNotBecomeNetworkFailure() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val controller = LearningController(MemoryStore(null), { accountA }, {}, { _, _, _ ->
            backend(courses = { throw CancellationException("synthetic cancel") }) }, dispatcher, dispatcher)
        var propagated = false
        try { controller.refreshCourses() } catch (_: CancellationException) { propagated = true }
        assertTrue(propagated)
        assertNull(controller.courses.value.error)
        assertFalse(controller.courses.value.loading)
    }
    @Test fun cancelledWorkReadPreservesPauseTargetAndOnlyRechecksBlockedCourse() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val blockedCourse = workCourse.copy(courseId = 4, name = "受阻课程")
        val remainingCourse = workCourse.copy(courseId = 7, name = "剩余课程")
        val started = CompletableDeferred<Unit>()
        val store = MemoryStore(cached())
        val calls = mutableListOf<Long>()
        var discoveries = 0
        var blocked = true
        val controller = LearningController(store, { accountA }, {}, { guard, token, _ -> object : LearningBackend {
            override suspend fun workCourses(): List<ChxWorkCourse> {
                discoveries++
                return listOf(workCourse, blockedCourse, remainingCourse)
            }
            override suspend fun works(courses: List<ChxWorkCourse>): List<ChxCourseWorkResult> {
                val selected = courses.single()
                guard.requests.execute(token, { guard.check(token) }) {
                    calls += selected.courseId
                    if (selected == blockedCourse && blocked) {
                        guard.requests.record(ChxResponse(200, """{"code":9010}""").requestPause()!!.exception(), token)
                        started.complete(Unit)
                        awaitCancellation()
                    }
                    ChxResponse(200, "合成成功")
                }
                return if (selected == workCourse) success("取消前读取的作业")
                    else listOf(ChxCourseWorkResult.Success(selected, emptyList()))
            }
            override suspend fun courses() = error("不应读取进度")
        } }, dispatcher, dispatcher)
        controller.reload()
        val reading = launch { controller.refresh() }
        runCurrent(); started.await()
        reading.cancelAndJoin()
        assertTrue(reading.isCancelled)
        assertFalse(controller.state.value.loading)
        assertTrue(controller.state.value.readControl.pause!!.verificationRequired)
        assertEquals(blockedCourse.cacheKey(), controller.state.value.readControl.blockedCourseKey)
        assertEquals(blockedCourse.url, controller.state.value.readControl.verificationUrl)
        assertEquals(listOf(blockedCourse.cacheKey(), remainingCourse.cacheKey()),
            store.value!!.readControl.pendingCourseKeys)
        assertEquals("取消前读取的作业", store.value!!.courses.first().works.single().title)
        controller.refresh(); controller.refreshCourses()
        assertEquals(1, discoveries)
        assertEquals(listOf(1L, 4L), calls)
        blocked = false
        assertNull(controller.verifyFromWeb(false))
        assertEquals(listOf(1L, 4L, 4L), calls)
        assertNull(controller.state.value.readControl.pause)
        assertEquals(listOf(remainingCourse.cacheKey()), controller.state.value.readControl.pendingCourseKeys)
    }

    @Test fun cancelledProgressReadPreservesPauseAndChangedAccountCannotReceiveIt() = runTest {
        for (changeAccount in listOf(false, true)) {
            val dispatcher = StandardTestDispatcher(testScheduler)
            val initial = cached()
            val store = MemoryStore(initial)
            val started = CompletableDeferred<Unit>()
            var account = accountA
            var blocked = true
            var calls = 0
            val controller = LearningController(store, { account }, {}, { guard, token, _ -> backend(courses = {
                guard.requests.execute(token, { guard.check(token) }) {
                    calls++
                    if (blocked) {
                        guard.requests.record(ChxResponse(200, """{"code":9010}""").requestPause()!!.exception(), token)
                        started.complete(Unit)
                        awaitCancellation()
                    }
                    ChxResponse(200, "合成成功")
                }
                ChxCourseOverview(listOf(course), loadedAt = 200)
            }) }, dispatcher, dispatcher)
            controller.reload()
            val reading = launch { controller.refreshCourses() }
            runCurrent(); started.await()
            if (changeAccount) account = accountB
            reading.cancelAndJoin()
            assertTrue(reading.isCancelled)
            assertFalse(controller.courses.value.loading)
            if (changeAccount) {
                assertNull(store.value)
                assertNull(controller.courses.value.readControl.pause)
                assertTrue(controller.courses.value.courses.isEmpty())
                assertTrue(controller.courses.value.error!!.contains("账号已变化"))
            } else {
                assertTrue(controller.courses.value.readControl.pause!!.verificationRequired)
                assertTrue(controller.courses.value.readControl.progressBlocked)
                assertEquals(initial.overview, store.value!!.overview)
                assertEquals(100L, controller.courses.value.updatedAt)
                controller.refreshCourses(); controller.refresh()
                assertEquals(1, calls)
                blocked = false
                assertNull(controller.verifyFromWeb(false))
                assertEquals(2, calls)
                assertNull(controller.courses.value.readControl.pause)
            }
        }
    }

    @Test fun replacedTokenPreservesLatchedPauseWithoutClearingLoading() = runTest {
        for (lane in LearningLane.entries) {
            val dispatcher = StandardTestDispatcher(testScheduler)
            val initial = cached()
            val store = MemoryStore(initial)
            val stopped = com.menglimi.chaoxing.ChxRequestPause(true, 31_000L)
            val controller = LearningController(store, { accountA }, {}, { guard, token, _ ->
                suspend fun pauseAndReplace(): Nothing {
                    guard.mutex.withLock {
                        guard.requests.record(stopped.exception(), token)
                        guard.begin(lane)
                    }
                    throw CancellationException("synthetic replacement after response")
                }
                backend(works = { pauseAndReplace() }, courses = { pauseAndReplace() })
            }, dispatcher, dispatcher)
            controller.reload()
            val reading = launch {
                if (lane == LearningLane.WORK) controller.refresh() else controller.refreshCourses()
            }
            reading.join()
            assertTrue(reading.isCancelled)
            val saved = checkNotNull(store.value)
            assertEquals(accountA, saved.account)
            assertEquals(stopped, saved.readControl.pause)
            assertEquals(lane == LearningLane.COURSES, saved.readControl.progressBlocked)
            assertEquals(if (lane == LearningLane.WORK) workCourse.cacheKey() else null, saved.readControl.blockedCourseKey)
            assertEquals(initial.courses, saved.courses)
            assertEquals(initial.overview, saved.overview)
            assertEquals(saved.readControl, controller.state.value.readControl)
            assertEquals(saved.readControl, controller.courses.value.readControl)
            assertTrue(if (lane == LearningLane.WORK) controller.state.value.loading else controller.courses.value.loading)
        }
    }

    @Test fun oldGenerationFinalizerCannotTouchNewSessionPauseOrLoading() = runTest {
        for (lane in LearningLane.entries) for (changeAccount in listOf(false, true)) {
            val dispatcher = StandardTestDispatcher(testScheduler)
            val store = MemoryStore(cached())
            val oldStarted = CompletableDeferred<Unit>()
            val releaseOld = CompletableDeferred<Unit>()
            val newStarted = CompletableDeferred<Unit>()
            val stopped = com.menglimi.chaoxing.ChxRequestPause(true, 61_000L)
            var account = accountA
            var calls = 0
            val controller = LearningController(store, { account }, {}, { guard, token, _ ->
                val old = calls++ == 0
                suspend fun waitForResponse(): Nothing {
                    if (old) {
                        withContext(NonCancellable) { oldStarted.complete(Unit); releaseOld.await() }
                        throw CancellationException("synthetic late response")
                    }
                    guard.requests.record(stopped.exception(), token)
                    newStarted.complete(Unit)
                    awaitCancellation()
                }
                backend(works = { waitForResponse() }, courses = { waitForResponse() })
            }, dispatcher, dispatcher)
            val old = launch {
                if (lane == LearningLane.WORK) controller.refresh() else controller.refreshCourses()
            }
            oldStarted.await()
            controller.disconnect()
            if (changeAccount) account = accountB
            val newer = launch {
                if (lane == LearningLane.WORK) controller.refresh() else controller.refreshCourses()
            }
            newStarted.await()
            val workState = controller.state.value
            val courseState = controller.courses.value
            val snapshot = store.value
            val writes = store.writes
            releaseOld.complete(Unit); old.join()
            assertTrue(old.isCancelled)
            assertEquals(workState, controller.state.value)
            assertEquals(courseState, controller.courses.value)
            assertEquals(snapshot, store.value)
            assertEquals(writes, store.writes)
            assertTrue(newer.isActive)
            newer.cancelAndJoin()
            assertEquals(account, store.value!!.account)
            assertEquals(stopped, store.value!!.readControl.pause)
            assertEquals(lane == LearningLane.COURSES, store.value!!.readControl.progressBlocked)
            assertFalse(controller.state.value.loading)
            assertFalse(controller.courses.value.loading)
        }
    }

    @Test fun noUniqueAccountKeepsOnlyMemoryAndNeverWritesSnapshot() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val store = MemoryStore(null)
        val controller = LearningController(store, { null }, {}, { _, _, _ -> backend() }, dispatcher, dispatcher)
        controller.refreshCourses(); controller.refresh()
        assertTrue(controller.courses.value.hasSnapshot)
        assertTrue(controller.state.value.hasSnapshot)
        assertNull(store.value)
        assertEquals(0, store.writes)
    }

    @Test fun webCheckKeepsPartialHomeworkOpenUntilTheCourseCanBeReadAgain() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        var blocked = true
        val controller = LearningController(MemoryStore(cached()), { accountA }, {}, { _, _, _ -> backend(
            works = { if (blocked) listOf(ChxCourseWorkResult.Failure(workCourse,
                com.menglimi.chaoxing.ChxCourseReadException("请完成验证码", verificationRequired = true)))
                else success("验证后的作业") }) }, dispatcher, dispatcher)
        assertTrue(controller.verifyFromWeb(false) != null)
        assertEquals(1, controller.state.value.failedCourses)
        blocked = false
        assertNull(controller.verifyFromWeb(false))
        assertEquals("验证后的作业", controller.state.value.works.single().title)
    }

    @Test fun courseWebCheckUsesProgressAndNeverReadsHomework() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        var blocked = true
        var workCalls = 0
        val controller = LearningController(MemoryStore(null), { accountA }, {}, { _, _, _ -> backend(
            works = { workCalls++; success("不应读取") },
            courses = { ChxCourseOverview(listOf(course), if (blocked) "请完成验证码" else null) }) }, dispatcher, dispatcher)
        assertEquals("请完成验证码", controller.verifyFromWeb(true))
        blocked = false
        assertNull(controller.verifyFromWeb(true))
        assertEquals(0, workCalls)
    }

    @Test fun verificationStopsTheBatchThenRechecksOnlyTheBlockedCourseBeforeManualContinue() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val second = workCourse.copy(courseId = 4, name = "受阻课程")
        val third = workCourse.copy(courseId = 7, name = "剩余课程")
        val all = listOf(workCourse, second, third)
        var blocked = true
        var discovered = 0
        var progressCalls = 0
        val calls = mutableListOf<Long>()
        val store = MemoryStore(cached())
        val controller = LearningController(store, { accountA }, {}, { _, _, _ -> object : LearningBackend {
            override suspend fun workCourses(): List<ChxWorkCourse> { discovered++; return all }
            override suspend fun works(courses: List<ChxWorkCourse>): List<ChxCourseWorkResult> {
                val selected = courses.single(); calls += selected.courseId
                return listOf(if (selected == second && blocked) ChxCourseWorkResult.Failure(selected,
                    com.menglimi.chaoxing.ChxCourseReadException("需要验证", verificationRequired = true))
                    else ChxCourseWorkResult.Success(selected, emptyList()))
            }
            override suspend fun courses(): ChxCourseOverview { progressCalls++; return ChxCourseOverview(listOf(course)) }
        } }, dispatcher, dispatcher)
        controller.refresh()
        assertEquals(listOf(1L, 4L), calls)
        assertEquals(listOf(second.cacheKey(), third.cacheKey()), controller.state.value.readControl.pendingCourseKeys)
        controller.refresh(); controller.retryCourse(third.cacheKey()); controller.refreshCourses()
        assertEquals(1, discovered)
        assertEquals(0, progressCalls)
        assertTrue(store.value!!.readControl.pause!!.verificationRequired)
        blocked = false
        assertNull(controller.verifyFromWeb(false))
        assertEquals(listOf(1L, 4L, 4L), calls)
        assertNull(controller.state.value.readControl.pause)
        assertEquals(listOf(third.cacheKey()), controller.state.value.readControl.pendingCourseKeys)
        controller.resumeReading()
        assertEquals(listOf(1L, 4L, 4L, 7L), calls)
        assertTrue(controller.state.value.readControl.pendingCourseKeys.isEmpty())
    }

    @Test fun cooldownSurvivesCacheReloadAndCannotBeBypassedByWebCheckOrCacheClear() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        var time = 1_000L
        var calls = 0
        var blocked = true
        val store = MemoryStore(cached())
        fun create() = LearningController(store, { accountA }, {}, { _, _, _ -> backend(works = {
            calls++
            if (blocked) listOf(ChxCourseWorkResult.Failure(workCourse,
                com.menglimi.chaoxing.ChxCourseReadException("冷却", retryAt = 61_000))) else success("恢复后作业")
        }) }, dispatcher, dispatcher, now = { time })
        var controller = create()
        controller.refresh()
        assertEquals(1, calls)
        controller = create()
        controller.reload()
        controller.refresh(); controller.verifyFromWeb(false)
        assertEquals(1, calls)
        assertEquals(61_000L, controller.state.value.readControl.pause!!.retryAt)
        controller.clearCache()
        controller = create(); controller.reload(); controller.refresh()
        assertEquals(1, calls)
        time = 61_000; blocked = false
        controller.resumeReading()
        assertEquals(2, calls)
        assertNull(controller.state.value.readControl.pause)
    }

    @Test fun progressPauseBlocksWorkAndWebCheckRecoversTheOriginalLane() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        var blocked = true
        var works = 0
        var progressCalls = 0
        val store = MemoryStore(cached())
        val controller = LearningController(store, { accountA }, {}, { guard, _, _ -> backend(
            works = { works++; success("不应触发") },
            courses = {
                progressCalls++
                if (progressCalls > 1) assertTrue(guard.requests.pause.value != null)
                if (blocked) ChxCourseOverview(listOf(course), if (progressCalls == 1) "请验证" else null,
                    requestPause = com.menglimi.chaoxing.ChxRequestPause(true))
                else ChxCourseOverview(listOf(course))
            }
        ) }, dispatcher, dispatcher)
        controller.refreshCourses(); controller.refresh()
        assertEquals(0, works)
        assertTrue(controller.courses.value.readControl.progressBlocked)
        assertTrue(controller.verifyFromWeb(false) != null)
        assertNull(controller.courses.value.progressError)
        assertTrue(controller.courses.value.readControl.pause!!.verificationRequired)
        assertTrue(store.value!!.readControl.pause!!.verificationRequired)
        blocked = false
        assertNull(controller.verifyFromWeb(false))
        assertEquals(3, progressCalls)
        assertEquals(0, works)
        assertNull(controller.courses.value.readControl.pause)
        assertNull(store.value!!.readControl.pause)
    }

    @Test fun cancellingLaterCourseKeepsEarlierResultsAndRemainingQueueOnReload() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val second = workCourse.copy(courseId = 4)
        val started = CompletableDeferred<Unit>()
        val waiting = CompletableDeferred<List<ChxCourseWorkResult>>()
        val store = MemoryStore(null)
        val controller = LearningController(store, { accountA }, {}, { _, _, _ -> object : LearningBackend {
            override suspend fun workCourses() = listOf(workCourse, second)
            override suspend fun works(courses: List<ChxWorkCourse>): List<ChxCourseWorkResult> {
                if (courses.single() == workCourse) return success("取消前读取的作业")
                started.complete(Unit)
                return waiting.await()
            }
            override suspend fun courses() = ChxCourseOverview(listOf(course))
        } }, dispatcher, dispatcher)
        val reading = launch { controller.refresh() }
        runCurrent(); started.await()
        reading.cancel(); reading.join()
        assertEquals("取消前读取的作业", store.value!!.courses.first().works.single().title)
        assertEquals(listOf(second.cacheKey()), store.value!!.readControl.pendingCourseKeys)
        val restored = LearningController(store, { accountA }, {}, { _, _, _ -> backend() }, dispatcher, dispatcher)
        restored.reload()
        assertEquals("取消前读取的作业", restored.state.value.works.single().title)
        assertEquals(listOf(second.cacheKey()), restored.state.value.readControl.pendingCourseKeys)
    }

    @Test fun manualFolderSurvivesReloadAndOnlyNarrowsTheNextReadBatch() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val second = workCourse.copy(courseId = 4, name = "第二门")
        val calls = mutableListOf<Long>()
        val store = MemoryStore(cached())
        val controller = LearningController(store, { accountA }, {}, { _, _, _ -> object : LearningBackend {
            override suspend fun workCourses() = listOf(workCourse, second)
            override suspend fun works(courses: List<ChxWorkCourse>): List<ChxCourseWorkResult> {
                calls += courses.single().courseId
                return listOf(ChxCourseWorkResult.Success(courses.single(), emptyList()))
            }
            override suspend fun courses() = ChxCourseOverview(listOf(course), loadedAt = 200)
        } }, dispatcher, dispatcher)
        controller.reload()
        controller.refresh()
        assertEquals(listOf(1L, 4L), calls)
        val firstUpdated = store.value!!.courses.single { it.course?.courseId == 4L }.updatedAt
        assertNull(controller.createFolder("只读这门", null))
        val folderId = controller.courses.value.library.folders.single().id
        assertNull(controller.moveCourse(workCourse.cacheKey(), folderId))
        assertNull(controller.selectCourseScope(LearningScope.FOLDER, folderId))
        calls.clear()
        controller.refresh()
        // 文件夹外的课程不进入本轮读取，但保留上次结果且不计失败。
        assertEquals(listOf(1L), calls)
        val kept = store.value!!.courses.single { it.course?.courseId == 4L }
        assertEquals(firstUpdated, kept.updatedAt)
        assertNull(kept.failure)
        assertEquals(folderId, store.value!!.library.assignments[workCourse.cacheKey()])
        val restored = LearningController(store, { accountA }, {}, { _, _, _ -> backend() }, dispatcher, dispatcher)
        restored.reload()
        assertEquals(LearningScope.FOLDER, restored.courses.value.library.scope)
        assertEquals(folderId, restored.courses.value.library.selectedId)
        assertEquals(listOf("1"), restored.courses.value.visible.map { it.courseId })
    }

    @Test fun calendarTermScopeOnlyReadsCoursesWithMatchingJoinDate() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val unknown = workCourse.copy(courseId = 4, name = "无加入时间")
        val dated = workCourse.copy(courseId = 7, name = "秋季加入", joinedOn = "2026-10-08")
        val calls = mutableListOf<Long>()
        val controller = LearningController(MemoryStore(null), { accountA }, {}, { _, _, _ -> object : LearningBackend {
            override suspend fun workCourses() = listOf(unknown, dated)
            override suspend fun works(courses: List<ChxWorkCourse>): List<ChxCourseWorkResult> {
                calls += courses.single().courseId
                return listOf(ChxCourseWorkResult.Success(courses.single(), emptyList()))
            }
            override suspend fun courses() = ChxCourseOverview(emptyList())
        } }, dispatcher, dispatcher)
        val backup = """{"app":"gdust-v2","schema":1,"calendar":{"year":"2026","semester":"1","startDate":"2026-10-01","allWeek":14}}"""
        assertNull(controller.importCalendar(backup))
        assertNull(controller.selectCourseScope(LearningScope.TERM, "2026_1"))
        controller.refresh()
        assertEquals(listOf(7L), calls)
        // 没有加入时间的课程不会被开课时间或首次发现时间代替，只出现在待确认里。
        calls.clear()
        assertNull(controller.selectCourseScope(LearningScope.UNASSIGNED, null))
        controller.refresh()
        assertEquals(listOf(4L), calls)
    }

    private fun backend(works: suspend () -> List<ChxCourseWorkResult> = { success("合成新作业") },
                        courses: suspend () -> ChxCourseOverview = { ChxCourseOverview(listOf(course), loadedAt = 200) }): LearningBackend {
        val readWorks = works
        val readCourses = courses
        return object : LearningBackend {
            override suspend fun workCourses() = listOf(workCourse)
            override suspend fun works(courses: List<ChxWorkCourse>) = readWorks()
            override suspend fun courses() = readCourses()
        }
    }
    private class MemoryStore(var value: LearningSnapshot?) : LearningPersistence {
        var writes = 0
        override fun read() = value
        override fun write(snapshot: LearningSnapshot) { value = snapshot; writes++ }
        override fun clear() { value = null }
    }
}
