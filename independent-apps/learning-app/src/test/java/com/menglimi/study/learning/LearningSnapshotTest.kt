package com.menglimi.study.learning

import com.menglimi.chaoxing.ChxCourse
import com.menglimi.chaoxing.ChxCourseOverview
import com.menglimi.chaoxing.ChxWorkCourse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class LearningSnapshotTest {
    private val url = "https://mooc1.chaoxing.com/visit/stucoursemiddle"
    private val course = ChxWorkCourse(1, 2, 3, "合成课程", url)
    private val work = Homework("1:2:3:5", course.name, "合成作业", "未交", 0,
        "https://mooc1.chaoxing.com/mooc-ans/mooc2/work/task?workId=5")
    private val snapshot = LearningSnapshot("a".repeat(64), listOf(CourseHomework(course.cacheKey(), listOf(work), 123,
        course, "合成读取失败", false)), true,
        ChxCourseOverview(listOf(ChxCourse("2", "1", "3", course.name, "合成描述", url)), "进度未知", 456))

    @Test fun roundTripKeepsCourseFailureAndUnknownProgressSeparateFromWorks() {
        val encoded = LearningSnapshotCodec.encode(snapshot)
        val restored = LearningSnapshotCodec.decode(encoded)
        assertEquals(snapshot, restored)
        assertEquals(123L, restored.courses.single().updatedAt)
        assertEquals("合成读取失败", restored.courses.single().failure)
        assertNull(restored.overview!!.courses.single().progress)
        assertFalse(encoded.contains("Cookie"))
        assertFalse(encoded.contains("password"))
    }
    @Test fun workOnlyAndCourseOnlySnapshotsRoundTripWithoutInventingOtherSection() {
        val workOnly = snapshot.copy(overview = null)
        assertEquals(workOnly, LearningSnapshotCodec.decode(LearningSnapshotCodec.encode(workOnly)))
        val coursesOnly = snapshot.copy(courses = emptyList(), workLoaded = false)
        assertEquals(coursesOnly, LearningSnapshotCodec.decode(LearningSnapshotCodec.encode(coursesOnly)))
    }
    @Test fun pauseAndRemainingCoursesSurviveSerializationAndOlderSnapshotsStillLoad() {
        val controlled = snapshot.copy(readControl = LearningReadControl(
            com.menglimi.chaoxing.ChxRequestPause(true, 123_456), course.cacheKey(), false,
            listOf(course.cacheKey()), course.url))
        assertEquals(controlled, LearningSnapshotCodec.decode(LearningSnapshotCodec.encode(controlled)))
        val old = com.google.gson.JsonParser.parseString(LearningSnapshotCodec.encode(snapshot)).asJsonObject
        old.remove("readControl")
        assertEquals(snapshot, LearningSnapshotCodec.decode(old.toString()))
    }

    @Test fun mismatchedCourseIdentityCannotBeRestoredForRetry() {
        val broken = snapshot.copy(courses = listOf(snapshot.courses.single().copy(course = course.copy(cpi = 9))))
        assertThrows(IllegalArgumentException::class.java) { LearningSnapshotCodec.decode(LearningSnapshotCodec.encode(broken)) }
    }
    @Test fun foreignCachedUrlIsRejectedBeforeWebViewCanOpenIt() {
        val broken = snapshot.copy(courses = listOf(snapshot.courses.single().copy(works = listOf(work.copy(detailUrl = "https://example.org/private")))))
        assertThrows(IllegalArgumentException::class.java) { LearningSnapshotCodec.decode(LearningSnapshotCodec.encode(broken)) }
    }
    @Test fun duplicatedKeysCannotProduceAmbiguousUiOrRetryTargets() {
        val broken = snapshot.copy(courses = snapshot.courses + snapshot.courses)
        assertThrows(IllegalArgumentException::class.java) { LearningSnapshotCodec.decode(LearningSnapshotCodec.encode(broken)) }
    }
}
