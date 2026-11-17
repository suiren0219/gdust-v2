package com.menglimi.chaoxing

import okhttp3.HttpUrl
import java.net.URI
import java.util.Locale

/** 仅用于失败提示：不保留 Cookie、查询参数、片段或路径中的账号/签名值。 */
object ChxReadDiagnostics {
    private val knownHosts = setOf(
        "mooc1.chaoxing.com", "mooc2-ans.chaoxing.com", "passport.chaoxing.com",
        "passport2.chaoxing.com",
    )
    private val staticSegments = setOf(
        "visit", "stucoursemiddle", "mooc-ans", "mooc2-ans", "mycourse", "stu", "stu.html",
        "mooc2", "work", "list", "task", "login", "login.html", "mlogin", "fanyalogin",
        "error", "error.html", "auth", "index", "index.html",
    )

    /** 计数只接收客户端生成的固定原因，最多展示三类，避免首个失败掩盖其他卡点。 */
    fun failures(counts: Map<String, Int>): String = counts.entries.sortedByDescending { it.value }.take(3)
        .joinToString("\n") { "${it.value} 门：${it.key}" }

    fun route(base: HttpUrl, target: String?): String {
        if (target.isNullOrBlank()) return "无跳转地址"
        val uri = runCatching { URI(base.toString()).resolve(URI(target)) }.getOrNull()
            ?: return "地址格式无效"
        val host = uri.host?.lowercase(Locale.ROOT) ?: return "地址格式无效"
        if (host !in knownHosts) return if (ChxSso.isChaoxingHost(host)) "其他超星站点" else "其他站点"
        val scheme = uri.scheme?.lowercase(Locale.ROOT)?.takeIf { it in setOf("http", "https") } ?: return "非网页地址"
        val path = uri.rawPath.orEmpty().split('/').filter { it.isNotEmpty() }.take(8).joinToString("/") { segment ->
            segment.lowercase(Locale.ROOT).takeIf { it in staticSegments } ?: "…"
        }
        return "$scheme://$host/$path"
    }
}
