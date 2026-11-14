package com.menglimi.gdust.v2.data

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/** 复用已有课表通道；WebVPN 前缀来自已验证的教务课表入口。 */
internal object TeachingEndpoints {
    val bases = listOf(
        TEACHING_ENDPOINT.substringBefore("/kbcx/"),
        PUBLIC_TEACHING_ENDPOINT.substringBefore("/kbcx/"),
        PUBLIC_TEACHING_ENDPOINT.substringBefore("/kbcx/").replace("http://", "https://"),
        VPN_TEACHING_ENDPOINT.substringBefore("/kbcx/"),
    )
    private val gradePaths = setOf("/cjcx/cjcx_cxDgXscj.html", "/cjcx/cjcx_cxXsgrcj.html")

    fun baseForPage(page: String): String? {
        val url = page.toHttpUrlOrNull() ?: return null
        if (url.username.isNotEmpty() || url.password.isNotEmpty()) return null
        return bases.firstOrNull { value ->
            val base = requireNotNull(value.toHttpUrlOrNull())
            sameOrigin(base, url) && (base.encodedPath == "/" ||
                url.encodedPath == base.encodedPath || url.encodedPath.startsWith(base.encodedPath + "/"))
        }
    }

    fun isLandingPage(page: String): Boolean {
        val base = baseForPage(page) ?: return false
        val url = requireNotNull(page.toHttpUrlOrNull())
        val prefix = requireNotNull(base.toHttpUrlOrNull()).encodedPath.trimEnd('/')
        val path = url.encodedPath.removePrefix(prefix)
        // 认证中转、登录和退出页面即使有匿名 Cookie 也不能保存成已登录入口。
        return path.startsWith("/xtgl/index_") || path.startsWith("/kbcx/") || path.startsWith("/cjcx/")
    }

    fun allowsGradeRequest(base: String, url: HttpUrl): Boolean {
        if (base !in bases) return false
        val parsed = requireNotNull(base.toHttpUrlOrNull())
        return sameOrigin(parsed, url) && url.username.isEmpty() && url.password.isEmpty() &&
            url.fragment == null && url.encodedPath in gradePaths.map { parsed.encodedPath.trimEnd('/') + it }
    }

    private fun sameOrigin(a: HttpUrl, b: HttpUrl) = a.scheme == b.scheme && a.host == b.host && a.port == b.port
}
