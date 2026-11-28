package com.haooz.chedule.data.gdust

import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/** 广科三客户端共享的 OkHttpClient。 */
object GdustHttp {
    val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build()
    }
}
