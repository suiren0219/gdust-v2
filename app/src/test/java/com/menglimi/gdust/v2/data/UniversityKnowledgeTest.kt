package com.menglimi.gdust.v2.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class UniversityKnowledgeTest {
    @Test
    fun contextContainsCurrentTermCoursesAndOpenTasksWithoutCredentials() {
        val now = LocalDate.of(2026, 11, 24).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val calendar = CalendarInfo(12, 18, "2026-09-01", "2026-2027", "1")
        val blocks = listOf(
            CourseBlock(13, 1, 1, 2, "算法设计", "A101", "教师甲", "2026-11-24"),
            CourseBlock(12, 2, 3, 3, "旧周课程", "B202", "教师乙", "2026-11-17"),
        )
        val todos = listOf(
            Todo(1, "完成实验报告", false, TodoKind.HOMEWORK, "数据结构", now + 86_400_000, "chaoxing"),
            Todo(2, "已完成任务", true, TodoKind.GENERAL),
        )

        val context = buildStudentContext(calendar, blocks, todos, now)

        assertTrue(context.contains("2026-2027 1"))
        assertTrue(context.contains("算法设计"))
        assertTrue(context.contains("完成实验报告"))
        assertTrue(context.contains("数据结构"))
        assertFalse(context.contains("旧周课程"))
        assertFalse(context.contains("已完成任务"))
        assertFalse(context.contains("password"))
    }

    @Test
    fun longExternalFieldsKeepDeadlineAndSourceOnOneLine() {
        val date = LocalDate.of(2026, 11, 24)
        val zone = ZoneId.systemDefault()
        val now = date.atStartOfDay(zone).toInstant().toEpochMilli()
        val due = date.plusDays(1).atTime(18, 30).atZone(zone).toInstant().toEpochMilli()
        val calendar = CalendarInfo(
            13, 18, "2026-09-01", "2026-2027\r\n" + "学".repeat(9000), "1\t" + "期".repeat(9000),
        )
        val blocks = listOf(
            CourseBlock(
                13, 1, 1, 2, "课程\r\n标题\t" + "课".repeat(9000),
                "教室\u2029位置\t" + "室".repeat(9000), "教师", "2026-11-24",
            ),
        )
        val todos = listOf(
            Todo(
                1, "报告\r\n标题\t" + "写".repeat(9000), false, TodoKind.HOMEWORK,
                "课程\u2028名称\u0085" + "课".repeat(9000), due, "chaoxing",
            ),
        )

        val context = buildStudentContext(calendar, blocks, todos, now)
        val todoLine = context.lineSequence().single { it.startsWith("- [HOMEWORK]") }
        val courseLine = context.lineSequence().single { it.startsWith("- 周一") }

        assertTrue(context.length <= 8000)
        assertTrue(context.contains("当前时间："))
        assertTrue(context.contains("学期：2026-2027 学"))
        assertTrue(context.contains("学期起始日 2026-09-01；总周数 18"))
        assertTrue(context.contains("当前第 13 周"))
        assertTrue(todoLine.contains("报告 标题 "))
        assertTrue(todoLine.contains("（课程：课程 名称 "))
        assertTrue(todoLine.endsWith("；截止 2026-11-25 18:30；来源 学习通本地同步"))
        assertTrue(todoLine.length < 400)
        assertTrue(courseLine.contains("课程 标题 "))
        assertTrue(courseLine.contains("教室 位置 "))
        assertTrue(courseLine.length < 230)
        assertTrue(todoLine.contains("…"))
        assertTrue(courseLine.contains("…"))
        assertFalse(context.any { it == '\r' || it == '\t' || it == '\u2028' || it == '\u2029' || it == '\u0085' })
    }

    @Test
    fun longCourseAndTodoListsPreserveBothSectionsWithinBudget() {
        val now = LocalDate.of(2026, 11, 24).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val calendar = CalendarInfo(13, 18, "2026-09-01", "2026-2027", "1")
        val blocks = (1..80).map { index ->
            CourseBlock(
                13, (index - 1) % 7 + 1, 1, 2, "课程$index" + "课".repeat(1000),
                "教室" + "室".repeat(1000), "教师", "2026-11-24",
            )
        }
        val todos = (1..60).map { index ->
            Todo(
                index.toLong(), "待办$index" + "写".repeat(1000), false, TodoKind.HOMEWORK,
                "关联课程" + "课".repeat(1000), now + 86_400_000 + index, "chaoxing",
            )
        } + Todo(100, "已完成且不应显示", true)

        val context = buildStudentContext(calendar, blocks, todos, now)
        val courseLines = context.lineSequence().filter { it.startsWith("- 周") }.toList()
        val todoLines = context.lineSequence().filter { it.startsWith("- [HOMEWORK]") }.toList()

        assertTrue(context.length <= 8000)
        assertTrue(courseLines.isNotEmpty())
        assertTrue(todoLines.isNotEmpty())
        assertTrue(courseLines.size < 40)
        assertTrue(todoLines.size < 30)
        assertTrue(context.contains("另有 ${80 - courseLines.size} 条课程未展示"))
        assertTrue(context.contains("另有 ${60 - todoLines.size} 条待办未展示"))
        assertTrue(todoLines.all { it.contains("；截止 ") && it.endsWith("；来源 学习通本地同步") })
        assertFalse(context.contains("已完成且不应显示"))
        assertTrue(context.endsWith("。）\n"))
    }

    @Test
    fun countLimitsReportOmittedCoursesAndOnlyUnfinishedTodos() {
        val now = LocalDate.of(2026, 11, 24).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val calendar = CalendarInfo(13, 18, "2026-09-01", "2026-2027", "1")
        val blocks = (1..41).map { index ->
            CourseBlock(13, 1, 1, 2, "课程$index", "A101", "教师", "2026-11-24")
        }
        val todos = (1..31).map { index -> Todo(index.toLong(), "待办$index", false) } +
            Todo(100, "已完成且不应计入", true)

        val context = buildStudentContext(calendar, blocks, todos, now)
        val todoLines = context.lineSequence().filter { it.startsWith("- [GENERAL]") }.toList()

        assertEquals(40, context.lineSequence().count { it.startsWith("- 周一") })
        assertEquals(30, todoLines.size)
        assertTrue(context.contains("另有 1 条课程未展示"))
        assertTrue(context.contains("另有 1 条待办未展示"))
        assertTrue(todoLines.all { it.endsWith("；未设置截止时间；来源 本地") })
        assertFalse(context.contains("已完成且不应计入"))
        assertTrue(context.length <= 8000)
    }

    @Test
    fun missingTasksAreNotPresentedAsProofOfNoRemoteAssignments() {
        val context = buildStudentContext(null, emptyList(), emptyList(), 0L)

        assertTrue(context.contains("不代表学习通远端没有作业"))
        assertTrue(UniversityKnowledge.systemPrompt.contains("主动回忆"))
        assertTrue(UniversityKnowledge.systemPrompt.contains("不代写"))
    }
}
