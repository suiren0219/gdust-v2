package com.menglimi.gdust.v2.data

/** Portal announcements can be filtered by metadata without inferring exam schedules. */
enum class NoticeCategory { ALL, ACADEMIC, EXAM }

fun Notice.matches(category: NoticeCategory): Boolean {
    if (category == NoticeCategory.ALL) return true
    val metadata = listOf(title, type, issuer.orEmpty()).joinToString(" ")
    val exam = listOf("考试", "考务", "补考", "缓考", "测验", "四六级", "期末考")
        .any(metadata::contains)
    return when (category) {
        NoticeCategory.ALL -> true
        NoticeCategory.EXAM -> exam
        NoticeCategory.ACADEMIC -> exam || listOf("教务", "教学", "考试", "考务")
            .any(metadata::contains)
    }
}
