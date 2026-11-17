package com.menglimi.study.debug

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.menglimi.chaoxing.ChxCourse
import com.menglimi.chaoxing.ChxCourseProgress
import com.menglimi.chaoxing.ChxWorkCourse
import com.menglimi.ksu.ui.Appearance
import com.menglimi.ksu.ui.KsuTheme
import com.menglimi.ksu.ui.ThemeMode
import com.menglimi.study.learning.CoursesUiState
import com.menglimi.study.learning.Homework
import com.menglimi.study.learning.HomeworkFailure
import com.menglimi.study.learning.LearningUiState
import com.menglimi.study.ui.StudyRoot

/** 仅 debug 源集；所有数据和外观都在内存，不创建正式 controller/store/WebView，也不发请求。 */
class PreviewActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val now = System.currentTimeMillis()
        val offline = { Toast.makeText(this, "合成预览，不执行真实请求或正式数据写入", Toast.LENGTH_SHORT).show(); Unit }
        val initialTab = when (intent.getStringExtra("screen")) { "courses" -> 1; "settings" -> 2; else -> 0 }
        val scenario = intent.getStringExtra("state") ?: "ready"
        setContent {
            var tab by remember { mutableIntStateOf(initialTab) }
            var appearance by remember { mutableStateOf(Appearance(
                mode = if (intent.getBooleanExtra("dark", false)) ThemeMode.DARK else ThemeMode.LIGHT,
                dynamic = intent.getBooleanExtra("dynamic", false), amoled = intent.getBooleanExtra("amoled", false))) }
            var work by remember { mutableStateOf(workState(now, scenario)) }
            var courses by remember { mutableStateOf(courseState(now, scenario)) }
            KsuTheme(appearance) {
                StudyRoot(tab, { tab = it }, appearance, work, courses, { appearance = it },
                    onLogin = offline, onWorkRefresh = offline, onRetryCourse = { offline() },
                    onCoursesRefresh = offline, onOpenPage = { offline() },
                    onWorkClearError = { work = work.copy(error = null) },
                    onCoursesClearError = { courses = courses.copy(error = null) },
                    onDisconnect = { work = LearningUiState(); courses = CoursesUiState(); offline() },
                    onClearCache = { work = LearningUiState(); courses = CoursesUiState(); offline() }, preview = true)
            }
        }
    }

    private fun workState(now: Long, scenario: String): LearningUiState {
        if (scenario == "logged-out") return LearningUiState()
        if (scenario == "loading") return LearningUiState(loading = true)
        if (scenario == "error") return LearningUiState(error = "合成网络失败：尚未确认课程作业，请稍后重试。")
        val failure = ChxWorkCourse(44, 55, 66, "示例课程 · 数据结构", "https://mooc1.chaoxing.com/visit/stucoursemiddle")
        return LearningUiState(connected = scenario != "cached", hasSnapshot = true, fromCache = scenario == "cached",
            updatedAt = now - 300_000, courseCount = 4,
            works = listOf(
                Homework("synthetic-1", "高等数学", "第四章 · 多元函数微分学", "未交", now + 5 * 3600_000L, ""),
                Homework("synthetic-2", "大学英语", "Unit 5 · Reading & Writing", "未交", now + 36 * 3600_000L, ""),
                Homework("synthetic-3", "计算机基础", "实验三：数据与算法", "未交", 0, ""),
                Homework("synthetic-4", "思想道德与法治", "课程阅读与思考", "已完成", 0, ""),
            ), failedCourses = if (scenario == "partial") 1 else 0,
            failures = if (scenario == "partial") listOf(HomeworkFailure(failure, "合成页面结构异常，是否有作业仍未知。", false)) else emptyList())
    }
    private fun courseState(now: Long, scenario: String): CoursesUiState {
        if (scenario == "logged-out") return CoursesUiState()
        if (scenario == "loading") return CoursesUiState(loading = true)
        if (scenario == "error") return CoursesUiState(error = "合成网络失败：课程列表尚未确认。")
        return CoursesUiState(connected = scenario != "cached", hasSnapshot = true, fromCache = scenario == "cached", updatedAt = now - 300_000,
            progressError = "部分课程的任务点进度未知，可稍后刷新。", courses = listOf(
                ChxCourse("1", "2", "3", "高等数学", "示例课程 · 2026 秋季", "", ChxCourseProgress(48, 36, 75.0)),
                ChxCourse("4", "5", "6", "大学英语", "示例课程 · 综合英语", "", ChxCourseProgress(30, 28, 93.3)),
                ChxCourse("7", "8", "9", "计算机基础", "示例课程 · 实践与思考", "", null),
            ))
    }
}
