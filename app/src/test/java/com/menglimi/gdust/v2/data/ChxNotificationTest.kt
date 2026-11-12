package com.menglimi.gdust.v2.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChxNotificationTest {
    @Test fun recognizesExplicitNewWork() {
        assertEquals("[学习通] 高数 · 发布了新作业：第3章，周五截止", ChxNotification.todo("高数", "发布了新作业：第3章，周五截止"))
        assertEquals("[学习通] 考试提醒 · 测验即将开始", ChxNotification.todo("考试提醒", "测验即将开始"))
    }

    @Test fun ignoresCompletedAndUnrelatedMessages() {
        assertNull(ChxNotification.todo("高数作业", "已提交，等待批阅"))
        assertNull(ChxNotification.todo("课程通知", "明日调课"))
        assertNull(ChxNotification.todo("学习通", "您的成绩已发布，请查看考试结果"))
        assertNull(ChxNotification.todo("新消息", "同学发来了消息"))
    }

    @Test fun avoidsCopyingLargeNotificationBodies() {
        assertNull(ChxNotification.todo("作业截止", "待提交".repeat(200)))
        assertNull(ChxNotification.todo(null, null))
    }
}
