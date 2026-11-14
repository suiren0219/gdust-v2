package com.menglimi.gdust.v2.data

import android.webkit.CookieManager
import android.webkit.WebStorage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import kotlin.coroutines.resume

/**
 * 与教务 WebView 共用应用 CookieManager，但仅清理已知超星/CAS 范围。
 * Android 无法枚举全部 Cookie 的 Domain/Path；true 也不表示所有站点或服务器会话都已退出。
 */
object ChxWebSession {
    private val clearMutex = Mutex()
    private val visited = linkedSetOf<String>()
    private val knownHosts = listOf(
        "chaoxing.com", "chaoxing.cn", "passport.chaoxing.com", "passport2.chaoxing.com",
        "passport.chaoxing.cn", "passport2.chaoxing.cn", "i.chaoxing.com", "i.chaoxing.cn",
        "mooc2-ans.chaoxing.com", "mooc1.chaoxing.com", "www.fanya.chaoxing.com",
        "gdkjxy.fanya.chaoxing.com", "gdkjxy.fysso.chaoxing.com", "gdkj.mh.chaoxing.com",
        "cas.gdust.edu.cn",
    )
    private val chxPaths = listOf("/", "/mooc2-ans", "/mooc2-ans/", "/mooc2-ans/visit", "/mooc2-ans/visit/",
        "/mooc2-ans/visit/interaction", "/sso", "/sso/", "/base", "/login", "/fanyalogin")
    private val casPaths = listOf("/", "/cas", "/cas/", "/cas/login", "/cas/logout",
        "/lyuapServer", "/lyuapServer/", "/lyuapServer/login", "/lyuapServer/logout")

    /** 仅记主机与路径以补充本次浏览范围，不保留 query、fragment 或 Cookie 值。在 Main 调用。 */
    fun recordVisited(url: String) {
        val parsed = url.toHttpUrlOrNull() ?: return
        if (!isSessionHost(parsed.host)) return
        visited += parsed.newBuilder().query(null).fragment(null).username("").password("").build().toString()
    }

    /** 等待 Cookie 写入回调、flush 与已知范围复查；取消由调用方处理，失败可重试。 */
    suspend fun clear(): Boolean = withContext(Dispatchers.Main.immediate) {
        clearMutex.withLock {
            try {
                withTimeoutOrNull(20_000) { clearKnownSession() } ?: false
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                false
            }
        }
    }

    private suspend fun clearKnownSession(): Boolean {
        val cookies = CookieManager.getInstance()
        val storage = WebStorage.getInstance()
        // WebStorage 的 origin 列表还能补上曾访问但本次进程尚未记录的超星主机。
        val origins = storage.origins() ?: return false
        val urls = (knownHosts.map { "https://$it/" } + visited + origins.filter(::isSessionOrigin))
            .flatMap(::probeUrls).distinct()
        var accepted = true
        urls.groupBy { it.toHttpUrlOrNull()!!.host }.forEach { (host, probes) ->
            val names = probes.flatMap { url -> ChxSso.cookiePairs(cookies.getCookie(url)).map { it.first } }.toSet()
            val paths = probes.map { it.toHttpUrlOrNull()!!.encodedPath }.toSet()
            for (name in names) {
                for (domain in cookieDomains(host, name)) {
                    for (path in paths) {
                        if (name.startsWith("__Host-") && (domain != null || path != "/")) continue
                        val expiry = buildString {
                            append(name).append("=; Max-Age=0; Expires=Thu, 01 Jan 1970 00:00:00 GMT; Path=").append(path)
                            if (domain != null) append("; Domain=").append(domain)
                            append("; Secure; HttpOnly")
                        }
                        if (!cookies.expire("https://$host/", expiry)) accepted = false
                    }
                }
            }
        }
        cookies.flush()
        // 不使用 removeAllCookies/deleteAllData，保留教务站点自己的 Cookie 和存储。
        origins.filter(::isSessionOrigin).forEach(storage::deleteOrigin)
        val remainingOrigins = storage.origins() ?: return false
        val cookiesGone = urls.all { ChxSso.cookiePairs(cookies.getCookie(it)).isEmpty() }
        return accepted && cookiesGone && remainingOrigins.none(::isSessionOrigin)
    }

    internal fun isSessionOrigin(origin: String): Boolean =
        origin.toHttpUrlOrNull()?.host?.let(::isSessionHost) == true

    private fun isSessionHost(host: String): Boolean = ChxSso.isChaoxingHost(host) || host == "cas.gdust.edu.cn"

    /** 包含 host-only、host Domain 和每级超星父域；学校父域只删除明确 CAS 票据名。 */
    internal fun cookieDomains(host: String, name: String): List<String?> {
        if (!isSessionHost(host)) return emptyList()
        if (name.startsWith("__Host-")) return listOf(null)
        if (host == "cas.gdust.edu.cn") {
            return listOf(null, host) + if (name in ChxSso.casTicketNames) listOf("gdust.edu.cn") else emptyList()
        }
        val domains = mutableListOf<String?>(null)
        var domain = host
        while (ChxSso.isChaoxingHost(domain)) {
            domains += domain
            domain = domain.substringAfter('.', "")
        }
        return domains
    }

    internal fun probeUrls(url: String): List<String> {
        val parsed = url.toHttpUrlOrNull()?.takeIf { isSessionHost(it.host) } ?: return emptyList()
        val paths = (if (parsed.host == "cas.gdust.edu.cn") casPaths else chxPaths).toMutableSet()
        var path = parsed.encodedPath
        while (path.isNotEmpty()) {
            if (';' !in path) {
                paths += path
                if (!path.endsWith('/')) paths += "$path/"
            }
            path = path.trimEnd('/').substringBeforeLast('/', "")
        }
        return paths.flatMap { p -> listOf("https://${parsed.host}$p", "http://${parsed.host}$p") }
    }

    private suspend fun CookieManager.expire(url: String, value: String): Boolean =
        suspendCancellableCoroutine { continuation ->
            setCookie(url, value) { accepted ->
                if (continuation.isActive) continuation.resume(accepted == true)
            }
        }

    private suspend fun WebStorage.origins(): Set<String>? = suspendCancellableCoroutine { continuation ->
        getOrigins { entries ->
            if (continuation.isActive) continuation.resume(entries?.keys?.filterIsInstance<String>()?.toSet())
        }
    }
}
