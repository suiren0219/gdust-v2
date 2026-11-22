package com.menglimi.study.learning

import com.menglimi.chaoxing.ChxAuthFailed
import com.menglimi.chaoxing.ChxCourseWorkResult
import com.menglimi.chaoxing.ChxWorkCourse
import com.menglimi.chaoxing.ChxWorkItem
import com.menglimi.chaoxing.ChxWorkWithDeadline
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 继承 campus-lite 作业合并回归，追加独立应用的单课重试。 */
class LearningMergeTest {
    private val courseA = ChxWorkCourse(1, 2, 3, "同名课程", "https://mooc1.chaoxing.com/visit/stucoursemiddle")
    private val courseB = ChxWorkCourse(1, 20, 30, "同名课程", "https://mooc1.chaoxing.com/visit/stucoursemiddle")
    private val oldA = cached(courseA, "旧作业 A", 100)
    private val oldB = cached(courseB, "旧作业 B", 200)

    @Test fun replacesSuccessfulCourseAndKeepsFailedCourseTimestamp() {
        val merged = mergeHomework(listOf(oldA, oldB), listOf(success(courseA, "新作业 A"),
            ChxCourseWorkResult.Failure(courseB, IllegalStateException("synthetic"))), 300)
        assertEquals(listOf("新作业 A", "旧作业 B"), merged.works.map { it.title })
        assertEquals(1, merged.failedCourses)
        assertEquals(200L, merged.updatedAt)
        assertEquals(300L, merged.courses.first().updatedAt)
    }
    @Test fun unknownFailedCourseIsNotTreatedAsSuccessfulEmptyResult() {
        val merged = mergeHomework(listOf(oldA), listOf(success(courseA, "新作业 A"),
            ChxCourseWorkResult.Failure(courseB, IllegalStateException("synthetic"))), 300)
        assertEquals(1, merged.failedCourses)
        assertEquals(0L, merged.updatedAt)
        assertEquals(0L, merged.courses.last().updatedAt)
        assertFalse(merged.failures.single().hasCachedResult)
    }
    @Test fun successfulEmptyCourseReplacesOldWorksButFailureDoesNot() {
        val merged = mergeHomework(listOf(oldA, oldB), listOf(ChxCourseWorkResult.Success(courseA, emptyList()),
            ChxCourseWorkResult.Failure(courseB, ChxAuthFailed(401, "synthetic"))), 300)
        assertEquals(listOf("旧作业 B"), merged.works.map { it.title })
        assertEquals(1, merged.failedCourses)
        assertTrue(merged.courses.first().works.isEmpty())
    }
    @Test fun doesNotMergeAcrossClassOrPersonEvenWhenNamesMatch() {
        val merged = mergeHomework(listOf(oldA), listOf(ChxCourseWorkResult.Failure(courseB, IllegalStateException("synthetic"))), 300)
        assertTrue(merged.works.isEmpty())
        assertEquals(1, merged.failedCourses)
        assertFalse(merged.courses.any { it.courseKey == courseA.cacheKey() })
    }
    @Test fun stableKeyDoesNotDependOnRotatingSignedDetailUrl() {
        val first = mergeHomework(emptyList(), listOf(success(courseA, "作业", "first")), 100)
        val second = mergeHomework(first.courses, listOf(success(courseA, "作业", "second")), 200)
        assertEquals(first.works.single().key, second.works.single().key)
        assertTrue(second.works.single().detailUrl.endsWith("second"))
        assertEquals(200L, second.updatedAt)
        assertEquals(0, second.failedCourses)
    }
    @Test fun retryOneCourseKeepsOtherCourseAndRemovesOnlyItsFailure() {
        val failed = mergeHomework(listOf(oldA, oldB), listOf(
            ChxCourseWorkResult.Failure(courseA, IllegalStateException()),
            ChxCourseWorkResult.Failure(courseB, IllegalStateException())), 300)
        val retried = mergeHomework(failed.courses, listOf(success(courseA, "重试后 A")), 400, partial = true)
        assertEquals(listOf("重试后 A", "旧作业 B"), retried.works.map { it.title })
        assertEquals(1, retried.failedCourses)
        assertEquals(courseB, retried.failures.single().course)
        assertEquals(200L, retried.updatedAt)
    }
    private fun cached(course: ChxWorkCourse, title: String, at: Long) = CourseHomework(course.cacheKey(),
        listOf(Homework("${course.cacheKey()}:5", course.name, title, "未交", 0,
            "https://mooc1.chaoxing.com/mooc-ans/mooc2/work/task?workId=5")), at, course)
    private fun success(course: ChxWorkCourse, title: String, signature: String = "synthetic") =
        ChxCourseWorkResult.Success(course, listOf(ChxWorkWithDeadline(ChxWorkItem(title, "未交",
            "https://mooc1.chaoxing.com/mooc-ans/mooc2/work/task?workId=5&enc=$signature", 5, 6, signature, ""), 500)))
}
