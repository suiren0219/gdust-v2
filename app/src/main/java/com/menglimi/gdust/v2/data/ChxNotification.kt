package com.menglimi.gdust.v2.data

internal object ChxNotification {
    const val PACKAGE = "com.chaoxing.mobile"

    fun todo(title: String?, body: String?): String? {
        val heading = title.orEmpty().replace(Regex("\\s+"), " ").trim()
        val detail = body.orEmpty().replace(Regex("\\s+"), " ").trim()
        val combined = "$heading $detail"
        if (combined.isBlank() || combined.length > 500) return null
        if (listOf("已提交", "已完成", "已批阅", "批改完成", "成绩已发布").any { it in combined }) return null
        if (listOf("作业", "考试", "测验").none { it in combined }) return null
        if (listOf("待提交", "未提交", "待完成", "未完成", "待参加", "即将开始", "发布了", "新作业", "新考试", "截止").none { it in combined }) return null
        return "[学习通] " + listOf(heading, detail).filter { it.isNotBlank() }.distinct().joinToString(" · ")
    }
}
