package com.menglimi.study.learning

import com.menglimi.chaoxing.ChxCourse
import com.menglimi.chaoxing.ChxCourseOverview
import com.menglimi.chaoxing.ChxCourseProgress
import com.menglimi.chaoxing.ChxCourseWorkResult
import com.menglimi.chaoxing.ChxWorkCourse
import com.menglimi.chaoxing.ChxWorkItem
import com.menglimi.chaoxing.ChxWorkWithDeadline
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

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
        val controller = LearningController(MemoryStore(cached()), { accountA }, {}, { _, _, _ -> backend(
            works = { works++; success("不应触发") },
            courses = { if (blocked) ChxCourseOverview(listOf(course), "请验证",
                requestPause = com.menglimi.chaoxing.ChxRequestPause(true)) else ChxCourseOverview(listOf(course)) }
        ) }, dispatcher, dispatcher)
        controller.refreshCourses(); controller.refresh()
        assertEquals(0, works)
        assertTrue(controller.courses.value.readControl.progressBlocked)
        blocked = false
        assertNull(controller.verifyFromWeb(false))
        assertEquals(0, works)
        assertNull(controller.courses.value.readControl.pause)
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
