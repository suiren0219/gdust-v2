package com.menglimi.study

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.menglimi.ksu.ui.KsuTheme
import com.menglimi.study.learning.LearningWebActivity
import com.menglimi.study.ui.StudyRoot
import java.io.ByteArrayOutputStream
import java.io.InputStream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 与 LearningCalendarImport 的上限一致：只读校历所需的几 KB，不整读任意大文件。 */
private const val MAX_CALENDAR_BYTES = 4 * 1024 * 1024

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
            // 只读取校历字段；备份里的账号、绑定等其它内容不解析、不转存。
            val pickCalendar = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
                if (uri == null) return@rememberLauncherForActivityResult
                libraryAction("已导入校历") {
                    val text = withContext(Dispatchers.IO) {
                        runCatching {
                            contentResolver.openInputStream(uri)?.use { stream -> stream.readLimitedText(MAX_CALENDAR_BYTES) }
                        }.getOrNull()
                    }
                    if (text == null) "无法读取所选文件，请确认它是主应用导出的备份。" else app.learning.importCalendar(text)
                }
            }
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
                    onMoveCourse = { key, folderId -> libraryAction { app.learning.moveCourse(key, folderId) } },
                    onCreateFolder = { name -> libraryAction("已新建文件夹") { app.learning.createFolder(name, null) } },
                    onRemoveFolder = { folderId -> libraryAction("已删除文件夹") { app.learning.removeFolder(folderId) } },
                    onSelectScope = { scope, id -> libraryAction { app.learning.selectCourseScope(scope, id) } },
                    onImportCalendar = { pickCalendar.launch(arrayOf("*/*")) },
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
    /** 分类操作返回非空文本表示没有保存成功；成功时用 done 给一句轻提示。 */
    private fun libraryAction(done: String? = null, block: suspend () -> String?) {
        lifecycleScope.launch {
            try {
                val message = block()
                (message ?: done)?.let { Toast.makeText(this@MainActivity, it, Toast.LENGTH_SHORT).show() }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                Toast.makeText(this@MainActivity, failure.message ?: "课程分类未能保存，请稍后重试",
                    Toast.LENGTH_LONG).show()
            }
        }
    }
}

private fun InputStream.readLimitedText(limit: Int): String {
    val bytes = ByteArrayOutputStream()
    val chunk = ByteArray(8 * 1024)
    while (bytes.size() <= limit) {
        val read = read(chunk)
        if (read < 0) break
        bytes.write(chunk, 0, read)
    }
    require(bytes.size() <= limit) { "校历文件过大" }
    return String(bytes.toByteArray(), Charsets.UTF_8)
}
