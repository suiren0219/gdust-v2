package com.menglimi.gdust.v2.data

import org.junit.Assert.assertEquals
import org.junit.Test

class NoticeNoiseFilterTest {
    @Test fun hidesClearPublicityAndMeetingReportsButKeepsUnknownTitles() {
        val titles = listOf("校长一行到学院调研", "学校召开年度工作部署会", "校园文艺晚会精彩回顾",
            "运动会开幕式圆满举行", "关于进一步推进相关工作的通知", "普通校园新闻")
        assertEquals(titles.takeLast(2), NoticeNoiseFilter.project(titles.map(::notice)).map { it.title })
    }

    @Test fun studentServicesAndSafetyProtectionTakePriorityOverNoisePatterns() {
        val titles = listOf("关于文艺晚会期间停电的通知", "校领导调研考试安排", "召开学费缴费工作会",
            "校园音乐节志愿者报名通知", "召开奖学金申请专题会", "校长走访毕业实习单位",
            "文艺晚会期间校园安全提醒", "教务处关于补考成绩复核的通知")
        assertEquals(titles, NoticeNoiseFilter.project(titles.map(::notice)).map { it.title })
    }

    @Test fun genericPortalListUrlsNeverCollapseDifferentAnnouncements() {
        val notices = listOf(notice("第一条通知"), notice("第二条通知"), notice("第三条通知"))
        assertEquals(notices, NoticeNoiseFilter.project(notices))
    }

    @Test fun sameDayNormalizedTitlesAndArticleUrlsDeduplicateButKeepSeparateReminders() {
        val first = notice("关于缴费的通知", 100_000_000L).copy(url = "https://school.example/info/1/123.htm#top")
        val retitled = first.copy(title = "缴费安排说明", url = "https://school.example/info/1/123.htm#content", time = first.time - 1)
        val repost = first.copy(title = " 关于 缴费的通知 ", url = "https://other.example/456.htm", time = first.time - 2)
        val reminder = first.copy(time = first.time - 86_400_000L, url = "https://school.example/info/1/789.htm")
        assertEquals(listOf(first, reminder), NoticeNoiseFilter.project(listOf(repost, reminder, retitled, first)))
    }

    @Test fun articleQueryIdentifiersAreNotDiscardedAndProjectionDoesNotModifyInput() {
        val first = notice("未知日期通知", 0L).copy(url = "https://school.example/article?id=1")
        val second = first.copy(title = "另一条通知", url = "https://school.example/article?id=2", time = 2L)
        val original = listOf(first, second)
        assertEquals(listOf(second, first), NoticeNoiseFilter.project(original))
        assertEquals(listOf(first, second), original)
    }

    @Test fun campusNoiseRulesNeverSuppressPortalOrAcademicAnnouncements() {
        val notices = listOf(notice("校长一行到学院调研"), notice("学校召开年度工作部署会"))
        listOf(NoticeSource.PORTAL, NoticeSource.ACADEMIC).forEach { source ->
            assertEquals(notices, NoticeNoiseFilter.project(notices, source))
        }
        assertEquals(emptyList<Notice>(), NoticeNoiseFilter.project(notices, NoticeSource.CAMPUS))
    }

    @Test fun deadlinesAppealsAndActionableEventsRemainVisible() {
        val titles = listOf("文艺晚会志愿者招募", "书画展作品征集", "关于召开学生座谈会的通知",
            "歌唱比赛评选结果公示", "音乐节申诉截止提醒", "学校召开申诉工作会", "校园摄影展")
        assertEquals(titles, NoticeNoiseFilter.project(titles.map(::notice), NoticeSource.CAMPUS).map { it.title })
    }

    private fun notice(title: String, time: Long = 1_000L) = Notice(
        title, "https://app.dingtalk.com/blackboard/h5/listNotice", "门户通知", null, time,
    )
}
