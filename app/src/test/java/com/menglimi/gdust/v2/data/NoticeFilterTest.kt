package com.menglimi.gdust.v2.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NoticeFilterTest {
    @Test
    fun recognizesExamAnnouncementsFromTitleTypeAndIssuer() {
        assertTrue(Notice("期末考试安排", "", "通知", null, 0).matches(NoticeCategory.EXAM))
        assertTrue(Notice("关于考务工作的通知", "", "公告", null, 0).matches(NoticeCategory.EXAM))
        assertTrue(Notice("重要通知", "", "考试通知", null, 0).matches(NoticeCategory.EXAM))
        assertTrue(Notice("重要通知", "", "公告", "教务处", 0).matches(NoticeCategory.ACADEMIC))
    }

    @Test
    fun leavesUnrelatedAnnouncementsOutOfAcademicFilters() {
        val notice = Notice("宿舍停水通知", "", "后勤公告", "后勤处", 0)
        assertFalse(notice.matches(NoticeCategory.EXAM))
        assertFalse(notice.matches(NoticeCategory.ACADEMIC))
        assertTrue(notice.matches(NoticeCategory.ALL))
    }
}
