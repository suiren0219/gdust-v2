package com.menglimi.gdust.v2.data

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl

/**
 * 可持久化的 Cookie 存储：登录一次后把 Cookie 落盘，后续后台同步直接复用。
 * 按 host 分组保存；过期 Cookie 自动丢弃；支持导出/回灌给 WebView。
 */
class PersistentCookieJar(context: Context) : CookieJar {

    private val prefs = context.applicationContext.getSharedPreferences("gdust_cookies", Context.MODE_PRIVATE)
    private val gson = Gson()
    private val store = mutableMapOf<String, MutableList<Cookie>>()

    init {
        load()
    }

    @Synchronized
    private fun load() {
        val raw = prefs.getString(KEY, null) ?: return
        runCatching {
            val type = object : TypeToken<Map<String, List<StoredCookie>>>() {}.type
            val saved: Map<String, List<StoredCookie>> = gson.fromJson(raw, type)
            val now = System.currentTimeMillis()
            saved.forEach { (host, list) ->
                val valid = list.filter { it.expiresAt > now }.mapNotNull { it.toCookie() }
                if (valid.isNotEmpty()) store[host] = valid.toMutableList()
            }
        }
    }

    @Synchronized
    fun persist() {
        val snapshot = store.mapValues { (_, list) ->
            list.filter { it.expiresAt > System.currentTimeMillis() }.map(StoredCookie::from)
        }.filterValues { it.isNotEmpty() }
        prefs.edit().putString(KEY, gson.toJson(snapshot)).apply()
    }

    @Synchronized
    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        val list = store.getOrPut(url.host) { mutableListOf() }
        cookies.forEach { incoming ->
            list.removeAll { it.name == incoming.name && it.path == incoming.path }
            if (incoming.expiresAt > System.currentTimeMillis()) list.add(incoming)
        }
        persist()
    }

    @Synchronized
    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val now = System.currentTimeMillis()
        val candidates = store[url.host].orEmpty().filter { it.expiresAt > now && it.matches(url) }
        return candidates.ifEmpty {
            // 兜底：父域 Cookie（如 .gdust.edu.cn）
            store.entries.filter { (host, _) -> url.host.endsWith(host) }.flatMap { it.value }
                .filter { it.expiresAt > now && it.matches(url) }
        }
    }

    @Synchronized
    fun hasSessionFor(host: String): Boolean =
        store.entries.filter { (domain, _) -> host == domain || host.endsWith(".$domain") }
            .any { (_, cookies) -> cookies.any { it.expiresAt > System.currentTimeMillis() } }

    @Synchronized
    fun cookiesFor(host: String): List<Cookie> =
        store[host].orEmpty().filter { it.expiresAt > System.currentTimeMillis() }

    @Synchronized
    fun clear() {
        store.clear()
        prefs.edit().remove(KEY).apply()
    }

    @Synchronized
    fun clearHost(host: String) {
        store.remove(host)
        persist()
    }

    /** 剩余有效期最短的那个（用来判断要不要续租）。 */
    @Synchronized
    fun minRemainingMillis(host: String): Long {
        val list = cookiesFor(host)
        if (list.isEmpty()) return 0
        return list.minOf { it.expiresAt - System.currentTimeMillis() }
    }

    private data class StoredCookie(
        val name: String,
        val value: String,
        val domain: String,
        val path: String,
        val expiresAt: Long,
        val secure: Boolean,
        val httpOnly: Boolean,
    ) {
        fun toCookie(): Cookie? = runCatching {
            Cookie.Builder()
                .name(name).value(value).domain(domain).path(path)
                .expiresAt(expiresAt)
                .apply { if (secure) secure() else { }; if (httpOnly) httpOnly() }
                .build()
        }.getOrNull()

        companion object {
            fun from(c: Cookie) = StoredCookie(
                c.name, c.value, c.domain, c.path, c.expiresAt, c.secure, c.httpOnly,
            )
        }
    }

    companion object {
        private const val KEY = "jar"
    }
}
