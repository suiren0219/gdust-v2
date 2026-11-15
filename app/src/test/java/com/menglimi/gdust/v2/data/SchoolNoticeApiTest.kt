package com.menglimi.gdust.v2.data

import com.google.gson.JsonParser
import java.time.Instant
import java.util.TimeZone
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Timeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class SchoolNoticeApiTest {
    @Test
    fun realResponseShapeUsesJiaowuDirectoryAndPublicationDateIndependently() {
        val originalZone = TimeZone.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("America/Los_Angeles"))
            val notices = PortalNews.parse(fixture(), NoticeSource.ACADEMIC)
            assertEquals(2, notices.size)
            assertEquals("示例教务处课程申报通知", notices[0].title)
            assertEquals("https://www.gdust.edu.cn/jwc/jiaowu/20310304/92001.html", notices[0].url)
            assertEquals(Instant.parse("2031-03-04T04:34:56Z").toEpochMilli(), notices[0].time)
            // 实测公告会稍后发布到新目录，不能从 thedate 猜原文路径或反过来猜发布时间。
            assertEquals("https://www.gdust.edu.cn/jwc/jiaowu/20310306/92002.html", notices[1].url)
            assertEquals(Instant.parse("2031-03-01T16:00:00Z").toEpochMilli(), notices[1].time)
            assertTrue(notices.all { it.type == "教务处公告" && it.issuer == "教务处" })
        } finally {
            TimeZone.setDefault(originalZone)
        }
    }

    @Test
    fun academicPathsRemainSourceScopedAndCannotBeReplacedByArbitraryUrls() {
        val body = JsonParser.parseString(fixture()).asJsonObject
        val row = body.getAsJsonArray("data")[0].asJsonObject
        row.addProperty("url", "https://untrusted.invalid/redirect")
        assertEquals("https://www.gdust.edu.cn/jwc/jiaowu/20310304/92001.html",
            PortalNews.parse(body.toString(), NoticeSource.ACADEMIC)[0].url)
        listOf(
            "article/101", "../jiaowu/20310304", "jiaowu/20310304/../20310305",
            "jiaowu/%32%30%33%31%30%33%30%34", "jiaowu/20310304?next=1", "jiaowu/２０３１０３０４",
            "https://untrusted.invalid/jiaowu/20310304", "/jiaowu/20310304", "jiaowu/20310304/", "jiaowu/2031034",
        ).forEach { directory ->
            row.addProperty("staticDir", directory)
            assertThrows(IllegalStateException::class.java) {
                PortalNews.parse(body.toString(), NoticeSource.ACADEMIC)
            }
        }
        assertThrows(IllegalStateException::class.java) { PortalNews.parse(fixture(), NoticeSource.CAMPUS) }
    }

    @Test
    fun publicLoaderSendsOneCredentialFreeRequestAndParsesTheResponse() = runBlocking {
        val calls = FakeCalls(fixture())
        val notices = SchoolNoticeApi(calls).notices()
        assertEquals(2, notices.size)
        val request = calls.created.single().request()
        assertEquals("https://portal.gdust.edu.cn/smart-admin-api/app/news/list?source=jiaowu", request.url.toString())
        assertEquals("GET", request.method)
        assertNull(request.body)
        assertNull(request.header("TOKEN"))
        assertNull(request.header("Authorization"))
        assertNull(request.header("Cookie"))
    }

    @Test
    fun publicHttpAndParseFailuresNeverBecomeEmptySuccessOrPortalSessionExpiry() {
        listOf(
            503 to "synthetic-private-response",
            302 to "redirect-to-login",
            200 to "<html><title>登录</title></html>",
            200 to """{"code":117,"success":false,"data":[]}""",
            200 to """{"code":1,"success":true}""",
        ).forEach { (status, body) ->
            val calls = FakeCalls(body, status)
            val failure = assertThrows(SchoolNoticeUnavailable::class.java) {
                runBlocking { SchoolNoticeApi(calls).notices() }
            }
            assertEquals("教务处公开公告暂时无法读取", failure.message)
            assertEquals(1, calls.created.size)
        }
        val empty = FakeCalls("""{"code":1,"success":true,"data":[]}""")
        assertTrue(runBlocking { SchoolNoticeApi(empty).notices() }.isEmpty())
    }

    @Test
    fun cancellingRefreshCancelsTheHttpCallAndDiscardsLateResponse() {
        val calls = FakeCalls(fixture(), immediately = false)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        var published = false
        try {
            val job = scope.launch { SchoolNoticeApi(calls).notices(); published = true }
            val call = calls.created.single()
            assertFalse(call.isCanceled())
            job.cancel()
            assertTrue(call.isCanceled())
            call.respond()
            assertFalse(published)
        } finally {
            scope.cancel()
        }
    }

    // 2026-11-15 无凭据 GET news/list?source=jiaowu；保留真实键/类型/路径结构，替换标题、日期和标识。
    private fun fixture(): String = checkNotNull(javaClass.getResource("/notices/academic-news.json"))
        .readText(Charsets.UTF_8)

    private class FakeCalls(
        private val body: String,
        private val status: Int = 200,
        private val immediately: Boolean = true,
    ) : Call.Factory {
        val created = mutableListOf<FakeCall>()
        override fun newCall(request: Request): Call = FakeCall(request, body, status, immediately).also(created::add)
    }

    private class FakeCall(
        private val original: Request,
        private val body: String,
        private val status: Int,
        private val immediately: Boolean,
    ) : Call {
        private var executed = false
        private var cancelled = false
        private lateinit var callback: Callback
        override fun request() = original
        override fun isExecuted() = executed
        override fun isCanceled() = cancelled
        override fun timeout() = Timeout.NONE
        override fun clone(): Call = FakeCall(original, body, status, immediately)
        override fun execute(): Response = error("Use the cancellable async request")
        override fun enqueue(responseCallback: Callback) {
            executed = true
            callback = responseCallback
            if (immediately) respond()
        }
        override fun cancel() { cancelled = true }
        fun respond() = callback.onResponse(this, Response.Builder()
            .request(original).protocol(Protocol.HTTP_1_1).code(status).message("synthetic")
            .body(body.toResponseBody("application/json".toMediaType())).build())
    }
}
