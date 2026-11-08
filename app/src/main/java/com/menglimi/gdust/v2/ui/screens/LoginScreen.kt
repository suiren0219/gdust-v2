package com.menglimi.gdust.v2.ui.screens

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.menglimi.gdust.v2.data.AuthFailed
import com.menglimi.gdust.v2.data.Repository
import com.menglimi.gdust.v2.ui.GlassCard
import com.menglimi.gdust.v2.ui.LocalDs
import kotlinx.coroutines.launch

@Composable
fun LoginScreen(repo: Repository, onLoggedIn: () -> Unit) {
    val ds = LocalDs.current
    val scope = rememberCoroutineScope()

    var account by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var captcha by remember { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }
    var uuid by remember { mutableStateOf("") }
    var seed by remember { mutableStateOf(0) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }

    LaunchedEffect(seed) {
        runCatching { repo.fetchCaptcha() }
            .onSuccess {
                captcha = BitmapFactory.decodeByteArray(it.imageBytes, 0, it.imageBytes.size)?.asImageBitmap()
                uuid = it.uuid
                error = ""
            }
            .onFailure { error = "验证码获取失败：${it.message}" }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(ds.bg)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
    ) {
        Spacer(Modifier.height(56.dp))
        Text("广科课程表", fontSize = 35.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, color = ds.text)
        Text("课表 · 电费 · 待办 · 通知总结", fontSize = 14.sp, color = ds.sub, modifier = Modifier.padding(top = 6.dp, bottom = 24.dp))

        GlassCard {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("统一身份认证登录", fontSize = 15.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, color = ds.text)
                InputField(account, { account = it }, "学号", KeyboardType.Number)
                InputField(password, { password = it }, "密码", visual = PasswordVisualTransformation())
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    InputField(code, { code = it }, "验证码", modifier = Modifier.weight(1f))
                    Box(
                        Modifier
                            .width(112.dp)
                            .height(46.dp)
                            .background(ds.bg, androidx.compose.foundation.shape.RoundedCornerShape(10.dp))
                            .clickable { seed++ },
                        contentAlignment = Alignment.Center,
                    ) {
                        captcha?.let {
                            Image(it, "验证码", contentScale = ContentScale.FillBounds, modifier = Modifier.fillMaxSize())
                        } ?: Text("点击加载", fontSize = 11.sp, color = ds.sub)
                    }
                }
                Button("登录并同步", enabled = !busy && account.isNotBlank() && password.isNotBlank() && code.isNotBlank()) {
                    busy = true
                    error = ""
                    scope.launch {
                        runCatching { repo.login(account.trim(), password, code.trim(), uuid) }
                            .onSuccess { onLoggedIn() }
                            .onFailure {
                                if (it is AuthFailed && it.code == 30001) {
                                    error = "验证码不对，已换一张，再试一次"
                                } else {
                                    error = "登录失败：${it.message}"
                                }
                                seed++
                            }
                        busy = false
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        DingTalkSection(repo = repo, onLoggedIn = onLoggedIn, onError = { error = it })

        if (error.isNotEmpty()) {
            Text(error, fontSize = 13.sp, color = ds.danger, modifier = Modifier.padding(top = 12.dp))
        }

        Text(
            "本机直连学校系统，不经过任何服务器；\n密码不落盘，只保存登录凭证与水电脑号。",
            fontSize = 11.sp,
            color = ds.sub,
            modifier = Modifier.padding(top = 20.dp),
        )
        Spacer(Modifier.height(40.dp))
    }
}

@Composable
fun InputField(
    value: String,
    onChange: (String) -> Unit,
    placeholder: String,
    keyboardType: KeyboardType = KeyboardType.Text,
    modifier: Modifier = Modifier,
    visual: VisualTransformation = VisualTransformation.None,
) {
    val ds = LocalDs.current
    BasicTextField(
        value = value,
        onValueChange = onChange,
        modifier = modifier
            .fillMaxWidth()
            .background(ds.bg, androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp, vertical = 13.dp),
        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 15.sp, color = ds.text),
        singleLine = true,
        cursorBrush = SolidColor(ds.accent),
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        visualTransformation = visual,
        decorationBox = { inner ->
            Box {
                if (value.isEmpty()) Text(placeholder, fontSize = 15.sp, color = ds.sub)
                inner()
            }
        },
    )
}

@Composable
fun Button(text: String, enabled: Boolean = true, onClick: () -> Unit) {
    val ds = LocalDs.current
    Box(
        Modifier
            .fillMaxWidth()
            .background(
                if (enabled) ds.accent else ds.sub.copy(alpha = 0.3f),
                androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, fontSize = 16.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, color = Color.White)
    }
}


/** 钉钉一键登录：SSE 连接 → 跳钉钉授权 → ticket 换 TOKEN。 */
@Composable
private fun DingTalkSection(repo: Repository, onLoggedIn: () -> Unit, onError: (String) -> Unit) {
    val ds = LocalDs.current
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    var active by remember { mutableStateOf(false) }
    var clientId by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(active) {
        if (!active) return@LaunchedEffect
        repo.subscribeSse().collect { ev ->
            when (ev) {
                is com.menglimi.gdust.v2.data.SseEvent.Hello -> clientId = ev.clientId
                is com.menglimi.gdust.v2.data.SseEvent.Ticket -> {
                    runCatching { repo.finishSso(ev.ticket) }
                        .onSuccess { onLoggedIn() }
                        .onFailure { onError("票据换取失败：${it.message}") }
                }
                is com.menglimi.gdust.v2.data.SseEvent.NeedBind ->
                    onError("钉钉未绑定校园账号，请在授权页完成绑定后重试")
                is com.menglimi.gdust.v2.data.SseEvent.Error -> onError(ev.message)
            }
        }
    }

    GlassCard {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("或使用钉钉一键登录（免密码）", fontSize = 14.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, color = ds.text)
            Button(
                text = if (clientId == null) "连接认证服务" else "打开钉钉授权",
                onClick = {
                    if (!active) {
                        active = true
                    } else {
                        val cid = clientId ?: return@Button
                        val url = repo.mobieAuthUrl(cid)
                        val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url))
                            .setPackage("com.alibaba.android.rimet")
                            .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                        val ok = runCatching { ctx.startActivity(intent) }.isSuccess ||
                            runCatching { ctx.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url)).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess
                        if (!ok) onError("打不开钉钉：请确认已安装钉钉")
                    }
                },
            )
        }
    }
}
