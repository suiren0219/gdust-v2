package com.menglimi.gdust.v2.ui.screens

import android.app.Activity
import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.menglimi.gdust.v2.data.AuthFailed
import com.menglimi.gdust.v2.data.CalendarInfo
import com.menglimi.gdust.v2.data.CourseBlock
import com.menglimi.gdust.v2.data.Repository
import com.menglimi.gdust.v2.data.TeachingEndpoints
import com.haooz.nexioui.overscroll.overScrollVertical
import com.haooz.nexioui.scrollhaptic.scrollEndHaptic
import com.menglimi.gdust.v2.ui.AppCard
import com.menglimi.gdust.v2.ui.AppUiTokens
import com.menglimi.gdust.v2.ui.LocalDs
import com.menglimi.gdust.v2.ui.NexioPageScaffold
import com.haooz.nexioui.button.ButtonDefaults
import com.haooz.nexioui.text.NexioText as Text
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withTimeoutOrNull
import java.io.IOException
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.UUID

private const val TERM_IMPORT_TIMEOUT_MS = 60_000L
private val termImportProcess = UUID.randomUUID().toString()

private data class PendingTermImport(
    val target: CalendarInfo,
    val sessionVersion: Long,
    val process: String = termImportProcess,
    // 0 表示等待独立登录页；认证耗时不计入课表读取的 60 秒期限。
    val readStartedAt: Long = 0L,
    val id: String = UUID.randomUUID().toString(),
)

// CalendarInfo 不是 Parcelable：逐字段保存，旋转后仍用发起登录时的目标学期。
private val PendingTermImportSaver = listSaver<PendingTermImport?, Any>(
    save = { request ->
        if (request == null) emptyList() else listOf(
            request.target.week, request.target.allWeek, request.target.startDate,
            request.target.year, request.target.semester, request.sessionVersion,
            request.process, request.readStartedAt, request.id,
        )
    },
    restore = { values ->
        if (values.size != 9) null else PendingTermImport(
            target = CalendarInfo(values[0] as Int, values[1] as Int, values[2] as String,
                values[3] as String, values[4] as String),
            sessionVersion = values[5] as Long,
            process = values[6] as String,
            readStartedAt = values[7] as Long,
            id = values[8] as String,
        )
    },
)

/** 保存过的网页入口只是候选，真正是否可用由本次读取确认。 */
private fun hasTermImportCandidate(repo: Repository): Boolean =
    !repo.store.webLogoutPending && TeachingEndpoints.isLandingPage(repo.store.loadWebImportPage()) &&
        repo.hasTeachingSession()

private fun termImportTarget(year: String, semester: String, start: String, weeks: String): CalendarInfo {
    val schoolYear = year.trim()
    require(schoolYear.matches(Regex("[0-9]{4}"))) { "请输入四位起始学年，如 2026。" }
    require(semester == "3" || semester == "12") { "请选择第一学期或第二学期。" }
    require(start.isNotBlank()) { "请选择第一周周一的日期。" }
    val date = runCatching { LocalDate.parse(start.trim()) }.getOrElse {
        error("开学日期格式不对，请重新选择，如 2026-08-31。")
    }
    require(date.dayOfWeek.value == 1) { "第一周的开始日期须为周一。" }
    val total = weeks.trim().toIntOrNull() ?: error("请输入学期总周数。")
    require(total in 1..60) { "学期总周数须为 1–60。" }
    val current = (ChronoUnit.DAYS.between(date, LocalDate.now()) / 7 + 1).coerceIn(1L, total.toLong()).toInt()
    return CalendarInfo(current, total, date.toString(), schoolYear, semester)
}

private fun CalendarInfo.termLabel(): String =
    "$year–${year.toInt() + 1} 学年 · ${if (semester == "3") "第一学期" else "第二学期"}"

/** 学校认证由独立 Activity 承载；本页尝试复用教务网页会话读取所选学期。 */
@Composable
fun TermImportScreen(
    repo: Repository,
    onBack: () -> Unit,
    onImported: (CalendarInfo, List<CourseBlock>) -> Unit,
) {
    val ds = LocalDs.current
    val context = LocalContext.current
    val focus = LocalFocusManager.current
    val isTablet = LocalConfiguration.current.screenWidthDp >= 600
    val pagePadding = if (isTablet) 20.dp else AppUiTokens.PagePadding
    val sessionVersion by repo.sessionVersion.collectAsState()
    val latestOnImported by rememberUpdatedState(onImported)
    val cached = remember(repo, sessionVersion) { repo.store.loadCalendar() }
    var year by rememberSaveable(sessionVersion) {
        mutableStateOf(cached?.year ?: LocalDate.now().let { if (it.monthValue < 8) it.year - 1 else it.year }.toString())
    }
    var semester by rememberSaveable(sessionVersion) { mutableStateOf(cached?.semester ?: "3") }
    var start by rememberSaveable(sessionVersion) {
        mutableStateOf(repo.store.termStartDate.ifBlank { cached?.startDate.orEmpty() })
    }
    var weeks by rememberSaveable(sessionVersion) {
        mutableStateOf((repo.store.termWeeks.takeIf { it > 0 } ?: cached?.allWeek ?: 20).toString())
    }
    var pending by rememberSaveable(stateSaver = PendingTermImportSaver) { mutableStateOf<PendingTermImport?>(null) }
    // 即使用户取消导入，也要等旧 Activity 返回后才能再发起，避免旧结果接管新目标。
    var awaitingLoginResult by rememberSaveable { mutableStateOf(false) }
    var importJob by remember(repo) { mutableStateOf<Job?>(null) }
    var candidate by remember(repo, sessionVersion) { mutableStateOf(hasTermImportCandidate(repo)) }
    var needsLogin by rememberSaveable(sessionVersion) { mutableStateOf(false) }
    var message by rememberSaveable(sessionVersion) { mutableStateOf("") }
    var isError by rememberSaveable(sessionVersion) { mutableStateOf(false) }
    val busy = pending != null || awaitingLoginResult

    fun currentAccount(request: PendingTermImport): Boolean =
        request.process == termImportProcess && repo.sessionVersion.value == request.sessionVersion &&
            repo.isCurrentSession(request.sessionVersion)

    fun interruptedMessage(request: PendingTermImport): String =
        if (request.process != termImportProcess) "上次导入已中断，原课表已保留，请重新导入。"
        else "账号已变更，本次导入已停止。请确认学期后重新导入。"

    fun cancelImport() {
        pending = null
        val job = importJob
        importJob = null
        job?.cancel()
        isError = false
        message = "已取消读取，原课表已保留。"
    }

    fun leaveScreen() {
        cancelImport()
        onBack()
    }

    BackHandler { leaveScreen() }

    val schoolLogin = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (!awaitingLoginResult) return@rememberLauncherForActivityResult
        awaitingLoginResult = false
        val request = pending?.takeIf { it.readStartedAt == 0L } ?: return@rememberLauncherForActivityResult
        if (!currentAccount(request)) {
            pending = null
            isError = true
            message = interruptedMessage(request)
        } else if (result.resultCode == Activity.RESULT_OK &&
            result.data?.getBooleanExtra(SchoolLoginActivity.EXTRA_TEACHING_READY, false) == true &&
            !repo.store.webLogoutPending
        ) {
            candidate = hasTermImportCandidate(repo)
            needsLogin = false
            isError = false
            message = ""
            pending = request.copy(readStartedAt = SystemClock.elapsedRealtime())
        } else {
            pending = null
            needsLogin = true
            isError = false
            message = "教务认证尚未完成，原课表已保留。可点「重新登录教务」继续。"
        }
    }

    // 会话序号只在同一进程内有效，进程重建后不自动消费上次账号的导入请求。
    LaunchedEffect(repo, sessionVersion) {
        val request = pending ?: return@LaunchedEffect
        if (!currentAccount(request)) {
            pending = null
            val job = importJob
            importJob = null
            job?.cancel()
            isError = true
            message = interruptedMessage(request)
        }
    }

    LaunchedEffect(repo, pending?.id, pending?.readStartedAt) {
        val request = pending?.takeIf { it.readStartedAt > 0L } ?: return@LaunchedEffect
        val job = currentCoroutineContext()[Job]
        importJob = job
        fun activeRequest(): Boolean = pending?.id == request.id && currentAccount(request)
        fun fail(text: String) {
            if (activeRequest()) {
                pending = null
                isError = true
                message = text
            }
        }
        try {
            currentCoroutineContext().ensureActive()
            if (!currentAccount(request)) {
                pending = null
                isError = true
                message = interruptedMessage(request)
                return@LaunchedEffect
            }
            val remaining = TERM_IMPORT_TIMEOUT_MS -
                (SystemClock.elapsedRealtime() - request.readStartedAt).coerceAtLeast(0L)
            val blocks = if (remaining > 0L) withTimeoutOrNull(remaining) {
                // 与仓库调用之间不切换线程，仓库会继续按同一账号版本保护保存操作。
                currentCoroutineContext().ensureActive()
                if (!activeRequest()) throw CancellationException("导入已停止")
                if (repo.store.webLogoutPending) throw AuthFailed(401, "需要重新认证")
                repo.importTermWithWebSession(request.target)
            } else null
            currentCoroutineContext().ensureActive()
            if (!activeRequest()) return@LaunchedEffect
            if (blocks == null) {
                fail("读取已超过 60 秒，已停止等待。原课表已保留，请检查网络后重试。")
                return@LaunchedEffect
            }
            pending = null
            candidate = hasTermImportCandidate(repo)
            needsLogin = false
            // 仓库只在完整读取成功后保存；本页不提前清空或覆盖原课表。
            latestOnImported(request.target, blocks)
        } catch (_: TimeoutCancellationException) {
            fail("教务响应超时，原课表已保留，请稍后重试。")
        } catch (cancelled: CancellationException) {
            // 配置重建会取消当前 Job，保留已保存的请求，以原期限恢复读取。
            if (currentCoroutineContext().isActive && pending?.id == request.id) {
                pending = null
                isError = true
                message = if (!currentAccount(request)) interruptedMessage(request)
                    else "本次读取已中断，原课表已保留，请重新导入。"
            }
            throw cancelled
        } catch (_: AuthFailed) {
            if (activeRequest()) needsLogin = true
            fail("教务登录已失效，需要重新认证。请点「重新登录教务」后继续，原课表已保留。")
        } catch (_: IOException) {
            fail("暂时连接不上教务，请检查网络后重试。原课表已保留。")
        } catch (_: Exception) {
            fail("暂时未能读到这个学期的课表，请确认学期后重试。原课表已保留。")
        } finally {
            if (importJob === job) importJob = null
        }
    }

    fun beginImport(forceLogin: Boolean) {
        if (pending != null || awaitingLoginResult || importJob?.isActive == true) return
        if (repo.sessionVersion.value != sessionVersion || !repo.isCurrentSession(sessionVersion)) {
            isError = true
            message = "账号已变更，请确认学期后重新导入。"
            return
        }
        val target = try {
            termImportTarget(year, semester, start, weeks)
        } catch (error: IllegalArgumentException) {
            isError = true
            message = error.message ?: "请检查学期设置。"
            return
        } catch (error: IllegalStateException) {
            isError = true
            message = error.message ?: "请检查学期设置。"
            return
        }
        focus.clearFocus()
        isError = false
        message = ""
        candidate = hasTermImportCandidate(repo)
        val request = PendingTermImport(target, sessionVersion)
        if (!forceLogin && !needsLogin && candidate) {
            pending = request.copy(readStartedAt = SystemClock.elapsedRealtime())
        } else {
            pending = request
            awaitingLoginResult = true
            try {
                schoolLogin.launch(SchoolLoginActivity.teachingIntent(context))
            } catch (_: Exception) {
                awaitingLoginResult = false
                pending = null
                isError = true
                message = "暂时无法打开学校登录页，请重试。原课表已保留。"
            }
        }
    }

    NexioPageScaffold(
        title = "教务学期导入",
        onBack = ::leaveScreen,
    ) { scaffoldPadding, _, _ ->
    Column(
        Modifier
            .fillMaxSize()
            .imePadding()
            .overScrollVertical()
            .scrollEndHaptic()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = pagePadding)
            .padding(top = scaffoldPadding.calculateTopPadding(), bottom = scaffoldPadding.calculateBottomPadding()),
    ) {
        Text(
            "选择学期与开学日期，读取成功后更新当前课表。",
            fontSize = 14.sp,
            color = ds.sub,
            modifier = Modifier.padding(horizontal = 4.dp).padding(bottom = 12.dp),
        )
        val request = pending
        if (request != null) {
            val reading = request.readStartedAt > 0L
            key(request.id, reading) {
                ScheduleLoadingCard(
                    title = if (reading) "读取学期课表" else "连接教务",
                    detail = if (reading) "正在读取所选学期的课程与周次，最多等待 60 秒。"
                        else "请在打开的学校页面完成认证，返回后会继续读取课表。",
                    onCancel = ::cancelImport,
                )
            }
            Spacer(Modifier.height(AppUiTokens.CardGap))
            AppCard(Modifier.fillMaxWidth(), insideMargin = PaddingValues(AppUiTokens.CardPadding)) {
                Text("本次导入", color = ds.sub, fontSize = 13.sp)
                Text(request.target.termLabel(), color = ds.text, fontSize = 17.sp, fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(top = 8.dp))
                Text("第一周周一 ${request.target.startDate} · 共 ${request.target.allWeek} 周",
                    color = ds.sub, fontSize = 13.sp, lineHeight = 20.sp, modifier = Modifier.padding(top = 8.dp))
            }
        } else {
            SectionHeader("学期设置")
            SettingGroup {
                SettingEditor {
                    NexioField(year, { year = it }, "起始学年", KeyboardType.Number, hint = "如 2026")
                    SegmentedChoice(listOf("3" to "第一学期", "12" to "第二学期"), semester, { semester = it })
                    TermDateField(value = start, onValueChange = { start = it })
                    NexioField(weeks, { weeks = it }, "学期总周数", KeyboardType.Number)
                }
            }
            if (candidate && !needsLogin) {
                Text("本机保存过教务登录，可先尝试读取。是否仍可用，会在读取时确认。",
                    color = ds.sub, fontSize = 13.sp, lineHeight = 20.sp,
                    modifier = Modifier.padding(bottom = 16.dp))
            }
            NexioActionButton(
                if (awaitingLoginResult) "等待登录页返回"
                else if (candidate && !needsLogin) "读取学期课表" else "登录教务并导入",
                enabled = !busy,
            ) { beginImport(forceLogin = false) }
            SettingActionText(
                text = "重新登录教务",
                enabled = !busy,
                onClick = { beginImport(forceLogin = true) },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
            if (awaitingLoginResult) {
                Text("关闭学校登录页后，即可重新开始导入。", color = ds.sub, fontSize = 13.sp, lineHeight = 20.sp)
            }
        }
        if (message.isNotBlank()) {
            Text(message, color = if (isError) ds.danger else ds.sub, fontSize = 13.sp, lineHeight = 20.sp,
                modifier = Modifier.padding(top = 12.dp).semantics { liveRegion = LiveRegionMode.Polite })
        }
        Spacer(Modifier.height(16.dp))
    }
    }
}
