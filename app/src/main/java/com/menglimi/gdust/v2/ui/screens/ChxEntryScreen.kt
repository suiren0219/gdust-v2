package com.menglimi.gdust.v2.ui.screens

import android.app.Activity
import android.content.Intent
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.menglimi.gdust.v2.data.ChxSso
import com.menglimi.gdust.v2.data.Repository
import com.menglimi.gdust.v2.ui.LocalDs
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 学习通联动：一次学校统一身份认证，学习通与教务网页共用同一个 CAS 会话。
 *
 * 登录页由 [ChxWebActivity]（独立 Activity + 裸 WebView）承载，不再放进 Compose
 * `AndroidView`——后者会让页面的视口单位（100vh/100dvh）恒为 0，CAS 前端整页白屏，
 * 详见 ChxWebActivity 顶部注释与 debug 源集的 WebViewProbeActivity。
 *
 * 只保存 CAS 放行后的超星 Cookie（加密存储），不保存密码。
 */
@Composable
fun ChxEntryScreen(repo: Repository, onBack: () -> Unit) {
    val ds = LocalDs.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var message by remember { mutableStateOf("点「学习通」开始；没登录过会跳到学校统一身份认证。") }
    var isError by remember { mutableStateOf(false) }
    var linked by remember { mutableStateOf(repo.store.hasChxSession) }
    var checking by remember { mutableStateOf(false) }
    var checkResult by remember { mutableStateOf<Boolean?>(null) }
    var syncing by remember { mutableStateOf(false) }

    androidx.activity.compose.BackHandler { onBack() }

    // 登录页在独立 Activity 里完成；回来后按本机是否已存会话刷新状态
    val login = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val ok = result.resultCode == Activity.RESULT_OK
        linked = repo.store.hasChxSession
        checkResult = null
        isError = !ok && !linked
        message = when {
            linked -> "登录完成，学习通会话已保存（CAS 已放行）。"
            else -> "还没拿到学习通会话：可能停在登录页，可以再打开一次完成登录。"
        }
    }

    // 退出联动：清完本地后打开 CAS 登出页退掉服务端会话（落地即自动关闭）
    val casLogout = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { /* 保留退出提示文案 */ }

    fun open(url: String) {
        isError = false
        message = "正在打开登录页…"
        login.launch(
            Intent(context, ChxWebActivity::class.java)
                .putExtra(ChxWebActivity.EXTRA_URL, url),
        )
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(ds.bg)
            .statusBarsPadding()
            .padding(horizontal = 16.dp),
    ) {
        Spacer(Modifier.height(20.dp))
        PageTitle(title = "学习通联动", subtitle = "一次统一身份认证，学习通与教务共用", onBack = onBack)

        // ── 状态 ──
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Box(
                Modifier
                    .background(
                        if (linked) ds.accent.copy(alpha = 0.14f) else ds.sub.copy(alpha = 0.10f),
                        RoundedCornerShape(8.dp),
                    )
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            ) {
                Text(
                    if (linked) "已联动" else "未联动",
                    fontSize = 11.sp, fontWeight = FontWeight.Medium,
                    color = if (linked) ds.accent else ds.sub,
                )
            }
            Text(
                when {
                    !linked -> "还没有学习通会话"
                    repo.store.chxCookieAt > 0 ->
                        "会话保存于 ${SimpleDateFormat("MM-dd HH:mm", Locale.CHINA).format(Date(repo.store.chxCookieAt))}"
                    else -> "已有会话"
                },
                fontSize = 11.sp, color = ds.sub,
                modifier = Modifier.padding(start = 8.dp),
            )
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(top = 10.dp),
        ) {
            Box(Modifier.weight(1f)) { Button("学习通") { open(ChxSso.fyssoUrl) } }
            Box(Modifier.weight(1f)) { Button("课程中心") { open(ChxSso.mhUrl) } }
            Box(Modifier.weight(1f)) { Button("教务网页") { open(ChxSso.teachingWebUrl) } }
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(top = 8.dp),
        ) {
            Box(Modifier.weight(1f)) {
                Button(if (linked) "打开学习通首页" else "学习通首页") { open(ChxSso.HOME) }
            }
            Box(Modifier.weight(1f)) {
                Button(
                    text = when {
                        checking -> "检查中…"
                        checkResult == true -> "会话有效 ✓"
                        checkResult == false -> "会话已失效"
                        else -> "检查会话"
                    },
                    enabled = linked && !checking,
                ) {
                    checking = true
                    isError = false
                    scope.launch {
                        val alive = runCatching { repo.chxSessionAlive() }
                            .getOrElse { null }
                        checkResult = alive
                        checking = false
                        linked = repo.store.hasChxSession
                        if (alive == null) {
                            isError = true
                            message = "网络不通，无法确认会话状态；本地会话没有删除。"
                        } else if (alive) {
                            message = "会话有效，可以直接进学习通。"
                        } else {
                            isError = false
                            message = "会话已失效，请重新登录一次（仍走学校统一身份认证）。"
                        }
                    }
                }
            }
            if (linked) {
                Box(Modifier.weight(1f)) {
                    Button("退出联动") {
                        repo.clearChxSession()
                        linked = false
                        checkResult = null
                        message = "已清除本机学习通会话与网页 Cookie，并退出学校统一身份认证。"
                        casLogout.launch(
                            Intent(context, ChxWebActivity::class.java)
                                .putExtra(ChxWebActivity.EXTRA_URL, ChxSso.LOGOUT_URL),
                        )
                    }
                }
            }
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(top = 8.dp),
        ) {
            // 备用入口：学校 CAS 不可达时用学习通账号（手机号/密码）直登，同一套 .chaoxing.com 会话
            Box(Modifier.weight(1f)) { Button("直接登录学习通（备用）") { open(ChxSso.DIRECT_LOGIN) } }
        }

        if (linked) {
            Button(if (syncing) "正在同步作业…" else "同步学习通作业到待办", enabled = !syncing) {
                syncing = true
                isError = false
                scope.launch {
                    message = runCatching { repo.chxSyncWorkTodos() }
                        .getOrElse { "同步失败：${it.message}" }
                    syncing = false
                }
            }
        }

        Text(
            message,
            fontSize = 12.sp, lineHeight = 17.sp,
            color = if (isError) ds.danger else ds.sub,
            modifier = Modifier.padding(top = 8.dp),
        )

        Spacer(Modifier.height(14.dp))
        // 说明卡片：登录页在独立 Activity 打开（不嵌进 Compose 的 WebView）
        Box(
            Modifier
                .fillMaxWidth()
                .background(ds.card, RoundedCornerShape(14.dp))
                .padding(14.dp),
        ) {
            Text(
                "点上面的入口会在独立页面打开学校统一身份认证。登录一次后，" +
                    "学习通与教务网页在这个页面里都不再要密码；返回后状态会自动更新。",
                fontSize = 12.sp, lineHeight = 18.sp, color = ds.sub,
            )
        }
    }
}
