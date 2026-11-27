package com.menglimi.gdust.v2.data

import com.menglimi.chaoxing.ChxRequestGate
import com.menglimi.chaoxing.ChxResponse
import com.menglimi.chaoxing.requestPause
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** Repository 共用一个闸门；恢复目标只保留在内存，不落盘 URL、签名或 Cookie。 */
internal class ChxRequestControl(private val gate: ChxRequestGate = ChxRequestGate()) {
    val pause = gate.pause
    private val lock = Any()
    private var recovery: Recovery? = null
    private class Recovery(val accountVersion: Long, val read: suspend (Any, () -> Unit) -> Unit)
    private class ReadOwner(val recovery: Recovery)

    fun check() = gate.check()

    /** HTTP 回调先锁存已收到的暂停，协程取消不能丢弃服务端要求。 */
    fun recordResponse(owner: Any, response: SessionResponse) {
        response.toChxResponse().requestPause()?.let { stopped -> synchronized(lock) {
            gate.record(stopped.exception(), owner)
            if (owner is ReadOwner && gate.causedPause(owner)) recovery = owner.recovery
        } }
    }

    suspend fun execute(owner: Any, checkSession: () -> Unit, request: suspend () -> SessionResponse): SessionResponse {
        val response = gate.execute(owner, checkSession) { request().toChxResponse() }
        return SessionResponse(response.status, response.body, response.location, response.retryAfter, response.serverDate)
    }

    /** 每个可重查的业务拥有独立 owner，排队失败不能覆盖真正触发暂停的详情/课程。 */
    suspend fun <T> read(
        accountVersion: Long,
        checkSession: () -> Unit,
        validateRecovery: (T) -> Boolean = { true },
        load: suspend (Any, () -> Unit) -> T,
    ): T {
        val target = Recovery(accountVersion) { recoveryOwner, checkCurrent ->
            val result = load(recoveryOwner, checkCurrent)
            checkCurrent()
            if (!validateRecovery(result)) throw ChxCourseReadException(
                "受阻业务尚未确认读取成功，学习通仍保持暂停。请完成网页验证后重试。")
        }
        val owner = ReadOwner(target)
        try {
            checkSession()
            return load(owner, checkSession)
        } finally {
            synchronized(lock) {
                if (pause.value != null && gate.causedPause(owner)) recovery = target
            }
        }
    }

    /** 只能由明确的人工操作调用；首页探活、刷新和 Activity 返回均不进入此方法。 */
    suspend fun recover(accountVersion: Long, checkSession: () -> Unit, afterWebVerification: Boolean) {
        if (pause.value == null) return
        val target = synchronized(lock) { recovery }?.takeIf { it.accountVersion == accountVersion }
            ?: throw ChxCourseReadException("无法确认此前受阻的业务，学习通仍保持暂停，请在网页查看。")
        val owner = Any()
        gate.beginRecovery(owner, afterWebVerification)
        var successful = false
        try {
            checkSession()
            target.read(owner, checkSession)
            currentCoroutineContext().ensureActive()
            checkSession()
            successful = true
        } finally {
            gate.finishRecovery(owner, successful)
            if (successful && pause.value == null) synchronized(lock) { if (recovery === target) recovery = null }
        }
    }
}

internal fun SessionResponse.toChxResponse() = ChxResponse(status, body, location, retryAfter, serverDate)
