package com.menglimi.gdust.v2.data

import java.io.Closeable
import java.io.IOException
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NoticeFeedTest {
    @Test
    fun sourceChangeImmediatelyClearsPreviousResultsAndLateRequestsCannotRestoreThem() = Fixture().use { f ->
        f.feed.refresh(f.scope)
        f.requests[0].complete(listOf(notice("已加载的门户通知")))
        f.feed.refresh(f.scope)
        val oldRequest = f.requests[1]
        f.feed.refresh(f.scope, NoticeSource.ACADEMIC)
        assertTrue(oldRequest.job.isCancelled)
        assertEquals(NoticeSource.ACADEMIC, f.feed.state.value.source)
        assertTrue(f.feed.state.value.notices.isEmpty())
        assertEquals(0L, f.feed.state.value.updatedAt)
        assertNull(f.feed.state.value.error)
        assertTrue(f.feed.state.value.loading)
        oldRequest.complete(listOf(notice("迟到门户通知")))
        assertTrue(f.feed.state.value.notices.isEmpty())
        assertTrue(f.feed.state.value.loading)
        val academic = notice("虚构教务处公告")
        f.requests[2].complete(listOf(academic))
        assertEquals(listOf(academic), f.feed.state.value.notices)
        assertFalse(f.feed.state.value.loading)
        assertEquals(NoticeSource.ACADEMIC, f.requests[2].source)
    }

    @Test
    fun sameSourceFailureKeepsLastSuccessAndItsTimestamp() = Fixture().use { f ->
        f.feed.refresh(f.scope, NoticeSource.CAMPUS)
        val previous = notice("虚构官网新闻")
        f.requests[0].complete(listOf(previous))
        f.now = 2_000L
        f.feed.refresh(f.scope)
        assertEquals(NoticeSource.CAMPUS, f.requests[1].source)
        assertEquals(listOf(previous), f.feed.state.value.notices)
        f.requests[1].fail(IOException("synthetic-private-response"))
        assertEquals(listOf(previous), f.feed.state.value.notices)
        assertEquals(1_000L, f.feed.state.value.updatedAt)
        assertEquals("网络连接失败，请检查网络后重试。", f.feed.state.value.error)
        assertFalse(f.feed.state.value.loading)
    }

    @Test
    fun newSourceFailureDoesNotKeepOtherSourcesDataOrTimestamp() = Fixture().use { f ->
        f.feed.refresh(f.scope)
        f.requests[0].complete(listOf(notice("门户旧结果")))
        f.feed.refresh(f.scope, NoticeSource.CAMPUS)
        f.requests[1].fail(IllegalStateException("synthetic-response-body"))
        assertEquals(NoticeSource.CAMPUS, f.feed.state.value.source)
        assertTrue(f.feed.state.value.notices.isEmpty())
        assertEquals(0L, f.feed.state.value.updatedAt)
        assertEquals("公告加载失败，请稍后重试。", f.feed.state.value.error)
        assertFalse(f.feed.state.value.loading)
    }

    @Test
    fun switchingBackToTheOriginalSourceStillRejectsItsEarlierRequest() = Fixture().use { f ->
        f.feed.refresh(f.scope, NoticeSource.CAMPUS)
        f.feed.refresh(f.scope, NoticeSource.ACADEMIC)
        f.feed.refresh(f.scope, NoticeSource.CAMPUS)
        f.requests[0].complete(listOf(notice("旧官网请求")))
        f.requests[1].fail(AuthFailed(117, "synthetic-stale-auth-error"))
        assertEquals(NoticeSource.CAMPUS, f.feed.state.value.source)
        assertTrue(f.feed.state.value.notices.isEmpty())
        assertNull(f.feed.state.value.error)
        assertTrue(f.feed.state.value.loading)
        val latest = notice("新官网请求")
        f.requests[2].complete(listOf(latest))
        assertEquals(listOf(latest), f.feed.state.value.notices)
        assertFalse(f.feed.state.value.loading)
    }

    @Test
    fun accountChangeClearsStoredResultsAndRejectsOldAccountsLateResponse() = Fixture().use { f ->
        f.feed.refresh(f.scope, NoticeSource.CAMPUS)
        f.requests[0].complete(listOf(notice("旧账号已有新闻")))
        f.feed.refresh(f.scope)
        val oldRequest = f.requests[1]
        f.session = PortalSession("test-account-b", "synthetic-token-b", 2L)
        f.feed.refresh(f.scope)
        assertTrue(oldRequest.job.isCancelled)
        assertTrue(f.feed.state.value.notices.isEmpty())
        assertEquals(0L, f.feed.state.value.updatedAt)
        assertEquals(NoticeSource.CAMPUS, f.feed.state.value.source)
        oldRequest.complete(listOf(notice("旧账号迟到新闻")))
        assertTrue(f.feed.state.value.notices.isEmpty())
        assertTrue(f.feed.state.value.loading)
        val current = notice("新账号新闻")
        f.requests[2].complete(listOf(current))
        assertEquals(f.session, f.requests[2].session)
        assertEquals(listOf(current), f.feed.state.value.notices)
    }

    @Test
    fun newerSessionVersionRejectsResultsEvenWhenAccountAndTokenAreUnchanged() = Fixture().use { f ->
        f.feed.refresh(f.scope)
        f.session = f.session!!.copy(version = 2L)
        f.feed.refresh(f.scope)
        val latest = notice("重新登录后的结果")
        f.requests[1].complete(listOf(latest))
        f.requests[0].fail(AuthFailed(116, "synthetic-old-session-failure"))
        assertEquals(listOf(latest), f.feed.state.value.notices)
        assertNull(f.feed.state.value.error)
        assertFalse(f.feed.state.value.loading)
    }

    @Test
    fun sessionChangeBeforeRefreshCannotPublishAnOldResponse() = Fixture().use { f ->
        f.feed.refresh(f.scope)
        f.session = f.session!!.copy(version = 2L)
        f.requests[0].complete(listOf(notice("会话失效后的结果")))
        assertTrue(f.feed.state.value.notices.isEmpty())
        assertEquals(0L, f.feed.state.value.updatedAt)
        f.feed.clear()
        assertEquals(NoticeFeedState(), f.feed.state.value)
    }

    @Test
    fun latestSameSourceRequestWinsWithoutChangingNoticeReadIdentity() = Fixture().use { f ->
        f.feed.refresh(f.scope)
        f.feed.refresh(f.scope)
        assertTrue(f.requests[0].job.isCancelled)
        val latest = notice("最新门户结果")
        val readId = latest.readId
        f.requests[1].complete(listOf(latest))
        f.requests[0].complete(listOf(notice("较早门户结果")))
        assertEquals(listOf(latest), f.feed.state.value.notices)
        assertEquals(readId, f.feed.state.value.notices.single().readId)
        assertNull(f.feed.state.value.error)
        assertFalse(f.feed.state.value.loading)
    }

    @Test
    fun genericOriginalUrlDoesNotMergeDifferentAnnouncementsAndUnknownDatesSortLast() = Fixture().use { f ->
        val first = notice("第一条通知", 2_000L)
        val second = first.copy(title = "同链接同时间的另一条通知")
        val older = first.copy(title = "较早通知", time = 1_000L)
        val unknown = first.copy(title = "日期未知通知", time = 0L)
        f.feed.refresh(f.scope)
        f.requests[0].complete(listOf(unknown, older, first, second))
        assertEquals(listOf(first, second, older, unknown), f.feed.state.value.notices)
    }

    @Test
    fun validEmptyResponseIsSuccessfulAndReplacesPreviousResults() = Fixture().use { f ->
        f.feed.refresh(f.scope)
        f.requests[0].complete(listOf(notice("旧结果")))
        f.now = 2_000L
        f.feed.refresh(f.scope)
        f.requests[1].complete(emptyList())
        assertTrue(f.feed.state.value.notices.isEmpty())
        assertEquals(2_000L, f.feed.state.value.updatedAt)
        assertNull(f.feed.state.value.error)
        assertFalse(f.feed.state.value.loading)
    }

    @Test
    fun cancellationDoesNotBecomeAnErrorAndAuthenticationFailureRemainsExplicit() = Fixture().use { f ->
        val old = notice("保留的结果")
        f.feed.refresh(f.scope)
        f.requests[0].complete(listOf(old))
        f.feed.refresh(f.scope)
        f.requests[1].fail(CancellationException("synthetic-cancel"))
        assertEquals(listOf(old), f.feed.state.value.notices)
        assertNull(f.feed.state.value.error)
        assertFalse(f.feed.state.value.loading)
        f.feed.refresh(f.scope)
        f.requests[2].fail(AuthFailed(117, "synthetic-auth-response"))
        assertEquals("学校门户登录已失效，请重新登录后刷新。", f.feed.state.value.error)
        assertEquals(listOf(old), f.feed.state.value.notices)
        assertFalse(f.feed.state.value.loading)
    }

    @Test
    fun clearAndLoggedOutRefreshNeverPublishOldDataOrLaunchAnotherRequest() = Fixture().use { f ->
        f.feed.refresh(f.scope, NoticeSource.CAMPUS)
        f.feed.clear()
        assertTrue(f.requests[0].job.isCancelled)
        assertEquals(NoticeFeedState(), f.feed.state.value)
        f.session = null
        f.feed.refresh(f.scope, NoticeSource.ACADEMIC)
        assertEquals(1, f.requests.size)
        assertEquals(NoticeSource.ACADEMIC, f.feed.state.value.source)
        assertEquals("请先登录学校门户后再获取教务处公告。", f.feed.state.value.error)
        f.requests[0].complete(listOf(notice("退出后的迟到响应")))
        assertTrue(f.feed.state.value.notices.isEmpty())
        assertFalse(f.feed.state.value.loading)
    }

    private fun notice(title: String, time: Long = 1_000L) = Notice(
        title, "https://app.dingtalk.com/blackboard/h5/listNotice", "门户通知", "示例部门", time,
    )

    /** 手动恢复的非取消挂起点模拟忽略取消的 I/O，无需真实等待或 coroutines-test。 */
    private class Fixture : Closeable {
        var session: PortalSession? = PortalSession("test-account-a", "synthetic-token-a", 1L)
        var now = 1_000L
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val requests = mutableListOf<Pending>()
        val feed = NoticeFeed(
            currentSession = { session },
            fetch = { session, source ->
                val job = currentCoroutineContext()[Job]!!
                suspendCoroutine { continuation -> requests += Pending(session, source, job, continuation) }
            },
            clock = { now },
        )

        override fun close() {
            feed.clear()
            requests.filterNot { it.finished }.forEach { it.complete(emptyList()) }
            scope.cancel()
        }
    }

    private class Pending(
        val session: PortalSession,
        val source: NoticeSource,
        val job: Job,
        private val continuation: Continuation<List<Notice>>,
    ) {
        var finished = false
            private set

        fun complete(notices: List<Notice>) {
            finished = true
            continuation.resume(notices)
        }

        fun fail(error: Exception) {
            finished = true
            continuation.resumeWithException(error)
        }
    }
}
