package com.menglimi.gdust.v2.data

import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.CookieJar
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resumeWithException

/** 教务处公开公告单独读取，不接收门户 TOKEN，也不共享登录客户端或 Cookie。 */
internal class SchoolNoticeApi internal constructor(private val calls: Call.Factory) {
    constructor() : this(
        OkHttpClient.Builder()
            .cookieJar(CookieJar.NO_COOKIES)
            .followRedirects(false)
            .followSslRedirects(false)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .callTimeout(30, TimeUnit.SECONDS)
            .build(),
    )

    suspend fun notices(): List<Notice> = suspendCancellableCoroutine { continuation ->
        val request = Request.Builder()
            .url(PortalNews.requestUrl(NoticeSource.ACADEMIC))
            .header("Accept", "application/json")
            .header("User-Agent", "GdustV2/SchoolNotices")
            .build()
        val call = calls.newCall(request)
        // 切换来源或离开页面时同时停止真实网络请求，避免仅丢弃响应却继续耗电。
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                continuation.resumeWithException(e)
            }

            override fun onResponse(call: Call, response: Response) {
                val result = try {
                    Result.success(response.use {
                        // 公开源若改为登录页或返回异常，不尝试登录，也不冒充门户会话过期。
                        if (!it.isSuccessful) throw SchoolNoticeUnavailable()
                        val body = it.body?.string() ?: throw SchoolNoticeUnavailable()
                        try {
                            PortalNews.parse(body, NoticeSource.ACADEMIC)
                        } catch (_: AuthFailed) {
                            throw SchoolNoticeUnavailable()
                        } catch (_: IllegalStateException) {
                            throw SchoolNoticeUnavailable()
                        }
                    })
                } catch (e: Exception) {
                    Result.failure(e)
                }
                continuation.resumeWith(result)
            }
        })
    }
}

internal class SchoolNoticeUnavailable : Exception("教务处公开公告暂时无法读取")
