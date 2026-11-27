package com.menglimi.gdust.v2.data

import com.menglimi.chaoxing.ChxRequestGate
import com.menglimi.chaoxing.ChxRequestPause
import com.menglimi.chaoxing.ChxResponse
import com.menglimi.chaoxing.requestPause
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** Repository 共用一个闸门；恢复目标只保留在内存，不落盘 URL、签名或 Cookie。 */
internal class ChxRequestControl(
    private val gate: ChxRequestGate = ChxRequestGate(),
    private val now: () -> Long = System::currentTimeMillis,
) {
    internal data class Identity(val epoch: Long, val uid: String?)
    val pause = gate.pause
    private val lock = Any()
    private var identity = Identity(0, null)
    private var recovery: Recovery? = null
    private val otherOwners = java.util.WeakHashMap<Any, Identity>()
    private class Recovery(
        val accountVersion: Long,
        val identity: Identity,
        val read: suspend (Any, () -> Unit) -> Unit,
    )
    private class Owner(val identity: Identity, val checkIdentity: () -> Unit, val target: Recovery? = null) {
        var causedPause = false
    }
    // 核心闸门只负责串行与间隔；暂停在本层锁内按身份记录，避免 check 与 record 之间换号。
    private class RecordedPause(val failure: ChxCourseReadException) : RuntimeException(failure)

    fun currentIdentity(): Identity = synchronized(lock) { identity }

    fun bindIdentity(uid: String): Identity = synchronized(lock) {
        require(uid.isNotBlank())
        if (identity.uid != uid) changeIdentity(uid)
        identity
    }

    fun invalidateIdentity() = synchronized(lock) { changeIdentity(null) }

    private fun changeIdentity(uid: String?) {
        identity = Identity(identity.epoch + 1, uid)
        recovery = null
        // 服务端冷却与本机身份无关，退出或重新登录不能绕过 429。
        gate.restore(pause.value?.retryAt?.takeIf { it > 0 }?.let { ChxRequestPause(false, it) })
    }

    fun checkIdentity(expected: Identity) = synchronized(lock) {
        if (identity != expected) throw CancellationException("学习通身份已变更")
    }

    /** 身份校验与缓存提交共用身份切换的锁，旧结果不能在校验后跨账号落盘。 */
    fun <T> withIdentity(
        expected: Identity,
        checkIdentity: () -> Unit = {},
        commit: () -> T,
    ): T = synchronized(lock) {
        this.checkIdentity(expected)
        checkIdentity()
        this.checkIdentity(expected)
        gate.check()
        commit()
    }

    fun check() = synchronized(lock) {
        releaseOrphanCooldown()
        gate.check()
    }

    private fun releaseOrphanCooldown() {
        val stopped = pause.value ?: return
        if (recovery == null && !stopped.verificationRequired && stopped.retryAt > 0) {
            val owner = Any()
            gate.beginRecovery(owner, afterWebVerification = false)
            gate.finishRecovery(owner, successful = true)
        }
    }

    private fun ownerIdentity(owner: Any): Identity? = (owner as? Owner)?.identity ?: otherOwners[owner]

    private fun identityStillCurrent(owner: Any): Boolean {
        // 仅核对身份；不能把 Job 取消或普通 request version 失效当成拒收暂停的理由。
        if (owner is Owner && runCatching { owner.checkIdentity() }.isFailure) return false
        return synchronized(lock) { ownerIdentity(owner) == identity }
    }

    private fun recordFailure(owner: Any, failure: ChxCourseReadException) {
        if (!failure.pausesRequests || !identityStillCurrent(owner)) return
        synchronized(lock) {
            if (ownerIdentity(owner) != identity) return
            gate.record(failure, owner)
            if (owner is Owner && owner.target != null && gate.causedPause(owner)) {
                owner.causedPause = true
                recovery = owner.target
            }
        }
    }

    /** HTTP 回调先锁存已收到的暂停；同身份取消不能丢弃服务端要求，旧身份响应不能污染新会话。 */
    fun recordResponse(owner: Any, response: SessionResponse) {
        response.toChxResponse().requestPause(now())?.let { recordFailure(owner, it.exception()) }
    }

    suspend fun execute(owner: Any, checkSession: () -> Unit, request: suspend () -> SessionResponse): SessionResponse {
        synchronized(lock) {
            if (owner !is Owner && !otherOwners.containsKey(owner)) otherOwners[owner] = identity
            releaseOrphanCooldown()
        }
        fun checkCurrent() {
            if (!identityStillCurrent(owner)) throw CancellationException("学习通身份已变更")
            checkSession()
        }
        val response = try {
            gate.execute(owner, ::checkCurrent) {
                try {
                    val result = request()
                    result.toChxResponse().requestPause(now())?.let { stopped ->
                        recordFailure(owner, stopped.exception())
                        throw RecordedPause(stopped.exception())
                    }
                    result.toChxResponse()
                } catch (failure: ChxCourseReadException) {
                    recordFailure(owner, failure)
                    throw RecordedPause(failure)
                }
            }
        } catch (recorded: RecordedPause) {
            throw recorded.failure
        }
        return SessionResponse(response.status, response.body, response.location, response.retryAfter, response.serverDate)
    }

    /** 每个可重查的业务拥有独立 owner；重查只使用新入口提供的检查，不捕获旧请求版本。 */
    suspend fun <T> read(
        accountVersion: Long,
        checkSession: () -> Unit,
        validateRecovery: (T) -> Boolean = { true },
        identity: Identity = currentIdentity(),
        checkIdentity: () -> Unit = {},
        load: suspend (Any, () -> Unit) -> T,
    ): T {
        val target = Recovery(accountVersion, identity) { recoveryOwner, checkCurrent ->
            val result = load(recoveryOwner, checkCurrent)
            checkCurrent()
            if (!validateRecovery(result)) throw ChxCourseReadException(
                "受阻业务尚未确认读取成功，学习通仍保持暂停。请完成网页验证后重试。")
        }
        val owner = Owner(identity, checkIdentity, target)
        fun checkCurrent() {
            this.checkIdentity(expected = identity)
            checkIdentity()
            checkSession()
        }
        try {
            checkCurrent()
            return load(owner, ::checkCurrent).also { checkCurrent() }
        } finally {
            if (identityStillCurrent(owner)) synchronized(lock) {
                if (this.identity == identity && owner.causedPause && pause.value != null && gate.causedPause(owner)) recovery = target
            }
        }
    }

    /** 只能由明确的人工操作调用；首页探活、刷新和 Activity 返回均不进入此方法。 */
    suspend fun recover(
        accountVersion: Long,
        checkSession: () -> Unit,
        afterWebVerification: Boolean,
        identity: Identity = currentIdentity(),
        checkIdentity: () -> Unit = {},
    ) {
        fun checkCurrent() {
            this.checkIdentity(expected = identity)
            checkIdentity()
            checkSession()
        }
        checkCurrent()
        val target: Recovery?
        val owner: Owner
        synchronized(lock) {
            this.checkIdentity(expected = identity)
            if (pause.value == null) return
            target = recovery
            if (target != null && (target.accountVersion != accountVersion || target.identity != identity)) {
                throw ChxCourseReadException("当前身份与此前受阻业务不同，请在网页查看。")
            }
            if (target == null && (pause.value!!.verificationRequired || pause.value!!.retryAt == 0L)) {
                throw ChxCourseReadException("无法确认此前受阻的业务，学习通仍保持暂停，请在网页查看。")
            }
            owner = Owner(identity, checkIdentity, target)
            gate.beginRecovery(owner, afterWebVerification)
        }
        var successful = false
        try {
            checkCurrent()
            target?.read?.invoke(owner, ::checkCurrent)
            currentCoroutineContext().ensureActive()
            checkCurrent()
            successful = true
        } finally {
            synchronized(lock) {
                if (this.identity == identity) {
                    gate.finishRecovery(owner, successful)
                    if (successful && pause.value == null && recovery === target) recovery = null
                }
            }
        }
    }
}

internal fun SessionResponse.toChxResponse() = ChxResponse(status, body, location, retryAfter, serverDate)
