package com.menglimi.gdust.v2.ui.screens

import org.junit.Assert.*
import org.junit.Test

class SettingsNavigationTest {
    @Test fun productionDirectoryCoversEachExistingEntryExactlyOnce() {
        val expected = setOf("profile", "grades", "schoolNotices", "fiveEducation", "chx", "term", "weekend",
            "autoSync", "maintenanceReminder", "maintenance", "sync", "webImport", "cachedCourses", "calendar", "schedules", "shareSchedule", "reminder",
            "todoReminder", "chxNotifications", "warn", "interval", "desktop", "startTab", "widgets", "export", "import",
            "llm", "summary", "mcp", "about")
        val entries = SettingsNavigation.categories.flatMap { it.entries }
        assertEquals(expected, entries.toSet())
        assertEquals(entries.size, entries.distinct().size)
        assertEquals(7, SettingsNavigation.categories.size)
        assertEquals("AI 与 MCP", SettingsNavigation.categoryFor("mcp").title)
        assertEquals("课表与同步", SettingsNavigation.categoryFor("schedules").title)
        assertEquals("提醒", SettingsNavigation.categoryFor("chxNotifications").title)
    }

    @Test fun serviceStatusIsHiddenFromDirectoryAndUnrelatedCategoriesWithoutSearch() {
        assertFalse(SettingsNavigation.showServiceStatus(null, ""))
        SettingsNavigation.categories.filter { it.title != "账号与服务" }.forEach {
            assertFalse(it.title, SettingsNavigation.showServiceStatus(it.title, ""))
            assertFalse(it.title, SettingsNavigation.showServiceStatus(it.title, "  "))
        }
    }

    @Test fun accountCategoryAndRelatedSearchExposeServiceStatus() {
        assertTrue(SettingsNavigation.showServiceStatus("账号与服务", ""))
        assertTrue(SettingsNavigation.showServiceStatus("账号与服务", "其他"))
        listOf("服务连接", "登录状态", "门户", "教务", "学习通", "验证").forEach {
            assertTrue(it, SettingsNavigation.showServiceStatus(null, " $it "))
            assertTrue(it, SettingsNavigation.showServiceStatus("外观与数据", it))
        }
        assertFalse(SettingsNavigation.showServiceStatus(null, "壁纸"))
        assertFalse(SettingsNavigation.showServiceStatus("提醒", "MCP"))
    }

    @Test fun backClearsSearchBeforeReturningToDirectoryThenExits() {
        assertEquals(SettingsBack.CLEAR_SEARCH, SettingsNavigation.back("AI 与 MCP", "MCP"))
        assertEquals(SettingsBack.CLEAR_SEARCH, SettingsNavigation.back(null, "MCP"))
        assertEquals(SettingsBack.DIRECTORY, SettingsNavigation.back("AI 与 MCP", ""))
        assertEquals(SettingsBack.EXIT, SettingsNavigation.back(null, ""))
    }
}
