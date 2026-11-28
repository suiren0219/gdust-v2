package com.menglimi.kedian.learning

import com.menglimi.chaoxing.ChxApi
import com.menglimi.chaoxing.ChxCourseApi
import com.menglimi.chaoxing.ChxSso
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.net.URI
import java.util.Locale

/** 原生页面、WebView 导航/资源、传输层共用的边界；不启动任何外部 scheme。 */
internal object LearningUrls {
    const val LOGIN = "https://passport2.chaoxing.com/login?refer=https%3A%2F%2Fi.chaoxing.com"

    fun trusted(value: String?): String? {
        if (value.isNullOrBlank() || value.any { it.code <= 32 || it.code == 127 } || '\\' in value) return null
        val uri = try { URI(value) } catch (_: Exception) { return null }
        if (!uri.scheme.equals("https", ignoreCase = true) || uri.rawUserInfo != null ||
            uri.port !in setOf(-1, 443) || uri.normalize().rawPath != uri.rawPath) return null
        val host = uri.host?.lowercase(Locale.ROOT) ?: return null
        if (!ChxSso.isChaoxingHost(host)) return null
        val url = value.toHttpUrlOrNull() ?: return null
        return url.takeIf {
            it.scheme == "https" && it.host == host && it.port == 443 &&
                it.username.isEmpty() && it.password.isEmpty() &&
                it.encodedPath == uri.rawPath.orEmpty().ifEmpty { "/" }
        }?.toString()
    }

    fun allowedRequest(url: HttpUrl): Boolean = trusted(url.toString()) != null &&
        (ChxApi.isAllowedUrl(url) || ChxCourseApi.isAllowedUrl(url))
}
