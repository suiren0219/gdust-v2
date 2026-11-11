package com.menglimi.gdust.v2.data

internal const val VPN_TEACHING_ENDPOINT = "https://webvpn.gdust.edu.cn/http/77726476706e69737468656265737421a1a013d2766626022b5cc7fd/kbcx/xskbcx_cxXsgrkb.html?vpn-12-o1-172.16.254.1&gnmkdm=N2151"
internal const val TEACHING_ENDPOINT = "http://172.16.254.1/kbcx/xskbcx_cxXsgrkb.html?gnmkdm=N2151"
internal const val PUBLIC_TEACHING_ENDPOINT = "http://gkjx.gdust.edu.cn/kbcx/xskbcx_cxXsgrkb.html?gnmkdm=N2151"

internal fun isTeachingEndpoint(url: String): Boolean = url in setOf(
    VPN_TEACHING_ENDPOINT, TEACHING_ENDPOINT, PUBLIC_TEACHING_ENDPOINT,
    PUBLIC_TEACHING_ENDPOINT.replace("http://", "https://"),
)

data class TeachingSession(val endpoint: String, val cookie: String, val pageUrl: String)
