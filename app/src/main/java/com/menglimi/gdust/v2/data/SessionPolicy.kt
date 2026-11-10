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
