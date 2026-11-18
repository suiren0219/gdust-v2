package com.menglimi.study.learning

import com.menglimi.chaoxing.ChxAuthFailed
import com.menglimi.chaoxing.ChxCourse
import com.menglimi.chaoxing.ChxCourseOverview
import com.menglimi.chaoxing.ChxCourseReadException
import com.menglimi.chaoxing.ChxCourseWorkResult
import com.menglimi.chaoxing.ChxWorkCourse

/** 来自 campus-lite 的作业快照；任务点统计始终与作业分离。 */
data class Homework(
    val key: String,
    val courseName: String,
    val title: String,
    val status: String,
    val dueAt: Long,
    val detailUrl: String,
)

data class HomeworkFailure(val course: ChxWorkCourse, val message: String, val hasCachedResult: Boolean)

data class LearningUiState(
    val connected: Boolean = false,
    val loading: Boolean = false,
    val works: List<Homework> = emptyList(),
    val failedCourses: Int = 0,
    val failures: List<HomeworkFailure> = emptyList(),
    val courseCount: Int = 0,
    val endedCourses: Int = 0,
    val error: String? = null,
    val updatedAt: Long = 0,
    val hasSnapshot: Boolean = false,
    val fromCache: Boolean = false,
    val retryingCourse: String? = null,
    val readControl: LearningReadControl = LearningReadControl(),
)

data class CoursesUiState(
    val connected: Boolean = false,
    val loading: Boolean = false,
    val courses: List<ChxCourse> = emptyList(),
    val error: String? = null,
    val progressError: String? = null,
    val updatedAt: Long = 0,
    val hasSnapshot: Boolean = false,
    val fromCache: Boolean = false,
    val readControl: LearningReadControl = LearningReadControl(),
    val library: LearningLibrary = LearningLibrary(),
) {
    val visible: List<ChxCourse> get() = library.visible(courses)
}

/** courseId/classId/cpi 一起隔离重名课程、班级和选课身份。 */
internal fun ChxWorkCourse.cacheKey(): String = "$courseId:$classId:$cpi"

/** 本地日期键，用于比较页面开课时间（页面日期无时区语义）。 */
internal fun todayKey(now: Long): String =
    java.time.Instant.ofEpochMilli(now).atZone(java.time.ZoneId.systemDefault()).toLocalDate().toString()

internal data class CourseHomework(
    val courseKey: String,
    val works: List<Homework>,
    val updatedAt: Long,
    val course: ChxWorkCourse? = null,
    val failure: String? = null,
    val authRequired: Boolean = false,
    /** 页面标注已结束或开课时间已过；不计入失败，只是本轮不读取。 */
    val ended: Boolean = false,
)

internal data class HomeworkMerge(val courses: List<CourseHomework>, val failedCourses: Int) {
    val works: List<Homework> get() = courses.flatMap { it.works }
    // 部分结果沿用旧缓存时，不把旧作业的时间更新为本次刷新时间。
    val updatedAt: Long get() = courses.minOfOrNull { it.updatedAt } ?: 0L
    val failures: List<HomeworkFailure> get() = courses.mapNotNull { row ->
        val course = row.course ?: return@mapNotNull null
        row.failure?.let { HomeworkFailure(course, it, row.updatedAt > 0) }
    }
}

/** 单课重试仅替换目标课程；全量刷新按本次发现的课程集合更新。 */
internal fun mergeHomework(
    previous: List<CourseHomework>,
    results: List<ChxCourseWorkResult>,
    now: Long,
    partial: Boolean = false,
): HomeworkMerge {
    val old = previous.associateBy { it.courseKey }
    val replacements = results.distinctBy { it.course.cacheKey() }.map { result ->
        val key = result.course.cacheKey()
        when (result) {
            is ChxCourseWorkResult.Success -> CourseHomework(
                key,
                result.items.map { work ->
                    Homework("$key:${work.item.workId}", result.course.name, work.item.title,
                        work.item.status, work.dueAt, work.item.detailUrl)
                }, now, result.course,
            )
            is ChxCourseWorkResult.Failure -> (old[key] ?: CourseHomework(key, emptyList(), 0L)).copy(
                course = result.course,
                failure = learningFailureMessage(result.error),
                authRequired = result.error is ChxAuthFailed,
            )
        }
    }
    val courses = if (partial) {
        val byKey = replacements.associateBy { it.courseKey }
        previous.map { byKey[it.courseKey] ?: it } + replacements.filter { it.courseKey !in old }
    } else replacements
    return HomeworkMerge(courses, courses.count { it.failure != null })
}

internal fun learningFailureMessage(error: Exception): String = when (error) {
    is ChxAuthFailed -> "学习通登录已失效，请重新登录。"
    is ChxCourseReadException -> error.message ?: "页面结构暂时无法读取，请稍后重试。"
    else -> "读取未完成，请检查网络后重试。"
}

internal fun coursesState(
    overview: ChxCourseOverview,
    cached: Boolean,
    library: LearningLibrary = LearningLibrary(),
): CoursesUiState = CoursesUiState(
    connected = !cached && !overview.progressAuthRequired,
    courses = overview.courses,
    // 未知进度直接显示未知，绝不沿用上一份统计来填充或按作业数推算。
    progressError = overview.progressError,
    updatedAt = overview.loadedAt,
    hasSnapshot = true,
    fromCache = cached,
    library = library,
)

internal enum class WorkFilter(val title: String) { PENDING("待交"), DUE_SOON("临近截止"), SUBMITTED("已交") }
internal fun selectHomework(works: List<Homework>, filter: WorkFilter, now: Long): List<Homework> = works.filter {
    when (filter) {
        WorkFilter.PENDING -> it.status == "未交"
        WorkFilter.DUE_SOON -> it.status == "未交" && it.dueAt in now..(now + 24 * 3600_000L)
        WorkFilter.SUBMITTED -> it.status in setOf("已完成", "已提交")
    }
}.sortedWith(compareBy<Homework> { if (it.dueAt > 0) it.dueAt else Long.MAX_VALUE }.thenBy { it.courseName })
