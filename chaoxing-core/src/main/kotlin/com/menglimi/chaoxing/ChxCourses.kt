package com.menglimi.chaoxing

/** 学习通默认课程页的本次快照，不代表其他分页、文件夹或全部历史课程。 */
data class ChxCourseOverview(
    val courses: List<ChxCourse>,
    val progressError: String? = null,
    val loadedAt: Long = System.currentTimeMillis(),
    val progressAuthRequired: Boolean = false,
    val requestPause: ChxRequestPause? = null,
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
    /** 页面「开课时间：开始～结束」的原始日期；缺项或一侧缺失时为 null，不代表课程状态。 */
    val openingStart: String? = null,
    val openingEnd: String? = null,
    /** 页面明确标注「课程已结束」（not-open-tip），与开课时间无关。 */
    val ended: Boolean = false,
) {
    val info: String get() = "${clazzId}_${curPersonId}"

    /** 仅用页面给出的时间判断；开课区间覆盖今天返回 false，完全未知时返回 null。 */
    fun closedAsOf(today: String): Boolean? = when {
        ended -> true
        openingEnd != null && openingEnd < today -> true
        openingStart != null && openingStart > today -> true
        openingStart != null && openingEnd != null -> false
        else -> null
    }
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
