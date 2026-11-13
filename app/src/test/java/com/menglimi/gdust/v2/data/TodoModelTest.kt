package com.menglimi.gdust.v2.data

import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TodoModelTest {
    private val gson = Gson()
    private val day = 24L * 3600 * 1000
    private val now = 1763000000000L // 固定时刻，避免时区/当前时间干扰

    @Test
    fun legacyJsonDeserializesWithDefaults() {
        val items = parseTodos("""[{"id":1,"text":"旧待办","done":false}]""", gson)
        assertEquals(1, items.size)
        assertEquals(TodoKind.GENERAL, items[0].kind)
        assertEquals("", items[0].course)
        assertEquals(0L, items[0].dueAt)
        assertEquals("manual", items[0].source)
    }

    @Test
    fun structuredJsonRoundTrips() {
        val item = Todo(2, "期末考试", false, TodoKind.EXAM, "高等数学", now + day, "chaoxing")
        val json = gson.toJson(listOf(item))
        val back = parseTodos(json, gson).single()
        assertEquals(item, back)
    }

    @Test
    fun groupsByTimeAndCompletion() {
        // 所有 dueAt 都是「相对 now」的绝对毫秒；分组按本地日历日，用整日偏移避免边界歧义
        val items = listOf(
            Todo(1, "逾期作业", false, TodoKind.HOMEWORK, "英语", now - 2 * day),
            Todo(2, "今天考试", false, TodoKind.EXAM, "数学", now + 60_000L),
            Todo(3, "本周任务", false, TodoKind.HOMEWORK, "英语", now + 3 * day),
            Todo(4, "很久以后", false, TodoKind.GENERAL, "", now + 30 * day),
            Todo(5, "没设时间", false),
            Todo(6, "已完成", true, TodoKind.HOMEWORK, "英语", now + day),
        )
        val g = groupTodos(items, now)
        assertEquals(5, g.pendingCount)
        assertEquals(1, g.completed.size)
        assertEquals(listOf("逾期作业"), g.overdue.map { it.text })
        assertEquals(listOf("今天考试"), g.today.map { it.text })
        assertEquals(listOf("本周任务"), g.thisWeek.map { it.text })
        assertEquals(listOf("很久以后"), g.later.map { it.text })
        assertEquals(listOf("没设时间"), g.noDue.map { it.text })
    }

    @Test
    fun examAndHomeworkClassification() {
        assertEquals(TodoKind.EXAM, classifyNotificationTodo("[学习通] 高等数学 · 发布了新考试：期末测验").first)
        assertEquals(TodoKind.HOMEWORK, classifyNotificationTodo("[学习通] 大学英语 · 新作业待提交").first)
        assertEquals(TodoKind.GENERAL, classifyNotificationTodo("买牛奶").first)
    }

    @Test
    fun courseNameIsExtractedWhenPresent() {
        val (kind, course) = classifyNotificationTodo("[学习通] 高等数学 · 新作业待提交")
        assertEquals(TodoKind.HOMEWORK, kind)
        assertTrue(course.isNotBlank())
    }
}
