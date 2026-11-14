package com.menglimi.gdust.v2.data

import com.google.gson.JsonParser

/** 只有明确的认证拒绝才失效；超时、维护和解析错误均保留本机会话。 */
internal fun schoolAuthFailure(status: Int, body: String): AuthFailed? {
    val code = runCatching {
        JsonParser.parseString(body).asJsonObject.num("code")
    }.getOrNull()
    return when {
        code == 116 || code == 117 -> AuthFailed(code, "学校登录会话已失效，请重新登录")
        status == 401 -> AuthFailed(401, "学校登录会话已失效，请重新登录")
        else -> null
    }
}

internal fun shouldKeepSession(hasSavedSession: Boolean, failure: Throwable?): Boolean =
    hasSavedSession && failure !is AuthFailed

/**
 * 一次性票据只在「请求确定没有到达学校」时才可重放：
 * 连接失败、DNS 失败、TLS 握手失败和连接超时都发生在请求发出前；
 * 读超时则说明请求已经发出——票据可能已被消费，重放必然失败。
 */
internal fun Throwable.isOneShotRetrySafe(): Boolean {
    var t: Throwable? = this
    while (t != null) {
        when (t) {
            is java.net.ConnectException -> return true
            is java.net.UnknownHostException -> return true
            is javax.net.ssl.SSLHandshakeException -> return true
            is java.net.SocketTimeoutException ->
                if (t.message?.contains("connect", ignoreCase = true) == true) return true
        }
        t = t.cause
    }
    return false
}
