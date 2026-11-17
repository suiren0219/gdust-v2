package com.menglimi.study.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.menglimi.ksu.ui.Appearance
import com.menglimi.ksu.ui.Glyph
import com.menglimi.ksu.ui.KsuScaffold
import com.menglimi.ksu.ui.NavItem
import com.menglimi.study.learning.CoursesUiState
import com.menglimi.study.learning.LearningUiState

@Composable
fun StudyRoot(
    tab: Int, onTab: (Int) -> Unit, appearance: Appearance,
    work: LearningUiState, courses: CoursesUiState,
    onAppearance: (Appearance) -> Unit, onLogin: () -> Unit,
    onWorkRefresh: () -> Unit, onRetryCourse: (String) -> Unit,
    onCoursesRefresh: () -> Unit, onOpenPage: (String) -> Unit,
    onWorkClearError: () -> Unit, onCoursesClearError: () -> Unit,
    onDisconnect: () -> Unit, onClearCache: () -> Unit,
    preview: Boolean = false, onResumeRead: () -> Unit = onWorkRefresh, diagnostic: String? = null,
) {
    BackHandler(tab != 0) { onTab(0) }
    KsuScaffold(tab, onTab, listOf(NavItem("作业", Glyph.BOOK), NavItem("课程", Glyph.CHART), NavItem("设置", Glyph.SETTINGS))) { page ->
        when (page) {
            0 -> LearningScreen(work, onWorkRefresh, onRetryCourse, onLogin, onOpenPage, onWorkClearError,
                Modifier.fillMaxSize(), preview, onResumeRead, diagnostic)
            1 -> CoursesScreen(courses, onCoursesRefresh, onLogin, onOpenPage, onCoursesClearError,
                Modifier.fillMaxSize(), preview, onResumeRead, diagnostic)
            else -> SettingsScreen(appearance, work, courses, onAppearance, onLogin, onDisconnect, onClearCache,
                Modifier.fillMaxSize(), preview)
        }
    }
}
