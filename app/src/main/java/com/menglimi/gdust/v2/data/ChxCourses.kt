package com.menglimi.gdust.v2.data

/** 学习通默认课程页的本次快照，不代表其他分页、文件夹或全部历史课程。 */
data class ChxCourseOverview(
    val courses: List<ChxCourse>,
    val progressError: String? = null,
    val loadedAt: Long = System.currentTimeMillis(),
    val progressAuthRequired: Boolean = false,
)

/** description 原样保留页面的两行说明，不推断其中的人名或机构角色。 */
data class ChxCourse(
    val clazzId: String,
    val courseId: String,
    val curPersonId: String,
    val name: String,
    val description: String,
    val url: String,
    val progress: ChxCourseProgress? = null,
) {
    val info: String get() = "${clazzId}_${curPersonId}"
}

/** 任务点统计不是作业待办；jobRate 是接口返回的 0–100 百分数。 */
data class ChxCourseProgress(
    val jobCount: Int,
    val jobFinishCount: Int,
    val jobRate: Double,
) {
    init {
        require(jobCount >= 0 && jobFinishCount in 0..jobCount)
        require(jobRate.isFinite() && jobRate in 0.0..100.0)
    }
}
