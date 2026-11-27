package com.menglimi.gdust.v2.data

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/** 数据与最近成功时间一起落盘；刷新失败不创建新快照。 */
data class DataSnapshot<T>(val data: T, val updatedAt: Long)

internal object DataSnapshots {
    private val gson = Gson()

    fun grades(json: String?, year: String, semester: String): DataSnapshot<GradeReport>? = decode(json,
        object : TypeToken<DataSnapshot<GradeReport>>() {}) { snapshot ->
        val report = snapshot.data
        GradeReport(report.entries, report.total)
        require(report.entries.all { it.year == year && it.semester == semester && it.courseName.isNotBlank() })
    }

    fun courses(json: String?): DataSnapshot<ChxCourseOverview>? = decode(json,
        object : TypeToken<DataSnapshot<ChxCourseOverview>>() {}) { snapshot ->
        require(snapshot.data.courses.isNotEmpty())
        snapshot.data.courses.forEach { course ->
            require(course.name.isNotBlank() && course.courseId.isNotBlank() && course.clazzId.isNotBlank())
            require(ChxCourseApi.trustedCourseUrl(course.url, course.clazzId, course.courseId, course.curPersonId) != null)
            course.progress?.let { ChxCourseProgress(it.jobCount, it.jobFinishCount, it.jobRate) }
        }
    }

    fun notices(json: String?): DataSnapshot<List<Notice>>? = decode(json,
        object : TypeToken<DataSnapshot<List<Notice>>>() {}) { snapshot ->
        require(snapshot.data.all { it.title.isNotBlank() && noticeWebUrl(it.url) != null && it.time >= 0 })
    }

    fun powerRooms(json: String?): DataSnapshot<List<PowerRoom>>? = decode(json,
        object : TypeToken<DataSnapshot<List<PowerRoom>>>() {}) { snapshot ->
        require(snapshot.data.all { it.name.isNotBlank() && it.verify.isNotBlank() })
    }

    fun calendarForSync(raw: CalendarInfo, semester: String?, startDate: String, weeks: Int, week: Int): CalendarInfo {
        require(semester == null || semester in setOf("3", "12")) { "请选择有效学期。" }
        return raw.copy(
            semester = semester ?: raw.semester,
            startDate = startDate.ifBlank { raw.startDate },
            allWeek = if (weeks > 0) weeks else raw.allWeek,
            week = if (week > 0) week else raw.week,
        )
    }

    /** 列表读成功、进度读失败时，只复用同一课程身份的历史进度；错误与认证状态仍取本次结果。 */
    fun mergeCourseProgress(previous: ChxCourseOverview?, current: ChxCourseOverview): ChxCourseOverview {
        if (previous == null || current.courses.none { it.progress == null }) return current
        val cached = previous.courses.associateBy { Triple(it.clazzId, it.courseId, it.curPersonId) }
        var retained = false
        val courses = current.courses.map { course ->
            val progress = cached[Triple(course.clazzId, course.courseId, course.curPersonId)]?.progress
            if (course.progress == null && progress != null) {
                retained = true
                course.copy(progress = progress)
            } else course
        }
        if (!retained) return current
        val warning = "部分任务点进度沿用上次成功结果，不代表本次读取或认证成功。"
        return current.copy(courses = courses,
            progressError = listOfNotNull(current.progressError, warning).distinct().joinToString("\n"))
    }

    /** Cookie 可失效或被清除，快照归属不应随凭据一起丢失；显式新身份优先，不能回落到旧账号。 */
    fun courseSnapshotOwner(liveCookie: String?, savedCookie: String?, persistedOwner: String?): String? =
        cookieOwner(liveCookie) ?: cookieOwner(savedCookie) ?: persistedOwner?.takeIf { it.isNotBlank() }

    fun cookieOwner(cookie: String?): String? = ChxSso.cookiePairs(cookie)
        .filter { it.first in setOf("_uid", "UID") }.map { it.second }.distinct()
        .singleOrNull()?.takeIf { it.isNotBlank() }

    fun encode(snapshot: DataSnapshot<*>): String = gson.toJson(snapshot)

    private fun <T> decode(json: String?, type: TypeToken<DataSnapshot<T>>, validate: (DataSnapshot<T>) -> Unit): DataSnapshot<T>? {
        if (json == null) return null
        return runCatching {
            val snapshot = requireNotNull(gson.fromJson<DataSnapshot<T>>(json, type.type))
            require(snapshot.updatedAt > 0 && snapshot.data != null)
            validate(snapshot)
            snapshot
        }.getOrNull()
    }
}
