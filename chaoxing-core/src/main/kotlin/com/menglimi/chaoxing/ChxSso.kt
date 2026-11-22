package com.menglimi.chaoxing

import java.net.URI
import java.util.Locale

/** 学习通通用会话候选与登录页判定；机构入口和其他认证服务由接入方提供。 */
object ChxSso {
    const val HOME = "https://mooc2-ans.chaoxing.com/mooc2-ans/visit/interaction"

    // Cookie 必须来自实际探活 URL，不能将其他主机的 host-only Cookie 用作候选。
    val cookieProbes: List<String> = listOf(HOME)
    private val identityCookieNames = setOf("_uid", "UID")
    private val cookieName = Regex("[!#$%&'*+.^_`|~0-9A-Za-z-]+")

    /** 解析 Cookie 请求头；保留同名条目，名称区分大小写，值允许包含 '='。 */
    fun cookiePairs(header: String?): List<Pair<String, String>> =
        header.orEmpty().split(';').mapNotNull { part ->
            val separator = part.indexOf('=')
            if (separator < 1) return@mapNotNull null
            val name = part.substring(0, separator).trim()
            val value = part.substring(separator + 1).trim().removeSurrounding("\"").trim()
            if (!cookieName.matches(name) || value.any { it.code < 32 || it.code == 127 }) null
            else name to value
        }

    /** 仅代表保存了可供验证的身份 Cookie；匿名 JSESSIONID 不是登录证据。 */
    fun hasSessionCookie(header: String?): Boolean = cookiePairs(header).any { (name, value) ->
        name in identityCookieNames && value.isNotBlank()
    }

    fun isChaoxingHost(host: String?): Boolean {
        val h = host.orEmpty().lowercase(Locale.ROOT)
        return h == "chaoxing.com" || h.endsWith(".chaoxing.com") ||
            h == "chaoxing.cn" || h.endsWith(".chaoxing.cn")
    }

    /** 只认登录主机/路径，不拿 query 里的 login 或 passport 字样判失效。 */
    fun isLoginUrl(url: String): Boolean {
        if (url.any { it.code <= 32 || it.code == 127 } || '\\' in url) return false
        val uri = try { URI(url) } catch (_: java.net.URISyntaxException) { return false }
        if (uri.scheme != "https" || uri.userInfo != null || uri.port !in setOf(-1, 443)) return false
        val host = uri.host?.lowercase(Locale.ROOT) ?: return false
        if (!isChaoxingHost(host)) return false
        val path = uri.path.orEmpty().trimEnd('/').lowercase(Locale.ROOT)
        val passport = host.substringBefore('.') in setOf("passport", "passport2")
        return path in setOf("/login", "/login.html", "/login/index", "/fanyalogin") ||
            (passport && (path.isEmpty() || path == "/mlogin"))
    }
}
