package com.menglimi.kedian.learning

import com.menglimi.chaoxing.ChxCourseWorkResult
import com.menglimi.chaoxing.ChxWorkCourse

data class Homework(
    val key: String,
    val courseName: String,
    val title: String,
    val status: String,
    val dueAt: Long,
    val detailUrl: String,
)

data class LearningUiState(
    val connected: Boolean = false,
    val loading: Boolean = false,
    val works: List<Homework> = emptyList(),
    val failedCourses: Int = 0,
    val error: String? = null,
    val updatedAt: Long = 0,
)

/** courseId/classId/cpi 一起隔离重名课程、班级和选课身份。 */
internal fun ChxWorkCourse.cacheKey(): String = "$courseId:$classId:$cpi"

internal data class CourseHomework(
    val courseKey: String,
    val works: List<Homework>,
    val updatedAt: Long,
)

internal data class HomeworkMerge(
    val courses: List<CourseHomework>,
    val failedCourses: Int,
) {
    val works: List<Homework> get() = courses.flatMap { it.works }
    // 部分结果沿用旧缓存时，不把旧作业的时间更新为本次刷新时间。
    val updatedAt: Long get() = courses.minOfOrNull { it.updatedAt } ?: 0L
}

internal fun mergeHomework(
    previous: List<CourseHomework>,
    results: List<ChxCourseWorkResult>,
    now: Long,
): HomeworkMerge {
    val old = previous.associateBy { it.courseKey }
    val courses = results.distinctBy { it.course.cacheKey() }.map { result ->
        val courseKey = result.course.cacheKey()
        when (result) {
            is ChxCourseWorkResult.Success -> CourseHomework(
                courseKey,
                result.items.map { work ->
                    Homework(
                        key = "$courseKey:${work.item.workId}",
                        courseName = result.course.name,
                        title = work.item.title,
                        status = work.item.status,
                        dueAt = work.dueAt,
                        detailUrl = work.item.detailUrl,
                    )
                },
                now,
            )
            is ChxCourseWorkResult.Failure -> old[courseKey]
                ?: CourseHomework(courseKey, emptyList(), 0L)
        }
    }
    return HomeworkMerge(courses, results.count { it is ChxCourseWorkResult.Failure })
}
