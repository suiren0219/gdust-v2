package com.menglimi.study

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.menglimi.ksu.ui.KsuTheme
import com.menglimi.study.learning.LearningWebActivity
import com.menglimi.study.ui.StudyRoot
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val selectedTab = mutableIntStateOf(0)
    private val app get() = application as StudyApplication
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        selectedTab.intValue = (savedInstanceState?.getInt("tab") ?: 0).coerceIn(0, 2)
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) { app.learning.reload() }
        }
        setContent {
            val appearance by app.appearance.state.collectAsStateWithLifecycle()
            val work by app.learning.state.collectAsStateWithLifecycle()
            val courses by app.learning.courses.collectAsStateWithLifecycle()
            val diagnostic by app.learning.diagnostic.collectAsStateWithLifecycle()
            KsuTheme(appearance) {
                StudyRoot(tab = selectedTab.intValue, onTab = { selectedTab.intValue = it },
                    appearance = appearance, work = work, courses = courses, diagnostic = diagnostic,
                    onAppearance = app.appearance::update,
                    onLogin = { startActivity(Intent(this, LearningWebActivity::class.java)
                        .putExtra("courseProgress", selectedTab.intValue == 1)) },
                    onWorkRefresh = { action { app.learning.refresh() } },
                    onResumeRead = { action { app.learning.resumeReading() } },
                    onRetryCourse = { key -> action { app.learning.retryCourse(key) } },
                    onCoursesRefresh = { action { app.learning.refreshCourses() } },
                    onOpenPage = { url -> startActivity(Intent(this, LearningWebActivity::class.java).putExtra("url", url)
                        .putExtra("courseProgress", selectedTab.intValue == 1)) },
                    onWorkClearError = app.learning::clearError,
                    onCoursesClearError = app.learning::clearCoursesError,
                    onDisconnect = { action { app.learning.disconnect() } },
                    onClearCache = { action { app.learning.clearCache() } },
                )
            }
        }
    }
    override fun onSaveInstanceState(outState: Bundle) {
        outState.putInt("tab", selectedTab.intValue)
        super.onSaveInstanceState(outState)
    }
    private fun action(block: suspend () -> Unit) {
        lifecycleScope.launch {
            try { block() } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { Toast.makeText(this@MainActivity, "操作暂未完成，请稍后重试", Toast.LENGTH_SHORT).show() }
        }
    }
}
