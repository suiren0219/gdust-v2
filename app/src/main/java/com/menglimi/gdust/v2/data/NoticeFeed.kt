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

/** 主线程使用：仅手动刷新，切源恢复内存缓存，拒绝退出、换号及较早请求的迟到结果。 */
internal class NoticeFeed(
    private val currentSession: () -> PortalSession?,
    private val fetch: suspend (PortalSession, NoticeSource) -> List<Notice>,
    private val fetchAcademic: suspend () -> List<Notice>,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val mutableState = MutableStateFlow(NoticeFeedState())
    val state = mutableState.asStateFlow()
    private val cache = mutableMapOf<NoticeSource, NoticeFeedState>()
    private var owner: PortalSession? = null
    private var requestVersion = 0L
    private var request: Job? = null

    fun clear() {
        cancelRequest()
        owner = null
        cache.clear()
        mutableState.value = NoticeFeedState()
    }

    /** 离开通知页时停止正在进行的刷新，保留本次页面会话中的成功结果。 */
    fun cancelRefresh() {
        cancelRequest()
        mutableState.value = mutableState.value.copy(loading = false)
    }

    /** 打开/切换来源不触发 I/O；切源中断旧刷新，换号时丢弃整份缓存。 */
    fun select(source: NoticeSource) {
        val session = currentSession()
        val previous = mutableState.value
        if (session == owner && source == previous.source) return
        cancelRequest()
        if (session != owner) cache.clear()
        else cache[previous.source] = previous.copy(loading = false)
        owner = session
        mutableState.value = cache[source] ?: NoticeFeedState(source = source)
    }

    fun refresh(scope: CoroutineScope, source: NoticeSource = state.value.source) {
        select(source)
        // 快速连点复用当前请求，不取消重开，也不在完成后排队自动刷新。
        if (mutableState.value.loading) return
        val session = currentSession()
        val version = ++requestVersion
        if (session == null && source != NoticeSource.ACADEMIC) {
            mutableState.value = mutableState.value.copy(error = "请先登录学校门户后再获取${source.noticeLabel}。")
            return
        }
        mutableState.value = mutableState.value.copy(loading = true, error = null)
        fun canPublish() = version == requestVersion && session == currentSession() &&
            source == mutableState.value.source
        request = scope.launch {
            try {
                // 门户的通用原文链接会重复，不能以 URL/readId 合并不同标题的公告。
                val notices = (if (source == NoticeSource.ACADEMIC) fetchAcademic()
                    else fetch(checkNotNull(session), source)).sortedByDescending { it.time }
                if (canPublish()) mutableState.value = NoticeFeedState(
                    source = source, notices = notices, updatedAt = clock(),
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (canPublish()) mutableState.value = mutableState.value.copy(
                    error = when {
                        e.isNetworkDown() -> "网络连接失败，请检查网络后重试。"
                        source == NoticeSource.ACADEMIC -> "教务处公开公告暂时无法读取，请稍后重试或查看官网。"
                        e is AuthFailed -> "学校门户登录已失效，请重新登录后刷新。"
                        else -> "公告加载失败，请稍后重试。"
                    },
                )
            } finally {
                if (canPublish()) mutableState.value = mutableState.value.copy(loading = false)
            }
        }
    }

    private fun cancelRequest() {
        requestVersion++
        request?.cancel()
        request = null
    }
}
