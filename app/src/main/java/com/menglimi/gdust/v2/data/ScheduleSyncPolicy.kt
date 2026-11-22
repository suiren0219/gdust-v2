package com.menglimi.gdust.v2.data

/** 学校会话维护与课表同步共用到期时间，不因重开 App 延后。 */
object ScheduleSyncPolicy {
    const val INTERVAL_MILLIS = 4 * 60 * 60_000L
    const val RETRY_MILLIS = 15 * 60_000L

    fun nextAt(now: Long, lastSuccess: Long, lastAttempt: Long): Long {
        // 调整系统时间后，未来锚点不应阻止当前重试。
        val success = lastSuccess.takeIf { it > 0 && it <= now }
        val attempt = lastAttempt.takeIf { it > 0 && it <= now && it > (success ?: 0L) }
        return maxOf(now, success?.let { it + INTERVAL_MILLIS } ?: now,
            attempt?.let { it + RETRY_MILLIS } ?: now)
    }

    /** 夜间只暂停电量查询；学校到期点仍可复用同一条一次性 chain。 */
    fun nextWakeAt(powerAt: Long?, syncAt: Long?): Long? = when {
        powerAt == null -> syncAt
        syncAt == null -> powerAt
        else -> minOf(powerAt, syncAt)
    }
}
