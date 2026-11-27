package com.menglimi.gdust.v2.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
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

/** 主线程使用：仅手动刷新，切源恢复本地缓存，拒绝退出、换号及较早请求的迟到结果。 */
internal class NoticeFeed(
    private val currentSession: () -> PortalSession?,
    private val fetch: suspend (PortalSession, NoticeSource) -> List<Notice>,
    private val fetchAcademic: suspend () -> List<Notice>,
    private val clock: () -> Long = System::currentTimeMillis,
    private val loadSnapshot: (NoticeSource) -> DataSnapshot<List<Notice>>? = { null },
    private val saveSnapshot: (NoticeSource, DataSnapshot<List<Notice>>) -> Unit = { _, _ -> },
) {
    private val mutableState = MutableStateFlow(NoticeFeedState())
    val state = mutableState.asStateFlow()
    private val cache = mutableMapOf<NoticeSource, NoticeFeedState>()
    private var owner: PortalSession? = null
    private var selected = false
    private var requestVersion = 0L
    private var request: Job? = null

    fun clear() {
        cancelRequest()
        owner = null
        selected = false
        cache.clear()
        mutableState.value = NoticeFeedState()
    }

    /** 离开通知页时停止正在进行的刷新，保留本次页面会话中的成功结果。 */
    fun cancelRefresh() {
        cancelRequest()
        mutableState.value = mutableState.value.copy(loading = false)
    }

    /** 打开/切换来源不触发 I/O；切源中断旧刷新，换号时恢复当前账号快照。 */
    fun select(source: NoticeSource) {
        val session = currentSession()
        val previous = mutableState.value
        if (selected && session == owner && source == previous.source) return
        val ownerChanged = !selected || session != owner
        cancelRequest()
        if (ownerChanged) {
            cache.clear()
            owner = session
        } else {
            cache[previous.source] = previous.copy(loading = false)
        }
        selected = true
        mutableState.value = if (ownerChanged) {
            restoreSnapshot(source) ?: NoticeFeedState(source = source)
        } else {
            cache[source] ?: restoreSnapshot(source) ?: NoticeFeedState(source = source)
        }
    }

    /** 应用创建时同步恢复默认源，使首帧 UI 不等待联网刷新。 */
    fun restoreInitial(source: NoticeSource = state.value.source) {
        select(source)
    }

    private fun restoreSnapshot(source: NoticeSource): NoticeFeedState? {
        val snapshot = runCatching { loadSnapshot(source) }.getOrNull() ?: return null
        if (snapshot.updatedAt <= 0L || snapshot.data.any { it.title.isBlank() || it.time < 0L }) return null
        return NoticeFeedState(
            source = source,
            notices = NoticeNoiseFilter.project(snapshot.data, source),
            updatedAt = snapshot.updatedAt,
        )
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
                val notices = if (source == NoticeSource.ACADEMIC) fetchAcademic()
                    else fetch(checkNotNull(session), source)
                val displayed = NoticeNoiseFilter.project(notices, source)
                currentCoroutineContext().ensureActive()
                if (canPublish()) {
                    val updatedAt = clock()
                    val snapshot = DataSnapshot(notices, updatedAt)
                    currentCoroutineContext().ensureActive()
                    if (canPublish()) {
                        saveSnapshot(source, snapshot)
                        currentCoroutineContext().ensureActive()
                        if (!canPublish()) return@launch
                        mutableState.value = NoticeFeedState(
                            source = source, notices = displayed, updatedAt = updatedAt,
                        )
                    }
                }
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
