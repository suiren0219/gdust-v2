package com.menglimi.gdust.v2.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class ChxCoursesTest {
    @Test fun taskPointProgressKeepsPercentageAndAllowsZeroTasks() {
        assertEquals(0.0, ChxCourseProgress(0, 0, 0.0).jobRate, 0.0)
        assertEquals(25.0, ChxCourseProgress(8, 2, 25.0).jobRate, 0.0)
        assertEquals(100.0, ChxCourseProgress(8, 8, 100.0).jobRate, 0.0)
    }

    @Test fun invalidCountsAndPercentagesCannotBecomeDisplayableProgress() {
        listOf(-1.0, 100.1, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY).forEach { rate ->
            assertThrows(IllegalArgumentException::class.java) { ChxCourseProgress(4, 1, rate) }
        }
        listOf(-1 to 0, 4 to -1, 4 to 5).forEach { (count, finished) ->
            assertThrows(IllegalArgumentException::class.java) { ChxCourseProgress(count, finished, 25.0) }
        }
    }

    @Test fun courseIdentityAndUnknownProgressStaySeparateFromDescription() {
        val course = ChxCourse("101", "201", "301", "示例课程", "示例教学团队\n示例课程说明",
            "https://mooc1.chaoxing.com/visit/stucoursemiddle?courseid=201&clazzid=101&cpi=301&ismooc2=1&v=2")
        assertEquals("101_301", course.info)
        assertEquals("示例教学团队\n示例课程说明", course.description)
        assertNull(course.progress)
        assertEquals(listOf(course), ChxCourseOverview(listOf(course)).courses)
    }
}
