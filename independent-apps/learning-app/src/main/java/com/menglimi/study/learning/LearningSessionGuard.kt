package com.menglimi.study.learning

import com.menglimi.chaoxing.ChxSso
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicLongArray

internal enum class LearningLane { WORK, COURSES }
internal data class LearningRequest(val generation: Long, val lane: LearningLane, val revision: Long)

/** Cookie、快照、清除共用一把锁；两条数据流各自只允许最新请求提交。 */
internal class LearningSessionGuard(val requests: com.menglimi.chaoxing.ChxRequestGate = com.menglimi.chaoxing.ChxRequestGate()) {
    val mutex = Mutex()
    @Volatile private var generation = 0L
    private val revisions = AtomicLongArray(LearningLane.entries.size)

    fun current(): Long = generation
    fun check(expected: Long) {
        if (generation != expected) throw CancellationException("学习通会话已变更")
    }
    fun begin(lane: LearningLane): LearningRequest =
        LearningRequest(generation, lane, revisions.incrementAndGet(lane.ordinal))
    fun isCurrent(request: LearningRequest): Boolean =
        generation == request.generation && revisions[request.lane.ordinal] == request.revision
    fun check(request: LearningRequest) {
        if (!isCurrent(request)) throw CancellationException("学习通请求已被替换")
    }
    /** 仅在 mutex 内调用，防止 Set-Cookie 已提交但清除先完成。 */
    fun invalidate(): Long = (++generation)
}

internal class LearningAccountChanged : Exception("学习通账号已变化，请重新检查登录状态。")

/** UID 只作为缓存归属指纹，绝不是已登录证据；原始 Cookie 不进入业务缓存。 */
internal fun learningAccountFingerprint(header: String?): String? {
    val identities = ChxSso.cookiePairs(header)
        .filter { it.first in setOf("_uid", "UID") && it.second.isNotBlank() }
        .map { it.second }.distinct()
    if (identities.size != 1) return null
    return MessageDigest.getInstance("SHA-256").digest(identities.single().toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
}
