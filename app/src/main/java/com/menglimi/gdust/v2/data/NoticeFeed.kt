package com.menglimi.gdust.v2.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

internal data class PortalSession(val account: String, val token: String, val version: Long) {
    override fun toString(): String = "PortalSession([redacted])"
}

internal data class NoticeFeedState(
    val source: NoticeSource = NoticeSource.PORTAL,
    val notices: List<Notice> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null,
    val updatedAt: Long = 0L,
)

/** 主线程使用：取消旧刷新，并拒绝退出、换号及较早请求的迟到结果。 */
internal class NoticeFeed(
    private val currentSession: () -> PortalSession?,
    private val fetch: suspend (PortalSession, NoticeSource) -> List<Notice>,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val mutableState = MutableStateFlow(NoticeFeedState())
    val state = mutableState.asStateFlow()
    private var owner: PortalSession? = null
    private var requestVersion = 0L
    private var request: Job? = null

    fun clear() {
        requestVersion++
        request?.cancel()
        request = null
        owner = null
        mutableState.value = NoticeFeedState()
    }

    fun refresh(scope: CoroutineScope, source: NoticeSource = state.value.source) {
        val session = currentSession()
        val reset = session != owner || source != mutableState.value.source
        val version = ++requestVersion
        request?.cancel()
        request = null
        owner = session
        if (session == null) {
            mutableState.value = NoticeFeedState(source = source, error = "请先登录学校门户后再获取${source.noticeLabel}。")
            return
        }
        mutableState.value = if (reset) NoticeFeedState(source = source, loading = true)
        else mutableState.value.copy(loading = true, error = null)
        fun canPublish() = version == requestVersion && session == currentSession() &&
            source == mutableState.value.source
        request = scope.launch {
            try {
                // 门户的通用原文链接会重复，不能以 URL/readId 合并不同标题的公告。
                val notices = fetch(session, source).sortedByDescending { it.time }
                if (canPublish()) mutableState.value = NoticeFeedState(
                    source = source, notices = notices, updatedAt = clock(),
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (canPublish()) mutableState.value = mutableState.value.copy(
                    error = when {
                        e is AuthFailed -> "学校门户登录已失效，请重新登录后刷新。"
                        e.isNetworkDown() -> "网络连接失败，请检查网络后重试。"
                        else -> "公告加载失败，请稍后重试。"
                    },
                )
            } finally {
                if (canPublish()) mutableState.value = mutableState.value.copy(loading = false)
            }
        }
    }
}
