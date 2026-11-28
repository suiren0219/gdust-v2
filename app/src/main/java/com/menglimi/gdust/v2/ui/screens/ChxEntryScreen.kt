package com.menglimi.gdust.v2.ui.screens

import android.app.Activity
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.menglimi.gdust.v2.data.AuthFailed
import com.menglimi.gdust.v2.data.ChxCourseReadException
import com.menglimi.gdust.v2.data.ChxSso
import com.menglimi.gdust.v2.data.ChxWebSession
import com.menglimi.gdust.v2.data.Repository
import com.menglimi.gdust.v2.ui.AppCard
import com.menglimi.gdust.v2.ui.AppUiTokens
import com.menglimi.gdust.v2.ui.LocalDs
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.LinearProgressIndicator
import top.yukonga.miuix.kmp.basic.ProgressIndicatorDefaults
import top.yukonga.miuix.kmp.basic.Text
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** onConnect 可接学校统一登录；外部登录返回后由调用方重建页面以重新验证。 */
@Composable
fun ChxEntryScreen(
    repo: Repository,
    onBack: () -> Unit,
    onCourses: () -> Unit = {},
    onConnect: (() -> Unit)? = null,
    onNotifications: (() -> Unit)? = null,
) {
    val ds = LocalDs.current
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    val accountVersion by repo.sessionVersion.collectAsState()
    val chxVersion by repo.chxSessionVersion.collectAsState()
    val requestPause by repo.chxRequestPause.collectAsState()
    var saved by remember(repo, accountVersion, chxVersion) { mutableStateOf(repo.store.hasChxSession) }
    var checkResult by remember(repo, accountVersion, chxVersion) { mutableStateOf<Boolean?>(null) }
    var checking by remember(repo, accountVersion, chxVersion) { mutableStateOf(false) }
    var syncing by remember(repo, accountVersion, chxVersion) { mutableStateOf(false) }
    var authRequired by rememberSaveable(accountVersion, chxVersion) { mutableStateOf(false) }
    var message by rememberSaveable(accountVersion) { mutableStateOf("") }
    var isError by rememberSaveable(accountVersion) { mutableStateOf(false) }
    var syncMessage by rememberSaveable(accountVersion, chxVersion) { mutableStateOf("") }
    var syncError by rememberSaveable(accountVersion, chxVersion) { mutableStateOf(false) }
    var helpExpanded by rememberSaveable { mutableStateOf(false) }
    var logoutPending by rememberSaveable { mutableStateOf(false) }
    var clearPending by rememberSaveable { mutableStateOf(false) }
    var serverConfirmed by rememberSaveable { mutableStateOf(false) }
    var localCleared by rememberSaveable { mutableStateOf<Boolean?>(null) }
    val busy = checking || syncing || logoutPending || clearPending

    fun isCurrent(account: Long, learning: Long): Boolean =
        repo.sessionVersion.value == account && repo.chxSessionVersion.value == learning && !repo.store.webLogoutPending

    fun checkSession() {
        if (busy) return
        val account = repo.sessionVersion.value
        val learning = repo.chxSessionVersion.value
        checking = true
        checkResult = null
        isError = false
        message = "正在验证连接…"
        scope.launch {
            try {
                val alive = repo.chxSessionAlive()
                if (!isCurrent(account, learning)) return@launch
                checkResult = alive
                saved = repo.store.hasChxSession
                isError = alive != true
                message = when (alive) {
                    true -> if (authRequired) "作业读取仍需重新连接。" else ""
                    false -> "连接已失效，请重新登录。"
                    null -> "暂时无法验证，请稍后重新检查。"
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                if (isCurrent(account, learning)) {
                    isError = true
                    message = (failure as? ChxCourseReadException)?.message ?: "暂时无法验证，请稍后重新检查。"
                }
            } finally {
                checking = false
            }
        }
    }

    BackHandler { onBack() }

    // 主入口注入的学校登录返回后重建本页；只检查已有候选，首次打开不发起登录。
    LaunchedEffect(repo, accountVersion) {
        if (saved && !logoutPending && !clearPending && localCleared == null) checkSession()
    }

    // 先等待学校网页结果，再清本地会话；失败和重建后仍保留本地重试入口。
    LaunchedEffect(clearPending) {
        if (!clearPending) return@LaunchedEffect
        checkResult = null
        syncMessage = ""
        val cleared = try {
            repo.clearChxSession()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            false
        }
        localCleared = cleared
        saved = repo.store.hasChxSession
        clearPending = false
        isError = !cleared || !serverConfirmed
        message = when {
            !cleared -> "本机清理未完成，请在连接与帮助中重试。"
            !serverConfirmed -> "本机已清理，学校退出未确认。"
            else -> "本机已清理，学校已确认退出。"
        }
    }

    val webLogin = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        // Activity 返回或存在 Cookie 都不能证明登录成功。
        saved = repo.store.hasChxSession
        checkSession()
    }
    val casLogout = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        logoutPending = false
        serverConfirmed = result.resultCode == Activity.RESULT_OK &&
            result.data?.getBooleanExtra(ChxWebActivity.EXTRA_LOGOUT_CONFIRMED, false) == true
        clearPending = true
        message = "正在清理本机连接…"
    }

    fun connect(direct: Boolean = false) {
        if (busy) return
        checkResult = null
        localCleared = null
        serverConfirmed = false
        isError = false
        message = "完成登录后将自动验证连接。"
        try {
            if (!direct && onConnect != null) onConnect()
            else webLogin.launch(Intent(context, ChxWebActivity::class.java)
                .putExtra(ChxWebActivity.EXTRA_URL, if (direct) ChxSso.DIRECT_LOGIN else ChxSso.fyssoUrl))
        } catch (_: Exception) {
            isError = true
            message = "暂时无法打开登录，请重试。"
        }
    }

    val connection = chxConnectionPresentation(saved, checkResult, checking, authRequired)
    val canSync = saved && checkResult != false && !authRequired && !busy && requestPause == null
    Column(
        Modifier.fillMaxSize().background(Color.Transparent).statusBarsPadding().navigationBarsPadding()
            .verticalScroll(rememberScrollState()).padding(horizontal = AppUiTokens.PagePadding),
    ) {
        Spacer(Modifier.height(20.dp))
        PageTitle(title = "学习通", onBack = onBack)
        AppCard(
            modifier = Modifier.fillMaxWidth(),
            insideMargin = PaddingValues(AppUiTokens.CardPadding),
            color = if (connection.verified) ds.successTint else ds.card,
        ) {
            val account = repo.store.jobNumber
            Text(
                if (account.isBlank()) "尚未保存学校账户" else "当前学校账户 · " +
                    if (account.length > 4) "****${account.takeLast(4)}" else "****",
                fontSize = 13.sp, color = ds.sub,
            )
            Text(
                when {
                    logoutPending -> "正在退出"
                    clearPending -> "正在清理"
                    else -> connection.title
                },
                fontSize = 26.sp, fontWeight = FontWeight.SemiBold, color = ds.text,
                modifier = Modifier.padding(top = 8.dp),
            )
            if (saved && repo.store.chxCookieAt > 0) {
                Text("连接保存于 ${SimpleDateFormat("MM-dd HH:mm", Locale.CHINA).format(Date(repo.store.chxCookieAt))}",
                    fontSize = 13.sp, color = ds.sub, modifier = Modifier.padding(top = 6.dp))
            }
            if (message.isNotBlank()) {
                Text(message, fontSize = 13.sp, lineHeight = 20.sp,
                    color = if (isError || authRequired) ds.danger else ds.sub,
                    modifier = Modifier.padding(top = 8.dp))
            }
        }
        requestPause?.let { stopped ->
            Spacer(Modifier.height(AppUiTokens.CardGap))
            AppCard(Modifier.fillMaxWidth(), insideMargin = PaddingValues(AppUiTokens.CardPadding)) {
                Text("学习通读取已暂停", color = ds.danger, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                Text(stopped.exception().message.orEmpty(), fontSize = 13.sp, lineHeight = 20.sp,
                    color = ds.sub, modifier = Modifier.padding(top = 8.dp))
                Text("请在网页完成验证；冷却结束后，点网页工具栏的“完成验证，检查恢复”。",
                    fontSize = 13.sp, lineHeight = 20.sp, color = ds.sub, modifier = Modifier.padding(top = 8.dp))
            }
            ChxActionButton("打开学习通网页处理", !busy, "完成后明确检查原受阻业务") {
                webLogin.launch(Intent(context, ChxWebActivity::class.java)
                    .putExtra(ChxWebActivity.EXTRA_URL, ChxSso.HOME))
            }
        }
        if (!connection.verified) {
            Spacer(Modifier.height(AppUiTokens.CardGap))
            ChxActionButton("连接学习通", !busy, "通过学校统一登录连接") { connect() }
        }
        Spacer(Modifier.height(AppUiTokens.CardGap))
        ChxActionButton("课程与任务进度", !busy, "查看课程及任务点完成情况", onCourses)
        Spacer(Modifier.height(AppUiTokens.CardGap))
        ChxActionButton(
            if (syncing) "正在同步作业…" else "同步作业",
            canSync,
            when {
                syncing -> "逐门读取课程，请稍候"
                !saved || checkResult == false || authRequired -> "连接后将作业更新到待办"
                else -> "更新到待办，保留已有记录"
            },
        ) {
            val account = repo.sessionVersion.value
            val learning = repo.chxSessionVersion.value
            syncing = true
            syncError = false
            syncMessage = ""
            scope.launch {
                try {
                    val result = repo.chxSyncWorkTodos()
                    if (isCurrent(account, learning)) {
                        syncMessage = result
                        if (chxWorkSyncSummary(result).authRequired) {
                            authRequired = true
                            checkResult = null
                        }
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (e: Exception) {
                    if (isCurrent(account, learning)) {
                        syncError = true
                        syncMessage = chxWorkSyncError(e)
                        if (e is AuthFailed || chxWorkSyncSummary(syncMessage, true).authRequired) {
                            authRequired = true
                            checkResult = null
                        }
                    }
                } finally {
                    syncing = false
                }
            }
        }
        if (syncing) {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                colors = ProgressIndicatorDefaults.progressIndicatorColors(
                    foregroundColor = ds.accent, backgroundColor = ds.breakBar,
                ),
            )
        }
        if (syncMessage.isNotBlank()) {
            Spacer(Modifier.height(AppUiTokens.CardGap))
            ChxSyncResult(syncMessage, syncError)
        }
        Spacer(Modifier.height(AppUiTokens.CardGap))
        AppCard(modifier = Modifier.fillMaxWidth()) {
            BasicComponent(
                modifier = Modifier.semantics { stateDescription = if (helpExpanded) "已展开" else "已折叠" },
                insideMargin = PaddingValues(AppUiTokens.CardPadding),
                onClick = { helpExpanded = !helpExpanded }, role = Role.Button,
                endActions = { Text(if (helpExpanded) "收起" else "展开", fontSize = 13.sp, color = ds.accent) },
            ) {
                Text("连接与帮助", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = ds.text)
            }
            if (helpExpanded) {
                onNotifications?.let { callback ->
                    ChxEntryOption("通知待办", !busy, onClick = callback)
                }
                ChxEntryOption("备用学习通登录", !busy) { connect(direct = true) }
                ChxEntryOption(if (checking) "正在检查…" else "重新检查连接", !busy, onClick = ::checkSession)
                // 即便候选已删除，退出和本地清理重试也不能消失。
                ChxEntryOption(if (logoutPending) "正在请求退出…" else "退出并清理连接", !busy, danger = true) {
                    checkResult = null
                    syncMessage = ""
                    serverConfirmed = false
                    localCleared = null
                    logoutPending = true
                    isError = false
                    message = "正在请求学校退出…"
                    try {
                        casLogout.launch(Intent(context, ChxWebActivity::class.java)
                            .putExtra(ChxWebActivity.EXTRA_URL, ChxSso.LOGOUT_URL)
                            .putExtra(ChxWebActivity.EXTRA_LOGOUT, true))
                    } catch (_: Exception) {
                        logoutPending = false
                        clearPending = true
                    }
                }
                if (clearPending || localCleared == false) {
                    ChxEntryOption(if (clearPending) "正在清理本机…" else "重试本机清理", !busy, danger = true) {
                        clearPending = true
                    }
                }
                Column(Modifier.padding(horizontal = AppUiTokens.CardPadding).padding(bottom = 12.dp)) {
                    ChxReadDetails(
                        "连接状态只依据本次学习通首页验证，不代表课程、任务进度或作业接口已成功。" +
                            "学校账户为本机保存信息，不用于确认学习通网页账户。\n" +
                            ChxSso.linkingHint(ChxWebSession.hasCasWebSession()) + "\n" +
                            "退出会清理本机已知学习通与学校认证站点的 Cookie 和网页存储；教务独立会话保留。" +
                            "学校退出确认不代表所有站点都已退出，本地待办不受影响。",
                    )
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
internal fun ChxSyncResult(message: String, failed: Boolean) {
    val ds = LocalDs.current
    val summary = remember(message, failed) { chxWorkSyncSummary(message, failed) }
    AppCard(Modifier.fillMaxWidth(), insideMargin = PaddingValues(AppUiTokens.CardPadding)) {
        Text(summary.title, fontSize = 18.sp, fontWeight = FontWeight.SemiBold,
            color = if (summary.needsAttention) ds.danger else ds.text)
        Row(Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(Modifier.weight(1f)) {
                Text("成功课程", fontSize = 13.sp, color = ds.sub)
                Text(summary.successfulCourses?.toString() ?: "—", fontSize = 34.sp,
                    fontWeight = FontWeight.Bold, color = ds.text)
            }
            Column(Modifier.weight(1f)) {
                Text("失败课程", fontSize = 13.sp, color = if (summary.needsAttention) ds.danger else ds.sub)
                Text(summary.failedCourses?.toString() ?: "—", fontSize = 34.sp,
                    fontWeight = FontWeight.Bold, color = ds.text)
            }
        }
        summary.changeSummary?.let {
            Text(it, fontSize = 13.sp, lineHeight = 20.sp, color = ds.sub, modifier = Modifier.padding(top = 8.dp))
        }
        Text("本地待办已保留", fontSize = 13.sp, color = ds.sub, modifier = Modifier.padding(top = 6.dp))
        if (summary.authRequired) {
            Text("部分请求需要重新连接", fontSize = 13.sp, color = ds.danger, modifier = Modifier.padding(top = 6.dp))
        }
        ChxReadDetails(message)
    }
}

/** 长诊断默认折叠；调用方必须在外层保留失败或部分完成的短提示。 */
@Composable
internal fun ChxReadDetails(message: String) {
    val ds = LocalDs.current
    var expanded by rememberSaveable(message) { mutableStateOf(false) }
    BasicComponent(
        modifier = Modifier.semantics { stateDescription = if (expanded) "已展开" else "已折叠" },
        insideMargin = PaddingValues(vertical = 12.dp),
        onClick = { expanded = !expanded }, role = Role.Button,
    ) {
        Text(if (expanded) "收起详情" else "查看详情", fontSize = 14.sp, color = ds.accent)
    }
    if (expanded) Text(message, fontSize = 13.sp, lineHeight = 20.sp, color = ds.sub)
}

/** 仅展示数据层固定的阶段与错误类别，原始网络异常可能包含签名或私密响应。 */
internal fun chxWorkSyncError(error: Exception): String = when (error) {
    is AuthFailed -> "作业接口要求重新登录学习通，请打开学习通网页完成认证；此前的首页验证不代表作业接口仍有效。"
    is ChxCourseReadException -> "作业同步未完成：${error.message}"
    else -> "作业同步未完成，请检查网络后重试或在学习通网页查看；现有待办已保留。"
}

@Composable
private fun ChxActionButton(text: String, enabled: Boolean, subtitle: String, onClick: () -> Unit) {
    val ds = LocalDs.current
    AppCard(
        modifier = Modifier.fillMaxWidth().semantics { if (!enabled) disabled() },
        insideMargin = PaddingValues(AppUiTokens.CardPadding),
        onClick = if (enabled) onClick else null,
    ) {
        Text(text, fontSize = 17.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold,
            color = if (enabled) ds.text else ds.sub)
        Text(subtitle, fontSize = 13.sp, lineHeight = 20.sp, color = ds.sub, modifier = Modifier.padding(top = 6.dp))
    }
}

@Composable
private fun ChxEntryOption(text: String, enabled: Boolean, danger: Boolean = false, onClick: () -> Unit) {
    val ds = LocalDs.current
    BasicComponent(
        modifier = Modifier.semantics { if (!enabled) disabled() },
        insideMargin = PaddingValues(horizontal = AppUiTokens.CardPadding, vertical = 16.dp),
        onClick = onClick, enabled = enabled, role = Role.Button,
    ) {
        Text(text, fontSize = 16.sp, lineHeight = 23.sp,
            color = when { !enabled -> ds.sub; danger -> ds.danger; else -> ds.text })
    }
}
