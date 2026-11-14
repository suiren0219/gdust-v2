package com.menglimi.gdust.v2.data

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/** 钉钉扫码 ticket 换门户 TOKEN 的参数编码回归。 */
class SsoTicketEncodingTest {

    private fun portalLoginUrl(rawTicket: String): String = HttpUrl.Builder()
        .scheme("https").host("portal.gdust.edu.cn")
        .addPathSegments("smart-admin-api/user/login")
        .addQueryParameter("loginCode", rawTicket.trim())
        .addQueryParameter("appId", "portalRemote")
        .build().toString()

    @Test
    fun base64LikeTicketSurvivesUrlEncoding() {
        val ticket = "GKCASa+b/c=d&e"
        val url = portalLoginUrl(ticket).toHttpUrl()
        assertEquals(ticket, url.queryParameter("loginCode"))
        assertEquals("portalRemote", url.queryParameter("appId"))
    }

    @Test
    fun rawConcatenationWouldCorruptTicketButBuilderDoesNot() {
        val ticket = "GKCAS+x=1"
        val raw = "https://portal.gdust.edu.cn/smart-admin-api/user/login?loginCode=$ticket&appId=portalRemote"
        // 裸拼时 + 会被解析成空格（服务端拿到错误票据）
        assertEquals("GKCAS x=1", raw.toHttpUrl().queryParameter("loginCode"))
        // HttpUrl 版本保留原值
        assertEquals(ticket, portalLoginUrl(ticket).toHttpUrl().queryParameter("loginCode"))
    }

    @Test
    fun whitespaceAroundSseTicketIsTrimmed() {
        val withNoise = "  GKCAS123456\n "
        assertEquals("GKCAS123456", withNoise.trim())
        assertEquals(
            portalLoginUrl("GKCAS123456").toHttpUrl().queryParameter("loginCode"),
            portalLoginUrl(" GKCAS123456 ").toHttpUrl().queryParameter("loginCode"),
        )
    }

    @Test
    fun distinctTicketsProduceDifferentUrls() {
        assertNotEquals(portalLoginUrl("ticket-A"), portalLoginUrl("ticket-B"))
    }
}
