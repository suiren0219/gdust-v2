package com.menglimi.gdust.v2.ui.screens

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
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
import com.menglimi.gdust.v2.ui.LocalUiStyle
import com.menglimi.gdust.v2.ui.UiStyle
import kotlinx.coroutines.launch

@Composable
fun LoginScreen(
    repo: Repository,
    onLoggedIn: () -> Unit,
    onOpenSettings: () -> Unit,
    onPreview: () -> Unit,
) {
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
        Spacer(Modifier.height(52.dp))
        PageTitle(title = "广科课程表", subtitle = "课表 · 电费 · 待办 · 通知总结")

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
        // 免登录预览：不接触网络数据，只看界面
        Text(
            text = "先看看界面（不登录）›",
            fontSize = 13.sp,
            color = ds.accent,
            modifier = Modifier
                .padding(top = 10.dp)
                .clickable { onPreview() },
        )

        // 未登录也能进设置（外观/关于等，先看再登录）
        Text(
            text = "设置与外观 ›",
            fontSize = 13.sp,
            color = ds.accent,
            modifier = Modifier
                .padding(top = 10.dp)
                .clickable { onOpenSettings() },
        )

        // 未登录也能切换两套 UI 风格（两套引擎完整适配）
        val styleState = LocalUiStyle.current
        Text(
            text = if (styleState.value == UiStyle.SOFT) "当前：柔光玻璃 · 点此换液态玻璃"
            else "当前：液态玻璃 · 点此换柔光玻璃",
            fontSize = 13.sp,
            color = ds.accent,
            modifier = Modifier
                .padding(top = 12.dp)
                .clickable {
                    styleState.value = if (styleState.value == UiStyle.SOFT) UiStyle.LIQUID else UiStyle.SOFT
                },
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
    if (LocalUiStyle.current.value == UiStyle.LIQUID) {
        io.github.neoruaa.glasmio.ui.GlasmioTextField(
            value = value,
            onValueChange = onChange,
            modifier = modifier,
            placeholder = placeholder,
            visualTransformation = visual,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        )
        return
    }
    BasicTextField(
        value = value,
        onValueChange = onChange,
        modifier = modifier
            .fillMaxWidth()
            .background(ds.bg.copy(alpha = 0.6f), androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
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
    if (LocalUiStyle.current.value == UiStyle.LIQUID) {
        io.github.neoruaa.glasmio.ui.GlasmioButton(
            onClick = onClick,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth(),
            emphasis = io.github.neoruaa.glasmio.glass.GlassEmphasis.Prominent,
        ) {
            Text(text, fontSize = 16.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
        }
        return
    }
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

/** 钉钉扫码登录：SSE 拿 clientId → App 自绘二维码 → 钉钉扫 → ticket 回传换 TOKEN。 */
@Composable
private fun DingTalkSection(repo: Repository, onLoggedIn: () -> Unit, onError: (String) -> Unit) {
    val ds = LocalDs.current
    var active by remember { mutableStateOf(false) }
    var clientId by remember { mutableStateOf<String?>(null) }
    var status by remember { mutableStateOf("") }

    LaunchedEffect(active) {
        if (!active) return@LaunchedEffect
        status = "正在连接认证服务…"
        repo.subscribeSse().collect { ev ->
            when (ev) {
                is com.menglimi.gdust.v2.data.SseEvent.Hello -> {
                    clientId = ev.clientId
                    status = "二维码已就绪，用钉钉扫一扫"
                }
                is com.menglimi.gdust.v2.data.SseEvent.Ticket -> {
                    status = "授权成功，正在进入…"
                    runCatching { repo.finishSso(ev.ticket) }
                        .onSuccess { onLoggedIn() }
                        .onFailure { onError("票据换取失败：${it.message}") }
                }
                is com.menglimi.gdust.v2.data.SseEvent.NeedBind ->
                    onError("钉钉未绑定校园账号，请在钉钉打开的页面里完成绑定后重试")
                is com.menglimi.gdust.v2.data.SseEvent.Error -> onError(ev.message)
            }
        }
    }

    GlassCard {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("或使用钉钉扫码登录（免密码）", fontSize = 15.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, color = ds.text)
            val cid = clientId
            if (cid == null) {
                Text("点下方按钮生成二维码，用钉钉扫一扫即可授权", fontSize = 12.sp, color = ds.sub)
                Button(text = if (active) "连接中…" else "生成登录二维码", enabled = !active) { active = true }
            } else {
                // 自绘二维码：内容 = CAS 授权页 URL（钉钉内打开会走 JSAPI 免登）
                val url = repo.mobieAuthUrl(cid)
                Box(
                    Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    QrCodeImage(content = url, size = 210.dp)
                }
                Text(status.ifEmpty { "用钉钉扫一扫上面的二维码" }, fontSize = 12.sp, color = ds.sub)
                Text("也可复制授权链接到钉钉内打开", fontSize = 11.sp, color = ds.sub)
            }
        }
    }
}

/** ZXing 生成矩阵 → Compose Canvas 画黑白块（不引扫码 SDK）。 */
@Composable
private fun QrCodeImage(content: String, size: androidx.compose.ui.unit.Dp) {
    val matrix = remember(content) {
        runCatching {
            com.google.zxing.qrcode.QRCodeWriter().encode(
                content,
                com.google.zxing.BarcodeFormat.QR_CODE,
                512,
                512,
                mapOf(com.google.zxing.EncodeHintType.MARGIN to 1),
            )
        }.getOrNull()
    }
    val dark = LocalDs.current.text
    androidx.compose.foundation.Canvas(
        Modifier
            .size(size)
            .background(androidx.compose.ui.graphics.Color.White, androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
            .padding(10.dp),
    ) {
        if (matrix == null) return@Canvas
        val n = matrix.width
        val cell = size.toPx() / n
        for (y in 0 until n) {
            for (x in 0 until n) {
                if (matrix[x, y]) {
                    drawRect(
                        color = dark,
                        topLeft = androidx.compose.ui.geometry.Offset(x * cell, y * cell),
                        size = androidx.compose.ui.geometry.Size(cell, cell),
                    )
                }
            }
        }
    }
}
