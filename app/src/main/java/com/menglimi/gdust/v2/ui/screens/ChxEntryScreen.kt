package com.menglimi.gdust.v2.ui.screens

import android.app.Activity
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.menglimi.gdust.v2.data.ChxSso
import com.menglimi.gdust.v2.data.Repository
import com.menglimi.gdust.v2.ui.LocalDs
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Text
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 学校认证与两个站点各自的会话独立；保存 Cookie 不等于当前已验证登录。 */
@Composable
fun ChxEntryScreen(repo: Repository, onBack: () -> Unit, onCourses: () -> Unit = {}) {
    val ds = LocalDs.current
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    var message by rememberSaveable { mutableStateOf("完成学校统一身份认证后，可进入学习通与教务网页。") }
    var isError by rememberSaveable { mutableStateOf(false) }
    var saved by remember { mutableStateOf(repo.store.hasChxSession) }
    var checking by remember { mutableStateOf(false) }
    var checkResult by remember { mutableStateOf<Boolean?>(null) }
    var logoutPending by rememberSaveable { mutableStateOf(false) }
    var clearPending by rememberSaveable { mutableStateOf(false) }
    var serverConfirmed by rememberSaveable { mutableStateOf(false) }
    var localCleared by rememberSaveable { mutableStateOf<Boolean?>(null) }
    val busy = checking || logoutPending || clearPending

    BackHandler { onBack() }

    // 先等待学校网页的结果，再清本地 CAS 票据；取消/重建后仍可重新执行本地清理。
    LaunchedEffect(clearPending) {
        if (!clearPending) return@LaunchedEffect
        checkResult = null
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
        message = (if (cleared) "已清理本机已知学习通与学校认证会话。" else "本地清理未完成，请重试。") +
            if (serverConfirmed) "学校服务器已确认退出统一认证。" else "学校服务器退出未确认。"
    }

    val webLogin = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        saved = repo.store.hasChxSession
        checkResult = null
        isError = false
        message = if (saved) "学习通会话已保存，待验证；可点击“检查会话”。" else "尚未取得学习通会话，可重新打开入口完成认证。"
    }
    val casLogout = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        logoutPending = false
        serverConfirmed = result.resultCode == Activity.RESULT_OK &&
            result.data?.getBooleanExtra(ChxWebActivity.EXTRA_LOGOUT_CONFIRMED, false) == true
        clearPending = true
        message = "正在清理本机已知会话…"
    }

    fun open(url: String) {
        if (busy) return
        checkResult = null
        localCleared = null
        serverConfirmed = false
        isError = false
        message = "正在打开网页登录…"
        try {
            webLogin.launch(Intent(context, ChxWebActivity::class.java).putExtra(ChxWebActivity.EXTRA_URL, url))
        } catch (_: Exception) {
            isError = true
            message = "暂时无法打开网页，请重试。"
        }
    }

    Column(
        Modifier.fillMaxSize().background(ds.bg).statusBarsPadding().navigationBarsPadding()
            .verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
    ) {
        Spacer(Modifier.height(20.dp))
        PageTitle(title = "学习通联动", subtitle = "学校认证，分别访问学习通与教务", onBack = onBack)
        Box(Modifier.background(
            if (saved) ds.accent.copy(alpha = 0.14f) else ds.sub.copy(alpha = 0.10f),
            RoundedCornerShape(8.dp),
        ).padding(horizontal = 8.dp, vertical = 4.dp)) {
            Text(
                when {
                    checkResult == true && saved -> "会话已验证"
                    checkResult == false -> "会话已失效"
                    saved -> "已保存，待验证"
                    else -> "未保存会话"
                },
                fontSize = 12.sp, fontWeight = FontWeight.Medium, color = if (saved) ds.accent else ds.sub,
            )
        }
        Text(
            if (saved && repo.store.chxCookieAt > 0) {
                "会话保存于 ${SimpleDateFormat("MM-dd HH:mm", Locale.CHINA).format(Date(repo.store.chxCookieAt))}"
            } else "学校、学习通和教务各自维护登录状态",
            fontSize = 12.sp, color = ds.sub, modifier = Modifier.padding(top = 8.dp),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 12.dp)) {
            Box(Modifier.weight(1f)) { ChxActionButton("学习通", !busy) { open(ChxSso.fyssoUrl) } }
            Box(Modifier.weight(1f)) { ChxActionButton("课程中心", !busy) { open(ChxSso.mhUrl) } }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
            Box(Modifier.weight(1f)) { ChxActionButton("教务网页", !busy) { open(ChxSso.teachingWebUrl) } }
            Box(Modifier.weight(1f)) { ChxActionButton("学习通首页", !busy) { open(ChxSso.HOME) } }
        }
        Spacer(Modifier.height(8.dp))
        ChxActionButton("查看课程与任务进度", !busy, onCourses)
        Spacer(Modifier.height(8.dp))
        ChxActionButton(if (checking) "检查中…" else "检查会话", saved && !busy) {
            checking = true
            checkResult = null
            isError = false
            scope.launch {
                try {
                    val alive = repo.chxSessionAlive()
                    checkResult = alive
                    saved = repo.store.hasChxSession
                    isError = alive != true
                    message = when (alive) {
                        true -> "学习通会话已通过本次验证。"
                        false -> "学习通会话已失效，请重新登录。"
                        null -> "暂时无法确认会话，可能是网络故障或页面变化；本地会话已保留。"
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    isError = true
                    message = "暂时无法确认会话；本地会话已保留，可稍后重试。"
                } finally {
                    checking = false
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        // 即便候选 Cookie 已删除，退出和本地重试入口也不消失。
        ChxActionButton(if (logoutPending) "正在请求学校退出…" else "退出学校认证并清理本机", !busy) {
            checkResult = null
            serverConfirmed = false
            localCleared = null
            logoutPending = true
            isError = false
            message = "正在请求学校退出；返回后再清理本地会话。"
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
            Spacer(Modifier.height(8.dp))
            ChxActionButton(if (clearPending) "正在清理本地会话…" else "重试本地清理", !busy) {
                clearPending = true
            }
        }
        Text(message, fontSize = 13.sp, lineHeight = 19.sp, color = if (isError) ds.danger else ds.sub,
            modifier = Modifier.padding(top = 12.dp))
        Text(
            "清理范围为本机已知学习通与学校认证站点的 Cookie、网页存储；无法保证覆盖其他域名或路径。" +
                "教务站点独立会话会保留，学校退出确认也不等于学习通等所有站点已退出。本应用不保存学校密码。",
            fontSize = 12.sp, lineHeight = 18.sp, color = ds.sub, modifier = Modifier.padding(top = 8.dp, bottom = 24.dp),
        )
    }
}

/** 沿用现有 Miuix 按钮，保证正文换行时仍有至少 48dp 的点击区域。 */
@Composable
private fun ChxActionButton(text: String, enabled: Boolean, onClick: () -> Unit) {
    Button(onClick = onClick, enabled = enabled, modifier = Modifier.fillMaxWidth(), minHeight = 48.dp,
        colors = ButtonDefaults.buttonColorsPrimary()) {
        Text(text, fontSize = 16.sp, fontWeight = FontWeight.Medium)
    }
}
