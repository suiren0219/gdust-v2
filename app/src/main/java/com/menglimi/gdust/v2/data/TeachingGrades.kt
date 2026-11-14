package com.menglimi.gdust.v2.data

/** 教务原文；成绩可能是「合格」「缓考」等文本，不据此推导平均绩点。 */
data class TeachingGrade(
    val year: String,
    val semester: String,
    val yearLabel: String,
    val semesterLabel: String,
    val courseCode: String,
    val courseName: String,
    val courseNature: String,
    val score: String,
    val credits: String,
    val gradePoint: String,
    val creditGradePoint: String,
    val examType: String,
    val department: String,
    val courseCategory: String? = null,
    val courseAttribute: String? = null,
    val assessmentMethod: String? = null,
    val teachingClass: String? = null,
    val teacher: String? = null,
    val degreeCourse: String? = null,
    val courseMark: String? = null,
)

/** 仅在所有分页校验成功后创建；不保存学号、姓名、userModel 或响应原文。 */
data class GradeReport(val entries: List<TeachingGrade>, val total: Int) {
    init {
        require(total >= 0 && entries.size == total) { "成绩数据尚未完整加载" }
    }
}

internal enum class GradeFailure(val message: String) {
    STRUCTURE("教务返回的成绩格式无法识别，请稍后重试"),
    INCOMPLETE("成绩分页不完整或已发生变化，请重新查询"),
    LIMIT("成绩条数超出单次读取范围，请在教务网页查看"),
    TERM_UNAVAILABLE("教务页面暂未提供所选学年或学期，请调整后重试"),
    TERM_MISMATCH("教务返回的成绩与所选学期不一致，请重新查询"),
    RESPONSE("教务暂时无法返回成绩，请稍后重试"),
}

/** 消息仅来自固定文案，避免把服务器响应或个人信息带入状态提示。 */
internal class GradeLoadException(val reason: GradeFailure) : Exception(reason.message)
