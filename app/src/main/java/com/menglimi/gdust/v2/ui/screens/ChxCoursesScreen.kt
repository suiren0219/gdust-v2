package com.menglimi.gdust.v2.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.menglimi.gdust.v2.data.AuthFailed
import com.menglimi.gdust.v2.data.ChxCourse
import com.menglimi.gdust.v2.data.ChxCourseOverview
import com.menglimi.gdust.v2.data.ChxCourseReadException
import com.menglimi.gdust.v2.data.Repository
import com.menglimi.gdust.v2.ui.LocalDs
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.LinearProgressIndicator
import top.yukonga.miuix.kmp.basic.ProgressIndicatorDefaults
import top.yukonga.miuix.kmp.basic.Text
import java.math.BigDecimal
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ChxCoursesScreen(
    repo: Repository,
    onBack: () -> Unit,
    onLogin: () -> Unit,
    onOpenCourse: (String) -> Unit,
) {
    val ds = LocalDs.current
    val scope = rememberCoroutineScope()
    val sessionVersion by repo.sessionVersion.collectAsState()
    val chxVersion by repo.chxSessionVersion.collectAsState()
    val controller = remember(repo, scope, chxVersion) {
        ChxCoursesController(scope, repo::chxCourses) { version -> repo.sessionVersion.value == version }
    }
    val loadedState by controller.state.collectAsState()
    // 新会话的首帧也不展示上一账号的数据，不等待 LaunchedEffect 才隐藏。
    val state = if (loadedState.sessionVersion == sessionVersion) loadedState else ChxCoursesUiState(sessionVersion)
    LaunchedEffect(controller, sessionVersion) {
        controller.changeSession(sessionVersion)
        controller.refresh()
    }
    DisposableEffect(controller) { onDispose { controller.cancel() } }

    SettingSubPage("学习通课程", "当前课程页 · 任务点进度", onBack) {
        Text(
            "显示学习通当前课程页；其他分页与文件夹请在网页查看。任务点进度不代表作业待办。",
            fontSize = 13.sp, lineHeight = 20.sp, color = ds.sub,
        )
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = controller::refresh,
            modifier = Modifier.fillMaxWidth(), minHeight = 48.dp,
            colors = ButtonDefaults.buttonColorsPrimary(),
        ) {
            Text(if (state.loading) "重新刷新" else "刷新课程", fontSize = 16.sp, fontWeight = FontWeight.Medium)
        }
        if (state.loading) {
            Spacer(Modifier.height(12.dp))
            LinearProgressIndicator(colors = ProgressIndicatorDefaults.progressIndicatorColors(
                foregroundColor = ds.accent, backgroundColor = ds.cardStroke,
            ))
            Text("正在读取课程与任务点进度…", fontSize = 12.sp, color = ds.sub,
                modifier = Modifier.padding(top = 8.dp))
        }
        if (state.authRequired) {
            Spacer(Modifier.height(8.dp))
            Button(onClick = onLogin, modifier = Modifier.fillMaxWidth(), minHeight = 48.dp) {
                Text("登录学习通", fontSize = 16.sp)
            }
        }
        state.error?.let { message ->
            Text(message, color = ds.danger, fontSize = 13.sp, lineHeight = 20.sp,
                modifier = Modifier.padding(top = 12.dp))
        }
        val overview = state.overview
        if (overview != null) {
            if (state.stale) {
                Text("以下为上次读取的结果，刷新尚未成功。", color = ds.danger, fontSize = 12.sp,
                    modifier = Modifier.padding(top = 8.dp))
            }
            val time = remember(overview.loadedAt) {
                SimpleDateFormat("MM-dd HH:mm", Locale.CHINA).format(Date(overview.loadedAt))
            }
            Text("最近读取 $time", fontSize = 12.sp, color = ds.sub, modifier = Modifier.padding(top = 12.dp))
            overview.progressError?.let { message ->
                Text(message, color = ds.danger, fontSize = 13.sp, lineHeight = 20.sp,
                    modifier = Modifier.padding(top = 8.dp))
            }
            SectionHeader("当前课程页 · ${overview.courses.size} 门")
            SettingGroup {
                overview.courses.forEachIndexed { index, course ->
                    if (index > 0) SettingDivider()
                    ChxCourseItem(course, onOpenCourse)
                }
            }
        } else if (!state.loading && state.error == null) {
            Text("尚未读取课程，请刷新。", fontSize = 13.sp, color = ds.sub,
                modifier = Modifier.padding(top = 12.dp))
        }
    }
}

@Composable
private fun ChxCourseItem(course: ChxCourse, onOpenCourse: (String) -> Unit) {
    val ds = LocalDs.current
    SettingOption(title = course.name, subtitle = course.description.takeIf { it.isNotBlank() },
        onClick = { onOpenCourse(course.url) })
    Column(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        val progress = course.progress
        if (progress == null) {
            Text("任务点进度未知", fontSize = 13.sp, color = ds.sub)
        } else {
            val percent = BigDecimal.valueOf(progress.jobRate).stripTrailingZeros().toPlainString()
            Text("任务点 ${progress.jobFinishCount} / ${progress.jobCount} · $percent%", fontSize = 13.sp, color = ds.sub)
            LinearProgressIndicator(progress = (progress.jobRate / 100.0).toFloat(),
                colors = ProgressIndicatorDefaults.progressIndicatorColors(
                    foregroundColor = ds.accent, backgroundColor = ds.cardStroke,
                ))
        }
        Text("点按课程进入学习通网页", fontSize = 12.sp, color = ds.sub)
    }
}

internal data class ChxCoursesUiState(
    val sessionVersion: Long? = null,
    val overview: ChxCourseOverview? = null,
    val loading: Boolean = false,
    val error: String? = null,
    val stale: Boolean = false,
    val authRequired: Boolean = false,
)

/** 在页面协程作用域中使用；序号与会话版本同时隔离取消后仍返回的旧结果。 */
internal class ChxCoursesController(
    private val scope: CoroutineScope,
    private val load: suspend () -> ChxCourseOverview,
    private val isCurrentSession: (Long) -> Boolean,
) {
    private val mutableState = MutableStateFlow(ChxCoursesUiState())
    val state = mutableState.asStateFlow()
    private var job: Job? = null
    private var requestNumber = 0L

    fun changeSession(version: Long) {
        if (mutableState.value.sessionVersion == version) return
        cancel()
        mutableState.value = ChxCoursesUiState(sessionVersion = version)
    }

    fun refresh() {
        val version = mutableState.value.sessionVersion ?: return
        if (!isCurrentSession(version)) return
        val number = ++requestNumber
        job?.cancel()
        mutableState.value = mutableState.value.copy(loading = true, error = null)
        job = scope.launch(start = CoroutineStart.LAZY) {
            try {
                val result = load()
                currentCoroutineContext().ensureActive()
                if (isCurrent(number, version)) {
                    if (result.courses.isEmpty()) throw ChxCourseReadException("未能识别当前课程页，请稍后重试。")
                    mutableState.value = ChxCoursesUiState(version, result, authRequired = result.progressAuthRequired)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                currentCoroutineContext().ensureActive()
                if (isCurrent(number, version)) {
                    mutableState.value = mutableState.value.copy(
                        error = when (error) {
                            is AuthFailed -> "学习通会话已失效，请重新登录。"
                            is ChxCourseReadException -> error.message
                            else -> "课程读取失败，请检查网络后重试。"
                        },
                        stale = mutableState.value.overview != null,
                        authRequired = error is AuthFailed || mutableState.value.authRequired,
                    )
                }
            } finally {
                if (isCurrent(number, version)) mutableState.value = mutableState.value.copy(loading = false)
            }
        }.also { it.start() }
    }

    fun cancel() {
        ++requestNumber
        job?.cancel()
        job = null
        mutableState.value = mutableState.value.copy(loading = false)
    }

    private fun isCurrent(number: Long, version: Long): Boolean = requestNumber == number &&
        mutableState.value.sessionVersion == version && isCurrentSession(version)
}
