package com.menglimi.gdust.v2.ui.screens

import org.junit.Assert.*
import org.junit.Test

class SettingsNavigationTest {
    @Test fun productionDirectoryCoversEachExistingEntryExactlyOnce() {
        val expected = setOf("profile", "grades", "schoolNotices", "fiveEducation", "chx", "term", "weekend",
            "autoSync", "maintenance", "sync", "webImport", "cachedCourses", "calendar", "schedules", "reminder",
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

    @Test fun backClearsSearchBeforeReturningToDirectoryThenExits() {
        assertEquals(SettingsBack.CLEAR_SEARCH, SettingsNavigation.back("AI 与 MCP", "MCP"))
        assertEquals(SettingsBack.CLEAR_SEARCH, SettingsNavigation.back(null, "MCP"))
        assertEquals(SettingsBack.DIRECTORY, SettingsNavigation.back("AI 与 MCP", ""))
        assertEquals(SettingsBack.EXIT, SettingsNavigation.back(null, ""))
    }
}
