package com.menglimi.gdust.v2.data

import com.menglimi.chaoxing.ChxRequestGate
import com.menglimi.chaoxing.ChxResponse
import com.menglimi.chaoxing.ChxCookieWriteException
import com.menglimi.chaoxing.requestPause
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.StateFlow

/** Repository 内学习通请求共享同一 gate；恢复目标仅保存在内存且绑定门户账号代数。 */
internal class ChxRequestControl(
    private val gate: ChxRequestGate = ChxRequestGate(),
) {
    val pause: StateFlow<com.menglimi.chaoxing.ChxRequestPause?> = gate.pause
    private val lock = Any()
    private var recovery: Recovery? = null

    private class ReadOwner(val target: Recovery)
    private class Recovery(
        val accountVersion: Long,
        val run: suspend (Any, () -> Unit) -> Any?,
    )

    fun check(owner: Any? = null) = gate.check(owner)

    fun reset() = synchronized(lock) {
        recovery = null
        gate.restore(null)
    }

    /** HTTP 回调先锁存暂停，再让 gate.check 阻止 Cookie 写入与业务结果继续传播。 */
    fun recordResponse(owner: Any, response: SessionResponse) = synchronized(lock) {
        val stopped = response.toChxResponse().requestPause() ?: return@synchronized
        gate.record(stopped.exception(), owner)
        if (gate.causedPause(owner) && owner is ReadOwner) recovery = owner.target
    }

    fun recordFailure(owner: Any, failure: ChxCookieWriteException) = synchronized(lock) {
        if (failure.pausesRequests) {
            gate.record(failure, owner)
            if (gate.causedPause(owner) && owner is ReadOwner) recovery = owner.target
        }
    }

    suspend fun execute(
        owner: Any,
        checkSession: () -> Unit,
        request: suspend () -> SessionResponse,
    ): SessionResponse {
        val response = gate.execute(owner, checkSession) { request().toChxResponse() }
        return response.toSessionResponse()
    }

    /** 每个原业务流程持有独立 owner；取消或并发迟到回调不覆盖暂停来源。 */
    suspend fun <T> read(
        accountVersion: Long,
        checkSession: () -> Unit,
        validateRecovery: (T) -> Boolean = { true },
        recoveryResult: suspend (T) -> Any? = { it },
        load: suspend (Any, () -> Unit) -> T,
    ): T {
        val target = Recovery(accountVersion) { owner, checkCurrent ->
            val result = load(owner, checkCurrent)
            checkCurrent()
            if (!validateRecovery(result)) throw ChxCourseReadException(
                "网页检查后原学习通业务仍未确认读取成功；请求继续暂停。请在网页完成检查后重试。",
            )
            recoveryResult(result)
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

    /** 仅显式人工操作可恢复；只有原业务重新执行并通过校验才清除暂停。 */
    suspend fun recover(
        accountVersion: Long,
        checkSession: () -> Unit,
        afterWebVerification: Boolean,
    ) {
        if (pause.value == null) return
        val target = synchronized(lock) { recovery }
            ?.takeIf { it.accountVersion == accountVersion }
            ?: throw ChxCourseReadException("无法确认此前受阻的学习通业务，请在网页查看；请求继续暂停。")
        val owner = Any()
        gate.beginRecovery(owner, afterWebVerification)
        var successful = false
        try {
            checkSession()
            target.run(owner, checkSession)
            currentCoroutineContext().ensureActive()
            checkSession()
            successful = true
        } finally {
            gate.finishRecovery(owner, successful)
            if (successful && pause.value == null) synchronized(lock) {
                if (recovery === target) recovery = null
            }
        }
    }
}

internal fun SessionResponse.toChxResponse() = ChxResponse(status, body, location, retryAfter, serverDate)

private fun ChxResponse.toSessionResponse() = SessionResponse(status, body, location, retryAfter, serverDate)
