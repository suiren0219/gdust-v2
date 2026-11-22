package com.menglimi.study.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.menglimi.study.BuildConfig
import com.menglimi.ksu.ui.EmptyCard
import com.menglimi.ksu.ui.Glyph
import com.menglimi.ksu.ui.HeroCard
import com.menglimi.ksu.ui.KCard
import com.menglimi.ksu.ui.KIcon
import com.menglimi.ksu.ui.MessageCard
import com.menglimi.ksu.ui.PageHeader
import com.menglimi.ksu.ui.SectionTitle
import com.menglimi.ksu.ui.StatusTag
import com.menglimi.ksu.ui.displayTime
import com.menglimi.study.learning.CoursesUiState
import com.menglimi.study.learning.LearningScope
import com.menglimi.study.learning.explanation
import com.menglimi.study.learning.libraryKey
import java.util.Locale

@Composable
fun CoursesScreen(
    state: CoursesUiState, onRefresh: () -> Unit, onLogin: () -> Unit,
    onOpenCourse: (String) -> Unit, onClearError: () -> Unit,
    modifier: Modifier = Modifier, preview: Boolean = false, onResumeRead: () -> Unit = onRefresh,
    diagnostic: String? = null,
    onMoveCourse: (String, String?) -> Unit = { _, _ -> },
    onCreateFolder: (String) -> Unit = {},
    onRemoveFolder: (String) -> Unit = {},
    onSelectScope: (LearningScope, String?) -> Unit = { _, _ -> },
    onImportCalendar: () -> Unit = {},
) {
    val clipboard = LocalClipboardManager.current
    var copiedError by rememberSaveable { mutableStateOf(false) }
    var classifying by rememberSaveable { mutableStateOf<String?>(null) }
    val classifyingCourse = classifying?.let { key -> state.courses.find { it.libraryKey() == key } }
    LazyColumn(modifier, contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 116.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { PageHeader("课程", if (preview) "合成预览 · 不访问网络或正式数据" else "课程与任务点，进度心中有数", onRefresh, state.loading) }
        item {
            HeroCard(listOf("任务点", if (state.fromCache) "已缓存" else if (state.connected) "已连接" else "待读取"),
                if (state.hasSnapshot) "${state.courses.size} 门课程" else if (state.loading) "正在读取课程" else "我的课程",
                if (state.hasSnapshot) "最近读取 ${displayTime(state.updatedAt)} · ${state.courses.count { it.progress == null }} 门进度未知"
                else "读取学习通当前默认课程页，任务点进度与作业分别更新。", Glyph.CHART) {
                if (!state.hasSnapshot && !state.loading) Button(onClick = onRefresh) { Text("读取课程") }
                if (!state.connected && !state.loading) Button(onClick = onLogin) { Text("登录学习通") }
            }
        }
        if (state.hasSnapshot || state.library.folders.isNotEmpty() || state.library.terms.isNotEmpty()) item {
            CourseLibraryCard(state.library, state.courses.size, state.visible.size,
                onSelectScope, onCreateFolder, onRemoveFolder, onImportCalendar)
        }
        if (state.readControl.active) item { ReadControlCard(state.readControl, state.loading, onResumeRead, onOpenCourse) }
        state.error?.let { error -> item {
            MessageCard(error, onClearError)
            Row(Modifier.fillMaxWidth().padding(top = 2.dp), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = {
                    clipboard.setText(AnnotatedString(diagnostic ?: "学习助手 ${BuildConfig.VERSION_NAME}\n$error"))
                    copiedError = true
                }) { Text(if (copiedError) "诊断已复制" else "复制本次诊断") }
            }
        } }
        state.progressError?.let { item { MessageCard(it) } }
        if (state.fromCache) item {
            KCard(Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.secondaryContainer) {
                Text("正在展示上次读取的课程与进度，刷新后确认最新状态。", fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSecondaryContainer)
            }
        }
        item { SectionTitle("课程列表", if (state.hasSnapshot) "${state.visible.size} 门 · ${state.library.label}" else "尚未读取") }
        if (state.courses.isEmpty()) item {
            EmptyCard(if (state.loading) "正在读取课程" else "尚未确认课程列表",
                "未知页面、网络失败或登录失效不会被当成没有课程。请点右上角刷新，或先完成官方网页登录。", Glyph.BOOK)
        } else if (state.visible.isEmpty()) item {
            EmptyCard("当前范围没有课程",
                "课程页共 ${state.courses.size} 门。切回「全部课程」，或在课程卡上点「归类」把课程放进这个文件夹。", Glyph.SEARCH)
        }
        items(state.visible, key = { it.info }) { course ->
            KCard(Modifier.fillMaxWidth(), onClick = { onOpenCourse(course.url) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(course.name, fontSize = 18.sp, lineHeight = 26.sp, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f).padding(end = 12.dp))
                    KIcon(Glyph.ARROW, Modifier.size(18.dp), MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (course.description.isNotBlank()) Text(course.description, fontSize = 12.sp, lineHeight = 20.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
                Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(state.library.explanation(course), fontSize = 12.sp, lineHeight = 18.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                    TextButton(onClick = { classifying = course.libraryKey() }) { Text("归类") }
                }
                val progress = course.progress
                Row(Modifier.fillMaxWidth().padding(top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("任务点", fontSize = 13.sp, modifier = Modifier.weight(1f))
                    StatusTag(if (progress == null) "进度未知" else String.format(Locale.getDefault(), "%.1f%%", progress.jobRate))
                }
                if (progress != null) {
                    LinearProgressIndicator(progress = { (progress.jobRate / 100).toFloat() },
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 8.dp))
                    Text("${progress.jobFinishCount} / ${progress.jobCount} 个任务点 · 百分比由学习通提供",
                        fontSize = 12.sp, lineHeight = 19.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else Text("接口尚未提供可识别的统计，不根据作业数量推算。", fontSize = 12.sp, lineHeight = 19.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 10.dp))
            }
        }
        item { Text("课程分类只保存在本机，不影响学习通原网页。加入时间以课程页明确标注为准；页面没有该字段时不会用开课时间或首次发现时间代替。任务点不等于作业数量，最终进度以学习通原网页为准。",
            fontSize = 12.sp, lineHeight = 19.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)) }
    }
    classifyingCourse?.let { course ->
        CourseClassifyDialog(course, state.library, onMoveCourse, onCreateFolder) { classifying = null }
    }
}
