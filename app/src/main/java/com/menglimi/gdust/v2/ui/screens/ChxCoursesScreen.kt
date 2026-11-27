package com.menglimi.gdust.v2.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.haooz.nexioui.progressindicator.LinearProgressIndicator as NexioLinearProgressIndicator
import com.haooz.nexioui.progressindicator.ProgressIndicatorDefaults as NexioProgressIndicatorDefaults
import com.haooz.nexioui.text.NexioText as Text
import com.menglimi.gdust.v2.data.AuthFailed
import com.menglimi.gdust.v2.data.ChxCourse
import com.menglimi.gdust.v2.data.ChxCourseOverview
import com.menglimi.gdust.v2.data.ChxCourseReadException
import com.menglimi.gdust.v2.data.ChxCourseProgress
import com.menglimi.gdust.v2.data.Repository
import com.menglimi.gdust.v2.ui.AppCard
import com.menglimi.gdust.v2.ui.AppUiTokens
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
    offlineOverview: ChxCourseOverview? = null,
) {
    val ds = LocalDs.current
    val scope = rememberCoroutineScope()
    val offlineMode = offlineOverview != null
    val sessionVersion by repo.sessionVersion.collectAsState()
    val chxVersion by repo.chxSessionVersion.collectAsState()
    val requestPause = if (offlineMode) null else repo.chxRequestPause.collectAsState().value
    val controller = remember(repo, scope, chxVersion, offlineOverview) {
        ChxCoursesController(
            scope = scope,
            load = {
                if (offlineMode) checkNotNull(offlineOverview) { "离线课程演示数据缺失" }
                else repo.chxCourses()
            },
            isCurrentSession = { version -> offlineMode || repo.sessionVersion.value == version },
        )
    }
    val loadedState by controller.state.collectAsState()
    // 新会话的首帧也不展示上一账号的数据，不等待 LaunchedEffect 才隐藏。
    val state = if (offlineMode || loadedState.sessionVersion == sessionVersion) loadedState
        else ChxCoursesUiState(sessionVersion)
    LaunchedEffect(controller, sessionVersion) {
        controller.changeSession(sessionVersion)
        if (!offlineMode) controller.restore(repo.loadChxCourseSnapshot())
        controller.refresh()
    }
    DisposableEffect(controller) { onDispose { controller.cancel() } }

    SettingSubPage("课程与任务进度", if (offlineOverview != null) "离线演示 · 虚构课程数据" else "学习通 · 当前课程页", onBack) {
        if (offlineOverview != null) {
            Text("离线演示 · 不访问学校账户、Cookie 或网络服务。", fontSize = 13.sp, color = ds.sub)
            Spacer(Modifier.height(8.dp))
        }
        Text(
            "任务点进度与作业待办分别统计。",
            fontSize = 13.sp, lineHeight = 20.sp, color = ds.sub,
        )
        Spacer(Modifier.height(16.dp))
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            NexioActionButton(
                text = if (state.loading) "重新刷新" else "刷新课程",
                onClick = controller::refresh,
            )
        }
        if (state.loading) {
            Spacer(Modifier.height(12.dp))
            NexioLinearProgressIndicator(colors = NexioProgressIndicatorDefaults.progressIndicatorColors(
                foregroundColor = ds.accent, backgroundColor = ds.breakBar,
            ))
            Text("正在读取课程与任务点进度…", fontSize = 13.sp, color = ds.sub,
                modifier = Modifier.padding(top = 8.dp))
        }
        requestPause?.let { stopped ->
            Text("学习通读取已暂停。" + stopped.exception().message.orEmpty(), color = ds.danger,
                fontSize = 13.sp, lineHeight = 20.sp, modifier = Modifier.padding(top = 8.dp))
        }
        if (state.authRequired || requestPause != null) {
            Spacer(Modifier.height(8.dp))
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                NexioActionButton(
                    if (requestPause != null) "打开网页验证并检查恢复" else "重新连接学习通",
                    enabled = !offlineMode,
                    onClick = {
                        if (requestPause != null) onOpenCourse(com.menglimi.gdust.v2.data.ChxSso.HOME)
                        else onLogin()
                    },
                )
            }
        }
        state.error?.let { message ->
            Spacer(Modifier.height(AppUiTokens.CardGap))
            AppCard(Modifier.fillMaxWidth(), insideMargin = PaddingValues(AppUiTokens.CardPadding)) {
                Text(if (state.authRequired) "需要重新连接" else "课程读取失败", color = ds.danger,
                    fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                Text(if (state.overview == null) "可重新连接或刷新后再试。" else "已保留上次读取的课程。",
                    fontSize = 13.sp, color = ds.sub, modifier = Modifier.padding(top = 8.dp))
                ChxReadDetails(message)
            }
        }
        val overview = state.overview
        if (overview != null) {
            if (state.stale) {
                Text("以下为上次读取的结果，刷新尚未成功。", color = ds.danger, fontSize = 13.sp,
                    modifier = Modifier.padding(top = 8.dp))
            }
            val time = remember(overview.loadedAt) {
                SimpleDateFormat("MM-dd HH:mm", Locale.CHINA).format(Date(overview.loadedAt))
            }
            Text("最近读取 $time", fontSize = 13.sp, color = ds.sub, modifier = Modifier.padding(top = 12.dp))
            overview.progressError?.let { message ->
                val missing = overview.courses.count { it.progress == null }
                Spacer(Modifier.height(AppUiTokens.CardGap))
                AppCard(Modifier.fillMaxWidth(), insideMargin = PaddingValues(AppUiTokens.CardPadding)) {
                    Text("进度读取未完成", color = ds.danger, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                    Text("已读取 ${overview.courses.size - missing} 门 · 未读取 $missing 门",
                        fontSize = 13.sp, color = ds.sub, modifier = Modifier.padding(top = 8.dp))
                    ChxReadDetails(message)
                }
            }
            SectionHeader("当前课程页 · ${overview.courses.size} 门")
            Column(verticalArrangement = Arrangement.spacedBy(AppUiTokens.CardGap)) {
                overview.courses.forEach { course -> ChxCourseItem(course, onOpenCourse) }
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
    AppCard(
        modifier = Modifier.fillMaxWidth(),
        insideMargin = PaddingValues(AppUiTokens.CardPadding),
        onClick = { onOpenCourse(course.url) },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(course.name, fontSize = 18.sp, lineHeight = 25.sp,
                fontWeight = FontWeight.SemiBold, color = ds.text)
            if (course.description.isNotBlank()) {
                Text(course.description, fontSize = 13.sp, lineHeight = 20.sp, color = ds.sub)
            }
            val progress = course.progress
            if (progress == null) {
                Text("任务点进度未知", fontSize = 13.sp, color = ds.sub)
            } else {
                val percent = BigDecimal.valueOf(progress.jobRate).stripTrailingZeros().toPlainString()
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("任务点完成率", fontSize = 13.sp, color = ds.sub)
                    Text("$percent%", fontSize = 34.sp, lineHeight = 40.sp,
                        fontWeight = FontWeight.Bold, color = ds.text)
                    Text("任务点 ${progress.jobFinishCount} / ${progress.jobCount}", fontSize = 13.sp, color = ds.sub)
                }
                NexioLinearProgressIndicator(
                    progress = (progress.jobRate / 100.0).toFloat(),
                    modifier = Modifier.fillMaxWidth(),
                    colors = NexioProgressIndicatorDefaults.progressIndicatorColors(
                        foregroundColor = ds.accent, backgroundColor = ds.breakBar,
                    ),
                )
            }
            Text(
                if (course.url.startsWith("https://example.invalid/")) "离线演示 · 不打开网页"
                else "点按课程进入学习通网页",
                fontSize = 13.sp,
                color = ds.sub,
            )
        }
    }
}

internal fun chxCoursesDemoOverview(): ChxCourseOverview = ChxCourseOverview(
    courses = listOf(
        ChxCourse(
            clazzId = "offline-class-1",
            courseId = "offline-course-1",
            curPersonId = "offline-user-1",
            name = "离线演示：数据结构",
            description = "仅用于本机界面验收的虚构课程",
            url = "https://example.invalid/course",
            progress = ChxCourseProgress(10, 8, 80.0),
        ),
    ),
)

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

    fun restore(overview: ChxCourseOverview?) {
        val version = mutableState.value.sessionVersion ?: return
        if (overview == null || !isCurrentSession(version) || mutableState.value.overview != null) return
        mutableState.value = ChxCoursesUiState(version, overview, stale = true,
            authRequired = overview.progressAuthRequired)
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
