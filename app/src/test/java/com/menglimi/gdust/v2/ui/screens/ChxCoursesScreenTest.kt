package com.menglimi.gdust.v2.ui.screens

import com.menglimi.gdust.v2.data.AuthFailed
import com.menglimi.gdust.v2.data.ChxCourse
import com.menglimi.gdust.v2.data.ChxCourseOverview
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class ChxCoursesScreenTest {
    @Test fun refreshCancelsThePreviousJobAndRejectsItsLateResult() = runBlocking {
        val first = CompletableDeferred<ChxCourseOverview>()
        val second = CompletableDeferred<ChxCourseOverview>()
        var calls = 0
        val controller = ChxCoursesController(this, {
            if (++calls == 1) withContext(NonCancellable) { first.await() } else second.await()
        }, { it == 0L })
        try {
            controller.changeSession(0)
            controller.refresh()
            yield()
            controller.refresh()
            yield()
            val latest = overview("最新示例课程")
            second.complete(latest)
            yield()
            assertSame(latest, controller.state.value.overview)
            assertFalse(controller.state.value.loading)
            first.complete(overview("取消后的旧课程"))
            yield()
            assertSame(latest, controller.state.value.overview)
            assertNull(controller.state.value.error)
            assertEquals(2, calls)
        } finally {
            first.complete(overview("测试结束"))
            second.cancel()
            controller.cancel()
        }
    }

    @Test fun sessionChangeClearsDataAndRejectsThePreviousAccountsLateResponse() = runBlocking {
        var version = 0L
        val oldRequest = CompletableDeferred<ChxCourseOverview>()
        val newRequest = CompletableDeferred<ChxCourseOverview>()
        var calls = 0
        val controller = ChxCoursesController(this, {
            when (++calls) {
                1 -> overview("已读取的旧账号课程")
                2 -> withContext(NonCancellable) { oldRequest.await() }
                else -> newRequest.await()
            }
        }, { it == version })
        try {
            controller.changeSession(0)
            controller.refresh()
            yield()
            assertEquals("已读取的旧账号课程", controller.state.value.overview!!.courses.single().name)
            controller.refresh()
            yield()
            version = 1
            controller.changeSession(version)
            assertEquals(1L, controller.state.value.sessionVersion)
            assertNull(controller.state.value.overview)
            assertNull(controller.state.value.error)
            assertFalse(controller.state.value.loading)
            controller.refresh()
            yield()
            oldRequest.complete(overview("迟到的旧账号课程"))
            yield()
            assertNull(controller.state.value.overview)
            assertTrue(controller.state.value.loading)
            val latest = overview("新账号课程")
            newRequest.complete(latest)
            yield()
            assertSame(latest, controller.state.value.overview)
        } finally {
            oldRequest.complete(overview("测试结束"))
            newRequest.cancel()
            controller.cancel()
        }
    }

    @Test fun failureKeepsPreviousResultsMarksThemStaleAndNeverDisplaysRawExceptions() = runBlocking {
        val first = overview("先前课程")
        val latest = overview("刷新后的课程")
        var calls = 0
        val controller = ChxCoursesController(this, {
            when (++calls) {
                1 -> first
                2 -> throw IOException("raw-response and private-cookie")
                3 -> throw AuthFailed(401, "raw-auth-response")
                else -> latest
            }
        }, { it == 0L })
        controller.changeSession(0)
        controller.refresh()
        yield()
        controller.refresh()
        yield()
        assertSame(first, controller.state.value.overview)
        assertTrue(controller.state.value.stale)
        assertEquals("课程读取失败，请检查网络后重试。", controller.state.value.error)
        assertFalse(controller.state.value.authRequired)
        assertFalse(controller.state.value.loading)
        controller.refresh()
        yield()
        assertSame(first, controller.state.value.overview)
        assertTrue(controller.state.value.stale)
        assertTrue(controller.state.value.authRequired)
        assertEquals("学习通会话已失效，请重新登录。", controller.state.value.error)
        controller.refresh()
        yield()
        assertSame(latest, controller.state.value.overview)
        assertFalse(controller.state.value.stale)
        assertFalse(controller.state.value.authRequired)
        assertNull(controller.state.value.error)
        controller.cancel()
    }

    @Test fun explicitCancellationDoesNotBecomeAnErrorOrClearExistingResults() = runBlocking {
        val first = overview("保留的课程")
        val pending = CompletableDeferred<ChxCourseOverview>()
        val stopped = CompletableDeferred<Unit>()
        var calls = 0
        val controller = ChxCoursesController(this, {
            if (++calls == 1) first else try { pending.await() } finally { stopped.complete(Unit) }
        }, { it == 0L })
        controller.changeSession(0)
        controller.refresh()
        yield()
        controller.refresh()
        yield()
        controller.cancel()
        stopped.await()
        assertSame(first, controller.state.value.overview)
        assertNull(controller.state.value.error)
        assertFalse(controller.state.value.loading)
        assertFalse(controller.state.value.stale)
    }

    @Test fun progressAuthenticationFailureKeepsCoursesAndOffersLogin() = runBlocking {
        val result = overview("进度未知的课程").copy(progressError = "进度未知，请重新登录。", progressAuthRequired = true)
        val controller = ChxCoursesController(this, { result }, { it == 0L })
        controller.changeSession(0)
        controller.refresh()
        yield()
        assertSame(result, controller.state.value.overview)
        assertTrue(controller.state.value.authRequired)
        assertFalse(controller.state.value.stale)
        assertNull(controller.state.value.error)
        controller.cancel()
    }

    private fun overview(name: String) = ChxCourseOverview(
        listOf(ChxCourse("101", "201", "301", name, "示例说明",
            "https://mooc1.chaoxing.com/visit/stucoursemiddle?courseid=201&clazzid=101&cpi=301&ismooc2=1&v=2")),
        loadedAt = 1_700_000_000_000L,
    )
}
