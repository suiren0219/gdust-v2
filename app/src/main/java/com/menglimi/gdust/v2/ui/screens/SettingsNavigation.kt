package com.menglimi.gdust.v2.ui.screens

internal data class SettingsCategory(val title: String, val summary: String, val entries: Set<String>)

/** 目录与搜索复用同一份分类，防止二级页迁移遗漏原入口。 */
internal object SettingsNavigation {
    val categories = listOf(
        SettingsCategory("账号与服务", "个人信息、门户、教务与学习通", setOf("profile", "grades", "schoolNotices", "fiveEducation", "chx")),
        SettingsCategory("课表与同步", "学期、自动同步与课表切换", setOf("term", "weekend", "autoSync", "maintenance", "sync", "webImport", "cachedCourses", "calendar", "schedules")),
        SettingsCategory("提醒", "课程、待办与学习通通知", setOf("reminder", "todoReminder", "chxNotifications")),
        SettingsCategory("宿舍电量", "采样节奏与低电量预警", setOf("warn", "interval")),
        SettingsCategory("外观与数据", "启动页、图标、小部件与备份", setOf("desktop", "startTab", "widgets", "export", "import")),
        SettingsCategory("AI 与 MCP", "模型配置、公告总结与课表工具", setOf("llm", "summary", "mcp")),
        SettingsCategory("关于", "版本、更新与数据来源", setOf("about")),
    )
    fun categoryFor(id: String): SettingsCategory = categories.single { id in it.entries }

    fun back(category: String?, query: String): SettingsBack = when {
        query.isNotEmpty() -> SettingsBack.CLEAR_SEARCH
        category != null -> SettingsBack.DIRECTORY
        else -> SettingsBack.EXIT
    }
}

internal enum class SettingsBack { CLEAR_SEARCH, DIRECTORY, EXIT }
