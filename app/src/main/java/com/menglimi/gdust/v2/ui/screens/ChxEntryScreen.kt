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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.height
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

/** 一次学校统一身份认证，共用 CAS 会话访问学习通与教务网页。 */
@Composable
fun ChxEntryScreen(repo: Repository, onBack: () -> Unit) {
    val ds = LocalDs.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var message by remember { mutableStateOf("首次使用时完成学校统一身份认证，后续可直接进入学习通与教务网页。") }
    var isError by remember { mutableStateOf(false) }
    var linked by remember { mutableStateOf(repo.store.hasChxSession) }
    var checking by remember { mutableStateOf(false) }
    var checkResult by remember { mutableStateOf<Boolean?>(null) }

    androidx.activity.compose.BackHandler { onBack() }

    val webLogin = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        linked = repo.store.hasChxSession
        checkResult = null
        isError = !linked && result.resultCode != Activity.RESULT_OK
        message = if (linked) {
            "学习通会话已保存；教务网页可复用同一学校认证。"
        } else {
            "尚未取得学习通会话。完成学校认证后返回，或重新打开入口。"
        }
    }
    val casLogout = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { message = "已退出学校统一认证与学习通会话。" }

    fun open(url: String) {
        isError = false
        message = "正在打开网页登录…"
        webLogin.launch(
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
        PageTitle(title = "学习通联动", subtitle = "一次学校认证，复用登录状态", onBack = onBack)
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Box(
                Modifier
                    .background(
                        if (linked) ds.accent.copy(alpha = 0.14f) else ds.sub.copy(alpha = 0.10f),
                        androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
                    )
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            ) {
                Text(
                    if (linked) "已联动" else "未联动",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (linked) ds.accent else ds.sub,
                )
            }
            Text(
                when {
                    !linked -> "还没有学习通会话"
                    repo.store.chxCookieAt > 0 -> "会话保存于 ${SimpleDateFormat("MM-dd HH:mm", Locale.CHINA).format(Date(repo.store.chxCookieAt))}"
                    else -> "已有会话"
                },
                fontSize = 11.sp,
                color = ds.sub,
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
                        val alive = runCatching { repo.chxSessionAlive() }.getOrNull()
                        checkResult = alive
                        checking = false
                        linked = repo.store.hasChxSession
                        when (alive) {
                            true -> message = "学习通会话有效。"
                            false -> message = "学习通会话已失效，请重新登录。"
                            null -> {
                                isError = true
                                message = "网络不通，暂时无法确认会话；本地状态已保留。"
                            }
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
                        message = "正在退出学校统一认证…"
                        casLogout.launch(
                            Intent(context, ChxWebActivity::class.java)
                                .putExtra(ChxWebActivity.EXTRA_URL, ChxSso.LOGOUT_URL),
                        )
                    }
                }
            }
        }
        Text(
            message,
            fontSize = 12.sp,
            lineHeight = 17.sp,
            color = if (isError) ds.danger else ds.sub,
            modifier = Modifier.padding(top = 8.dp),
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "教务网页与学习通分别向学校认证服务申请自己的会话；本应用不保存学校密码。",
            fontSize = 12.sp,
            lineHeight = 18.sp,
            color = ds.sub,
        )
    }
}
