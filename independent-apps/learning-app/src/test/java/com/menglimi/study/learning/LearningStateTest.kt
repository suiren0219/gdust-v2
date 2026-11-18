package com.menglimi.study.learning

import com.menglimi.chaoxing.ChxCourse
import com.menglimi.chaoxing.ChxCourseOverview
import com.menglimi.chaoxing.ChxCourseProgress
import kotlinx.coroutines.CancellationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class LearningStateTest {
    private val course = ChxCourse("1", "2", "3", "合成课程", "", "https://mooc1.chaoxing.com/visit/stucoursemiddle")
    @Test fun unknownProgressIsNotEstimatedFromOldProgressOrWorks() {
        val old = coursesState(ChxCourseOverview(listOf(course.copy(progress = ChxCourseProgress(20, 10, 50.0))), loadedAt = 100), false)
        val current = coursesState(ChxCourseOverview(listOf(course), "任务点进度未知", 200), false)
        assertEquals(50.0, old.courses.single().progress!!.jobRate, 0.0)
        assertNull(current.courses.single().progress)
        assertEquals("任务点进度未知", current.progressError)
        assertTrue(current.connected)
    }
    @Test fun cachedOrExpiredCourseSnapshotDoesNotProveLogin() {
        val overview = ChxCourseOverview(listOf(course), loadedAt = 100)
        assertFalse(coursesState(overview, true).connected)
        assertTrue(coursesState(overview, true).fromCache)
        assertFalse(coursesState(overview.copy(progressAuthRequired = true), false).connected)
    }
    @Test fun statusAndDueTimeFiltersDoNotTurnUnknownIntoSubmitted() {
        val works = listOf(Homework("a", "课", "未知", "未知", 0, ""), Homework("b", "课", "待交", "未交", 150, ""),
            Homework("c", "课", "未知截止", "未交", 0, ""), Homework("d", "课", "已完成", "已完成", 0, ""))
        assertEquals(listOf("b", "c"), selectHomework(works, WorkFilter.PENDING, 100).map { it.key })
        assertEquals(listOf("b"), selectHomework(works, WorkFilter.DUE_SOON, 100).map { it.key })
        assertEquals(listOf("d"), selectHomework(works, WorkFilter.SUBMITTED, 100).map { it.key })
    }
    @Test fun identityFingerprintRequiresOneUniqueUidAndDoesNotStoreRawValue() {
        val first = learningAccountFingerprint("_uid=synthetic-a; UID=synthetic-a; session=not-identity")
        assertTrue(first!!.matches(Regex("[0-9a-f]{64}")))
        assertEquals(first, learningAccountFingerprint("UID=synthetic-a"))
        assertNotEquals(first, learningAccountFingerprint("_uid=synthetic-b"))
        assertNull(learningAccountFingerprint("_uid=synthetic-a; UID=synthetic-b"))
        assertNull(learningAccountFingerprint("JSESSIONID=synthetic"))
    }
    @Test fun courseRefreshDoesNotInvalidateWorkRequestButNewWorkRequestDoes() {
        val guard = LearningSessionGuard()
        val work = guard.begin(LearningLane.WORK)
        val courses = guard.begin(LearningLane.COURSES)
        guard.check(work); guard.check(courses)
        guard.begin(LearningLane.WORK)
        assertThrows(CancellationException::class.java) { guard.check(work) }
        guard.check(courses)
    }
    @Test fun accountGenerationInvalidatesBothLanesIncludingLateResponses() {
        val guard = LearningSessionGuard()
        val work = guard.begin(LearningLane.WORK)
        val courses = guard.begin(LearningLane.COURSES)
        guard.invalidate()
        assertFalse(guard.isCurrent(work)); assertFalse(guard.isCurrent(courses))
        assertThrows(CancellationException::class.java) { guard.check(work) }
        assertThrows(CancellationException::class.java) { guard.check(courses) }
    }
}
