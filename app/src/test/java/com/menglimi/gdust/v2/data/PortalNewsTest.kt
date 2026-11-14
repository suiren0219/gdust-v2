package com.menglimi.gdust.v2.data

import com.google.gson.JsonNull
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.util.TimeZone

class PortalNewsTest {
    @Test
    fun codeOneNewsMapsToExistingNoticeAndUsesOnlyFixedArticleUrl() {
        val row = newsRow().apply { addProperty("url", "https://untrusted.invalid/redirect") }
        val notice = PortalNews.parse(envelope(row.toString()), NoticeSource.CAMPUS).single()
        assertEquals("虚构校园活动通知", notice.title)
        assertEquals("https://www.gdust.edu.cn/article/101/202.html", notice.url)
        assertEquals("官网新闻", notice.type)
        assertEquals("学校官网", notice.issuer)
        assertEquals(Instant.parse("2031-03-04T04:34:56.100Z").toEpochMilli(), notice.time)
    }

    @Test
    fun academicNewsUsesConfiguredChannelAndAcademicPrefix() {
        val notice = PortalNews.parse(envelope(newsRow().toString()), NoticeSource.ACADEMIC).single()
        assertEquals("https://www.gdust.edu.cn/jwc/article/101/202.html", notice.url)
        assertEquals("教务处公告", notice.type)
        assertEquals("教务处", notice.issuer)
        assertEquals("https://portal.gdust.edu.cn/smart-admin-api/app/news/list?source=jiaowu", PortalNews.requestUrl(NoticeSource.ACADEMIC))
        assertEquals("https://portal.gdust.edu.cn/smart-admin-api/app/news/list?source=home", PortalNews.requestUrl(NoticeSource.CAMPUS))
        assertThrows(IllegalStateException::class.java) { PortalNews.requestUrl(NoticeSource.PORTAL) }
        assertThrows(IllegalStateException::class.java) { PortalNews.parse(envelope(""), NoticeSource.PORTAL) }
    }

    @Test
    fun onlyValidEmptyArraysMeanNoNewsOrNotices() {
        assertTrue(PortalNews.parse(envelope(""), NoticeSource.CAMPUS).isEmpty())
        assertTrue(parsePortalNotices(envelope("")).isEmpty())
        assertTrue(parsePortalNotices("""{"code":0,"success":true,"data":[]}""").isEmpty())
        val invalid = listOf(
            "null", "[]", "not-json", "{}",
            """{"code":1,"success":true}""",
            """{"code":1,"success":true,"data":null}""",
            """{"code":1,"success":true,"data":{}}""",
            """{"code":1,"success":true,"data":"[]"}""",
            """{"code":1,"success":false,"data":[]}""",
            """{"code":1,"success":"true","data":[]}""",
            """{"code":1,"data":[]}""",
            """{"success":true,"data":[]}""",
            """{"code":"1","success":true,"data":[]}""",
            """{"code":1.5,"success":true,"data":[]}""",
            """{"code":500,"success":true,"data":[]}""",
        )
        invalid.forEach { body ->
            assertThrows(IllegalStateException::class.java) { PortalNews.parse(body, NoticeSource.CAMPUS) }
            assertThrows(IllegalStateException::class.java) { parsePortalNotices(body) }
        }
        assertThrows(IllegalStateException::class.java) {
            PortalNews.parse("""{"code":0,"success":true,"data":[]}""", NoticeSource.CAMPUS)
        }
    }

    @Test
    fun malformedItemsFailTheWholeResponseInsteadOfReturningPartialNews() {
        listOf("null", "[]", "123", "true", "\"text\"", "{}").forEach { invalid ->
            assertThrows(IllegalStateException::class.java) {
                PortalNews.parse(envelope("${newsRow()},$invalid"), NoticeSource.CAMPUS)
            }
            assertThrows(IllegalStateException::class.java) {
                parsePortalNotices(envelope("${portalRow()},$invalid"))
            }
        }
        listOf("id", "staticDir", "title").forEach { field ->
            val missing = newsRow().apply { remove(field) }
            val wrongType = newsRow().apply { addProperty(field, 123) }
            listOf(missing, wrongType).forEach { row ->
                assertThrows(IllegalStateException::class.java) {
                    PortalNews.parse(envelope(row.toString()), NoticeSource.CAMPUS)
                }
            }
        }
        listOf("title", "url", "announcementType", "issuer").forEach { field ->
            val row = portalRow().apply { add(field, JsonObject()) }
            assertThrows(IllegalStateException::class.java) { parsePortalNotices(envelope(row.toString())) }
        }
        val blankTitle = newsRow().apply { addProperty("title", "  ") }
        assertThrows(IllegalStateException::class.java) {
            PortalNews.parse(envelope(blankTitle.toString()), NoticeSource.CAMPUS)
        }
    }

    @Test
    fun newsDatesUseShanghaiEvenWhenDeviceTimezoneDiffers() {
        val originalZone = TimeZone.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("America/Los_Angeles"))
            val row = newsRow().apply { addProperty("thedate", "2032-02-29 00:15:00.123456789") }
            val notice = PortalNews.parse(envelope(row.toString()), NoticeSource.CAMPUS).single()
            assertEquals(Instant.parse("2032-02-28T16:15:00.123Z").toEpochMilli(), notice.time)
        } finally {
            TimeZone.setDefault(originalZone)
        }
    }

    @Test
    fun absentDatesUseUnknownButInvalidDatesAndTypesFail() {
        listOf(
            newsRow().apply { remove("thedate") },
            newsRow().apply { add("thedate", JsonNull.INSTANCE) },
            newsRow().apply { addProperty("thedate", "") },
        ).forEach { row ->
            assertEquals(0L, PortalNews.parse(envelope(row.toString()), NoticeSource.CAMPUS).single().time)
        }
        listOf("2031-02-29 12:00:00.0", "2031-03-04 24:00:00.0", "2031-03-04", "2031-03-04 12:34:56", "invalid").forEach { date ->
            val row = newsRow().apply { addProperty("thedate", date) }
            assertThrows(IllegalStateException::class.java) { PortalNews.parse(envelope(row.toString()), NoticeSource.CAMPUS) }
        }
        val wrongType = newsRow().apply { addProperty("thedate", 123) }
        assertThrows(IllegalStateException::class.java) { PortalNews.parse(envelope(wrongType.toString()), NoticeSource.CAMPUS) }
        listOf("\"123\"", "1.25", "-1", "9223372036854775808", "{}").forEach { value ->
            val row = portalRow().apply { add("gmtCreate", JsonParser.parseString(value)) }
            assertThrows(IllegalStateException::class.java) { parsePortalNotices(envelope(row.toString())) }
        }
        val missing = portalRow().apply { remove("gmtCreate") }
        assertEquals(0L, parsePortalNotices(envelope(missing.toString())).single().time)
    }

    @Test
    fun rejectsTraversalEncodedSegmentsAndAbsoluteArticlePaths() {
        val badDirectories = listOf(
            "../article/101", "article/101/../202", "article/%2e%2e", "article/101%2f202",
            "/article/101", "//untrusted.invalid/article/101", "https://untrusted.invalid/article/101",
            "article\\101", "article/101?next=202", "article/101#fragment", "article/１０１", "article/101/", " article/101",
        )
        badDirectories.forEach { path ->
            val row = newsRow().apply { addProperty("staticDir", path) }
            assertThrows(IllegalStateException::class.java) { PortalNews.parse(envelope(row.toString()), NoticeSource.CAMPUS) }
        }
        listOf("../202", "%32%30%32", "202.html", "202/303", "https://untrusted.invalid/202", "202?x=1", "２０２", "", "202\n").forEach { id ->
            val row = newsRow().apply { addProperty("id", id) }
            assertThrows(IllegalStateException::class.java) { PortalNews.parse(envelope(row.toString()), NoticeSource.ACADEMIC) }
        }
    }

    @Test
    fun portalPreservesDifferentTitlesSharingTheGenericDingTalkLink() {
        val first = portalRow()
        val second = portalRow().apply { addProperty("title", "另一条虚构门户通知") }
        val third = portalRow().apply { addProperty("title", "不同时间的虚构通知"); addProperty("gmtCreate", 2_000L) }
        val notices = parsePortalNotices(envelope("$first,$second,$third"))
        assertEquals(3, notices.size)
        assertEquals(1, notices.map { it.url }.distinct().size)
        assertEquals(listOf("虚构门户通知", "另一条虚构门户通知", "不同时间的虚构通知"), notices.map { it.title })
        assertEquals("门户通知", notices.first().type)
        assertEquals("示例发布部门", notices.first().issuer)
    }

    @Test
    fun expiredSessionsUseExistingAuthFailurePolicy() {
        listOf(116, 117).forEach { code ->
            val body = """{"code":$code,"success":false,"data":[]}"""
            assertEquals(code, assertThrows(AuthFailed::class.java) { PortalNews.parse(body, NoticeSource.CAMPUS) }.code)
            assertEquals(code, assertThrows(AuthFailed::class.java) { parsePortalNotices(body) }.code)
        }
        assertEquals(401, schoolAuthFailure(401, "")?.code)
    }

    @Test
    fun browserUrlsRequireHttpHostAndNoUserInfo() {
        listOf(
            "https://www.gdust.edu.cn/article/101/202.html",
            "http://www.gdust.edu.cn/article/101/202.html?mode=plain#content",
            "https://app.dingtalk.com/blackboard/h5/listNotice",
        ).forEach { url -> assertEquals(url, noticeWebUrl(url)) }
        listOf(
            "", "   ", "javascript:alert(1)", "file:///tmp/page.html", "intent://page", "data:text/html,test",
            "/article/101/202.html", "//www.gdust.edu.cn/article/101/202.html", "https:///missing-host",
            "https://www.gdust.edu.cn@untrusted.invalid/page", "https://user:pass@www.gdust.edu.cn/page",
            "https://www.gdust.edu.cn:99999/page", "https://www.gdust.edu.cn\\@untrusted.invalid/page",
            " https://www.gdust.edu.cn/", "https://www.gdust.edu.cn/page\n",
        ).forEach { url -> assertNull(noticeWebUrl(url)) }
    }

    private fun newsRow() = JsonObject().apply {
        addProperty("id", "202")
        addProperty("title", "虚构校园活动通知")
        addProperty("thedate", "2031-03-04 12:34:56.1")
        addProperty("staticDir", "article/101")
    }

    private fun portalRow() = JsonObject().apply {
        addProperty("title", "虚构门户通知")
        addProperty("url", "https://app.dingtalk.com/blackboard/h5/listNotice")
        addProperty("announcementType", "门户通知")
        addProperty("issuer", "示例发布部门")
        addProperty("gmtCreate", 1_000L)
    }

    private fun envelope(items: String) = """{"code":1,"success":true,"data":[$items]}"""
}
