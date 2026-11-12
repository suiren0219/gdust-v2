package com.menglimi.gdust.v2.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * 学习通只读入口。
 *
 * 目前只做一件事：用 CAS 单点后拿到的 Cookie 探活。
 * 作业/考试清单的接口与字段尚无 HAR 证据，按红线不在这里凭空写端点——
 * 上一个版本的通知监听负责「新作业提醒」，全量清单等抓到作业页请求后再补。
 */
class ChxApi {

    private val ua =
        "Mozilla/5.0 (Linux; Android 13; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"

    private fun client() = OkHttpClient.Builder()
        .followRedirects(true)
        .followSslRedirects(true)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    /**
     * 会话是否仍然有效：拉首页，如果被踢回登录页或响应体是登录表单，判失效。
     * 网络故障一律按「未知」处理，由调用方保留 Cookie，不轻易登出。
     */
    suspend fun probe(cookie: String): Boolean? = withContext(Dispatchers.IO) {
        if (cookie.isBlank()) return@withContext false
        val req = Request.Builder()
            .url(ChxSso.HOME)
            .header("User-Agent", ua)
            .header("Cookie", cookie)
            .get()
            .build()
        runCatching {
            client().newCall(req).execute().use { resp ->
                val finalUrl = resp.request.url.toString().lowercase()
                // 被重定向到 passport/login 一律视为失效
                if (finalUrl.contains("passport") || finalUrl.contains("login")) return@use false
                if (!resp.isSuccessful) return@use null
                val body = resp.body?.string().orEmpty()
                when {
                    body.isBlank() -> null
                    // 未登录时首页会渲染成登录引导页
                    body.contains("用户登录") || body.contains("扫码登录") -> false
                    else -> true
                }
            }
        }.getOrNull()
    }
}
