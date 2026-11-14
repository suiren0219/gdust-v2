package com.menglimi.gdust.v2.data

import kotlinx.coroutines.CancellationException

/** 退出或换号后，旧请求不得再读取新会话或写回本地数据。 */
internal class SessionGuard {
    private var version = 0L

    @Synchronized
    fun current(): Long = version

    @Synchronized
    fun invalidate(): Long = ++version

    @Synchronized
    fun <T> withCurrent(expected: Long, block: () -> T): T {
        if (expected != version) throw CancellationException("登录状态已变更")
        return block()
    }
}
