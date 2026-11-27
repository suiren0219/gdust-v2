package com.menglimi.gdust.v2.ui.screens

import com.menglimi.gdust.v2.data.ChxSso
import java.net.URI
import java.util.Locale

/** 恢复只在当前 HTTPS 学习通页面完成加载、无失败/挑战且有实时网页身份 Cookie 时就绪。 */
internal fun isChxWebRecoveryReady(
    currentSession: Boolean,
    url: String?,
    pageFinished: Boolean,
    mainDocumentFailed: Boolean,
    verificationPage: Boolean,
    candidateCookie: String?,
): Boolean {
    if (!currentSession || !pageFinished || mainDocumentFailed || verificationPage) return false
    val uri = url?.let { runCatching { URI(it) }.getOrNull() } ?: return false
    if (!uri.scheme.equals("https", ignoreCase = true) || uri.userInfo != null ||
        uri.port !in setOf(-1, 443) || uri.normalize().rawPath != uri.rawPath) return false
    val host = uri.host ?: return false
    if (!ChxSso.isChaoxingHost(host) && !host.equals("cas.gdust.edu.cn", ignoreCase = true)) return false
    val path = uri.path.orEmpty().lowercase(Locale.ROOT)
    if (listOf("login", "captcha", "verify", "validate").any(path::contains)) return false
    if (ChxSso.isLoginUrl(uri.toString())) return false
    return ChxSso.hasSessionCookie(candidateCookie)
}
