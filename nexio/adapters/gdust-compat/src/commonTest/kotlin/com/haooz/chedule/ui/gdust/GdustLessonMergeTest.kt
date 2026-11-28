package com.haooz.chedule.ui.gdust

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 门户课表「按节返回」的连堂合并。
 *
 * 门户同一门连堂课是 N 条单节记录（1、2、3…），不合并课表就渲染成 N 块碎卡。
 * 合并条件必须同时满足：同天 + 同课名 + 同教室 + 同教师 + 同周次 + 节次紧邻。
 * 这里钉住几个容易写错的点：跨节记录不能被截断、不同周次不能合、断档不能合。
 */
class GdustLessonMergeTest {

    private fun lesson(
        name: String = "高等数学",
        classroom: String = "A101",
        teacher: String = "张老师",
        day: Int = 1,
        start: Int,
        end: Int = start,
        weeks: List<Int> = (1..16).toList(),
    ) = GdustCourseData(
        name = name,
        classroom = classroom,
        teacher = teacher,
        dayOfWeek = day,
        startSection = start,
        endSection = end,
        weeks = weeks,
    )

    @Test
    fun `三节连续单节记录合并成一条整卡`() {
        val merged = mergeConsecutiveLessons(listOf(lesson(start = 1), lesson(start = 2), lesson(start = 3)))
        assertEquals(1, merged.size)
        assertEquals(1, merged[0].startSection)
        assertEquals(3, merged[0].endSection)
    }

    @Test
    fun `跨节记录合并时末节取后一条的 endSection`() {
        // 门户若返回 1-2、3-4 两条跨节记录，合并结果必须是 1-4 而不是 1-3
        val merged = mergeConsecutiveLessons(listOf(lesson(start = 1, end = 2), lesson(start = 3, end = 4)))
        assertEquals(1, merged.size)
        assertEquals(1, merged[0].startSection)
        assertEquals(4, merged[0].endSection)
    }

    @Test
    fun `节次断档不合并`() {
        val merged = mergeConsecutiveLessons(listOf(lesson(start = 1), lesson(start = 2), lesson(start = 4)))
        assertEquals(2, merged.size)
        assertEquals(1 to 2, merged[0].startSection to merged[0].endSection)
        assertEquals(4 to 4, merged[1].startSection to merged[1].endSection)
    }

    @Test
    fun `教室或教师不同不合并`() {
        val merged = mergeConsecutiveLessons(
            listOf(
                lesson(start = 1, classroom = "A101"),
                lesson(start = 2, classroom = "B202"),
                lesson(start = 3, teacher = "李老师"),
            )
        )
        assertEquals(3, merged.size)
    }

    @Test
    fun `周次不同不合并`() {
        // 单双周同一时间都是「高等数学」，但周次集合不同，必须保留两条
        val merged = mergeConsecutiveLessons(
            listOf(
                lesson(start = 1, end = 2, weeks = (1..16).filter { it % 2 == 1 }),
                lesson(start = 1, end = 2, weeks = (1..16).filter { it % 2 == 0 }),
            )
        )
        assertEquals(2, merged.size)
        assertTrue(merged.all { it.startSection == 1 && it.endSection == 2 })
    }

    @Test
    fun `不同天各自合并互不干扰`() {
        val merged = mergeConsecutiveLessons(
            listOf(
                lesson(day = 1, start = 1),
                lesson(day = 2, start = 1),
                lesson(day = 1, start = 2),
            )
        )
        assertEquals(2, merged.size)
        val monday = merged.single { it.dayOfWeek == 1 }
        val tuesday = merged.single { it.dayOfWeek == 2 }
        assertEquals(1 to 2, monday.startSection to monday.endSection)
        assertEquals(1 to 1, tuesday.startSection to tuesday.endSection)
    }
}
