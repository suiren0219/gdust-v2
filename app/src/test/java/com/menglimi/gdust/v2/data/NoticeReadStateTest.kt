package com.menglimi.gdust.v2.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NoticeReadStateTest {
    @Test
    fun readIdTracksUrlAndTime() {
        val a = Notice("标题", "https://portal.gdust.edu.cn/a", "通知", "教务处", 1000L)
        val sameUrlDifferentTime = a.copy(time = 2000L)
        val sameContentDifferentUrl = a.copy(url = "https://portal.gdust.edu.cn/b")
        assertEquals(a.readId, a.copy().readId)
        assertNotEquals(a.readId, sameUrlDifferentTime.readId)
        assertNotEquals(a.readId, sameContentDifferentUrl.readId)
    }

    @Test
    fun readIdFallsBackToMetadataWhenUrlMissing() {
        val a = Notice("考试安排", "", "教务通知", "教务处", 1000L)
        val sameMetadata = Notice("考试安排", "", "教务通知", "教务处", 1000L)
        val differentIssuer = a.copy(issuer = "考务中心")
        assertEquals(a.readId, sameMetadata.readId)
        assertNotEquals(a.readId, differentIssuer.readId)
    }

    @Test
    fun storageKeyIsPerAccountAndRejectsBlank() {
        val a = noticeReadStorageKey("2025001")
        val b = noticeReadStorageKey("2025002")
        assertNull(noticeReadStorageKey("   "))
        assertNotEquals(a, b)
        assertEquals(a, noticeReadStorageKey("2025001"))
    }
}
