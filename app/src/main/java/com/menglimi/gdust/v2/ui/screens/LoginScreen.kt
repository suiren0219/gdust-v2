package com.menglimi.gdust.v2.ui.screens

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.menglimi.gdust.v2.data.Repository
import com.menglimi.gdust.v2.data.isNetworkDown
import com.menglimi.gdust.v2.ui.AppCard
import com.menglimi.gdust.v2.ui.AppUiTokens
import com.menglimi.gdust.v2.ui.LocalDs
import com.menglimi.gdust.v2.ui.pageBackground
import com.haooz.nexioui.button.Button as NexioButton
import com.haooz.nexioui.button.ButtonDefaults
import com.haooz.nexioui.button.FlatTextButton
import com.haooz.nexioui.text.NexioText as Text

@Composable
fun LoginScreen(
    repo: Repository,
    onLoggedIn: () -> Unit,
    onOpenSettings: () -> Unit,
    onPreview: () -> Unit,
    onSchoolLogin: () -> Unit,
) {
    val ds = LocalDs.current
    val scope = rememberCoroutineScope()
    val isTablet = LocalConfiguration.current.screenWidthDp >= 600
    val pagePadding = if (isTablet) 20.dp else AppUiTokens.PagePadding

    var account by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    var showLegacy by remember { mutableStateOf(false) }
    val login = remember(repo, scope) {
        LegacyLoginController(scope, {
            repo.fetchCaptcha().also {
                check(BitmapFactory.decodeByteArray(it.imageBytes, 0, it.imageBytes.size) != null)
            }
        }, { account, password, code, uuid -> repo.login(account, password, code, uuid) })
    }
    val loginState by login.state.collectAsState()
    val captcha = remember(loginState.captcha) {
        loginState.captcha?.imageBytes?.let { BitmapFactory.decodeByteArray(it, 0, it.size)?.asImageBitmap() }
    }
    DisposableEffect(login, showLegacy) {
        if (showLegacy) login.refreshCaptcha()
        onDispose { login.cancel() }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(androidx.compose.ui.graphics.Color.Transparent)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = pagePadding),
    ) {
        Spacer(Modifier.height(if (isTablet) 24.dp else 12.dp))
        PageTitle(title = "广科课程表", subtitle = "课表 · 电费 · 待办 · 通知总结")

        AppCard(
            modifier = Modifier.fillMaxWidth(),
            cornerRadius = 20.dp,
            insideMargin = PaddingValues(AppUiTokens.CardPadding),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("一次学校登录", fontSize = 17.sp, fontWeight = FontWeight.Medium, color = ds.text)
                Text("在学校页面完成认证后，自动连接教务与学习通，分别确认可用状态。", fontSize = 14.sp, lineHeight = 21.sp, color = ds.sub)
                LoginActionButton(text = "学校统一登录", onClick = onSchoolLogin)
            }
        }
        Spacer(Modifier.height(12.dp))
        AppCard(
            modifier = Modifier.fillMaxWidth(),
            cornerRadius = 20.dp,
            insideMargin = PaddingValues(AppUiTokens.CardPadding),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("其他登录方式", fontSize = 17.sp, fontWeight = FontWeight.Medium, color = ds.text)
                Text("兼容入口登录后会继续学校网页登录，补全其他服务的认证。", fontSize = 13.sp, lineHeight = 20.sp, color = ds.sub)
                FlatTextButton(
                    text = if (showLegacy) "收起兼容登录" else "展开兼容登录",
                    onClick = { showLegacy = !showLegacy },
                    minHeight = 48.dp,
                    insideMargin = PaddingValues(horizontal = 16.dp, vertical = 0.dp),
                    cornerRadius = 100.dp,
                    textStyle = TextStyle(fontSize = 14.sp),
                    modifier = Modifier.fillMaxWidth(),
                )
                CardExpand(visible = showLegacy) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("账号登录（兼容）", fontSize = 17.sp, fontWeight = FontWeight.Medium, color = ds.text)
                        LoginLegacyInputField(account, { account = it }, "学号", KeyboardType.Number, enabled = !loginState.submitting)
                        LoginLegacyInputField(password, { password = it }, "密码", visual = PasswordVisualTransformation(), enabled = !loginState.submitting)
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            LoginLegacyInputField(loginState.code, login::changeCode, "验证码", modifier = Modifier.weight(1f),
                                enabled = !loginState.submitting && !loginState.loadingCaptcha && captcha != null)
                            Box(
                                Modifier
                                    .width(112.dp)
                                    .height(48.dp)
                                    .background(ds.bg, androidx.compose.foundation.shape.RoundedCornerShape(20.dp))
                                    .clickable(enabled = !loginState.submitting && !loginState.loadingCaptcha,
                                        role = Role.Button, onClickLabel = "刷新验证码") { login.refreshCaptcha() },
                                contentAlignment = Alignment.Center,
                            ) {
                                captcha?.let {
                                    Image(it, "验证码", contentScale = ContentScale.FillBounds, modifier = Modifier.fillMaxSize())
                                } ?: Text(if (loginState.loadingCaptcha) "加载中…" else "点击重试", fontSize = 13.sp, color = ds.sub)
                            }
                        }
                        loginState.loginError?.let {
                            Text(it, fontSize = 13.sp, lineHeight = 20.sp, color = ds.danger)
                        }
                        loginState.captchaError?.let {
                            Text(it, fontSize = 13.sp, lineHeight = 20.sp, color = ds.danger)
                        }
                        LoginActionButton(
                            text = if (loginState.submitting) "正在登录…" else "登录并同步",
                            enabled = captcha != null && loginState.canSubmit(account, password),
                        ) {
                            error = ""
                            login.submit(account, password, onLoggedIn)
                        }
                    }
                }
            }
        }
        if (showLegacy) {
            Spacer(Modifier.height(12.dp))
            DingTalkSection(repo = repo, onLoggedIn = onLoggedIn, onError = { error = it })
        }

        if (error.isNotEmpty()) {
            Text(error, fontSize = 13.sp, lineHeight = 20.sp, color = ds.danger, modifier = Modifier.padding(top = 12.dp))
        }

        Text(
            "认证直接在学校和学习通页面完成，密码不落盘。\n" +
                "已连接的服务保留在本机；部分服务暂不可用时，可以稍后继续连接。",
            fontSize = 13.sp,
            lineHeight = 20.sp,
            color = ds.sub,
            modifier = Modifier.padding(top = 20.dp),
        )
        // 免登录预览：不接触网络数据，只看界面
        Box(
            Modifier.fillMaxWidth().padding(top = 10.dp).heightIn(min = 48.dp)
                .clickable(role = Role.Button, onClick = onPreview),
            contentAlignment = Alignment.CenterStart,
        ) {
            Text("先看看界面（不登录）›", fontSize = 13.sp, color = ds.accent)
        }

        // 未登录也能进入设置和关于页。
        Box(
            Modifier.fillMaxWidth().heightIn(min = 48.dp)
                .clickable(role = Role.Button, onClick = onOpenSettings),
            contentAlignment = Alignment.CenterStart,
        ) {
            Text("设置 ›", fontSize = 13.sp, color = ds.accent)
        }
        Spacer(Modifier.height(40.dp))
    }

}

@Composable
private fun LoginLegacyInputField(
    value: String,
    onChange: (String) -> Unit,
    placeholder: String,
    keyboardType: KeyboardType = KeyboardType.Text,
    modifier: Modifier = Modifier,
    visual: VisualTransformation = VisualTransformation.None,
    enabled: Boolean = true,
    hint: String = "请输入",
) {
    val ds = LocalDs.current
    Row(
        modifier = modifier.fillMaxWidth().heightIn(min = 56.dp).padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(placeholder, modifier = Modifier.weight(1f), fontSize = 17.sp,
            fontWeight = FontWeight.Medium, color = if (enabled) ds.text else ds.sub,
            maxLines = 2, overflow = TextOverflow.Ellipsis)
        BasicTextField(
            value = value,
            onValueChange = onChange,
            enabled = enabled,
            modifier = Modifier.weight(1f).heightIn(min = 44.dp)
                .semantics { contentDescription = placeholder },
            textStyle = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.Medium,
                color = if (enabled) ds.text else ds.sub, textAlign = TextAlign.End),
            singleLine = true,
            cursorBrush = SolidColor(ds.accent),
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            visualTransformation = visual,
            decorationBox = { inner ->
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                    if (value.isEmpty()) Text(hint, fontSize = 14.sp, color = ds.sub, textAlign = TextAlign.End)
                    inner()
                }
            },
        )
    }
}

/** 账号登录中的操作使用共享 Nexio 按钮封装。 */
@Composable
private fun LoginActionButton(text: String, enabled: Boolean = true, onClick: () -> Unit) {
    NexioButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 100.dp,
        minHeight = 48.dp,
        colors = ButtonDefaults.buttonColorsPrimary(),
        insideMargin = PaddingValues(horizontal = 20.dp, vertical = 0.dp),
    ) {
        Text(text, fontSize = 16.sp, fontWeight = FontWeight.Medium, color = LocalContentColor.current)
    }
}

/** 钉钉扫码登录：SSE 拿 clientId → App 自绘二维码 → 钉钉扫 → ticket 回传换 TOKEN。 */
@Composable
private fun DingTalkSection(repo: Repository, onLoggedIn: () -> Unit, onError: (String) -> Unit) {
    val ds = LocalDs.current
    var active by remember { mutableStateOf(false) }
    // 会话序号：换票失败自动重试或手动刷新时递增，重启 SSE 连接拿新的 clientId/票据
    var sessionTick by remember { mutableStateOf(0) }
    var autoRetry by remember { mutableStateOf(0) }
    var clientId by remember { mutableStateOf<String?>(null) }
    var status by remember { mutableStateOf("") }
    var netDown by remember { mutableStateOf(false) }

    LaunchedEffect(active, sessionTick) {
        if (!active) return@LaunchedEffect
        var attempt = 0
        while (active && attempt < 3) {
            attempt++
            status = if (attempt == 1) "正在连接认证服务…" else "连接中断，正在重试（$attempt/3）…"
            // SSE 断连（Software caused connection abort 等）会沿 flow 抛异常：重连几次再报错
            val result = runCatching {
                repo.subscribeSse().collect { ev ->
                    when (ev) {
                        is com.menglimi.gdust.v2.data.SseEvent.Hello -> {
                            clientId = ev.clientId
                            status = "二维码已就绪，用钉钉扫一扫"
                        }
                        is com.menglimi.gdust.v2.data.SseEvent.Ticket -> {
                            status = "授权成功，正在进入…"
                            runCatching { repo.finishSso(ev.ticket) }
                                .onSuccess {
                                    autoRetry = 0
                                    active = false
                                    onLoggedIn()
                                }
                                .onFailure {
                                    // 失败后旧票据作废：同一张一次性票据不能重扫
                                    clientId = null
                                    if (it.isNetworkDown()) netDown = true
                                    // 概率性失败多为学校响应慢导致票据过期：自动换新二维码再试，
                                    // 超过次数后停下交还给用户（可手动刷新）
                                    if (autoRetry < 2 && !it.isNetworkDown()) {
                                        autoRetry++
                                        status = "票据未能换取会话，正在生成新的二维码…（$autoRetry/2）"
                                        sessionTick++
                                    } else {
                                        status = ""
                                        onError("票据换取失败：${it.message}\n请点「刷新二维码」再扫一次，或改用网页登录导入。")
                                    }
                                }
                        }
                        is com.menglimi.gdust.v2.data.SseEvent.NeedBind ->
                            onError("钉钉未绑定校园账号，请在钉钉打开的页面里完成绑定后重试")
                        is com.menglimi.gdust.v2.data.SseEvent.Error -> onError(ev.message)
                    }
                }
            }
            if (result.isSuccess || !active) return@LaunchedEffect
            val err = result.exceptionOrNull()
            // 最后一次失败才提示；中间失败静默重连
            if (attempt >= 3) {
                active = false
                clientId = null
                if (err?.isNetworkDown() == true) netDown = true
                else onError("认证服务连接不稳定，请重试")
                return@LaunchedEffect
            }
            kotlinx.coroutines.delay(1200L * attempt)
        }
    }

    AppCard(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 20.dp,
        insideMargin = PaddingValues(AppUiTokens.CardPadding),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("钉钉扫码登录", fontSize = 17.sp, fontWeight = FontWeight.Medium, color = ds.text)
            val cid = clientId
            if (cid == null) {
                Text("点下方按钮生成二维码，用钉钉扫一扫即可授权", fontSize = 13.sp, color = ds.sub)
                LoginActionButton(text = if (active) "连接中…" else "生成登录二维码", enabled = !active) {
                    autoRetry = 0
                    active = true
                }
            } else {
                // 自绘二维码：内容 = CAS 授权页 URL（钉钉内打开会走 JSAPI 免登）
                val url = repo.mobieAuthUrl(cid)
                Box(
                    Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    QrCodeImage(content = url, size = 210.dp)
                }
                Text(status.ifEmpty { "用钉钉扫一扫上面的二维码" }, fontSize = 13.sp, color = ds.sub)
                // 手动刷新：换一张新的 clientId/票据；二维码过期或怀疑被占用时用。
                // 等待扫码期间 SSE 仍在挂起，刷新会重启会话序号取消旧连接。
                LoginActionButton(text = "刷新二维码") {
                    autoRetry = 0
                    clientId = null
                    status = ""
                    sessionTick++
                }
                Text("也可复制授权链接到钉钉内打开", fontSize = 13.sp, color = ds.sub)
            }
        }
    }

    NetworkDownDialog(visible = netDown, onDismiss = { netDown = false })
}

/**
 * 钉钉登录二维码：ZXing 生成最小矩阵 → 预渲染成 ImageBitmap → 一次 drawImage。
 * 不逐格绘制（512×512 会 26 万次 drawRect 直接掉帧）；前景固定纯黑，绝不吃主题色（否则扫不出来）。
 */
@Composable
private fun QrCodeImage(content: String, size: androidx.compose.ui.unit.Dp) {
    val bitmap = remember(content) { renderQrBitmap(content) }
    if (bitmap == null) {
        Text("二维码生成失败，请重试", fontSize = 13.sp, color = LocalDs.current.danger)
        return
    }
    androidx.compose.foundation.Image(
        bitmap = bitmap,
        contentDescription = "钉钉登录二维码",
        contentScale = androidx.compose.ui.layout.ContentScale.Fit,
        filterQuality = androidx.compose.ui.graphics.FilterQuality.None,
        modifier = Modifier
            .size(size)
            .background(Color.White, androidx.compose.foundation.shape.RoundedCornerShape(AppUiTokens.ItemCorner))
            .padding(8.dp),
    )
}

/** 生成 N×N 黑白位图（宽高传 0 让 ZXing 用最小尺寸，一个模块一个像素）。 */
private fun renderQrBitmap(content: String): androidx.compose.ui.graphics.ImageBitmap? = runCatching {
    val hints = mapOf(
        com.google.zxing.EncodeHintType.MARGIN to 4,
        com.google.zxing.EncodeHintType.CHARACTER_SET to "UTF-8",
        com.google.zxing.EncodeHintType.ERROR_CORRECTION to com.google.zxing.qrcode.decoder.ErrorCorrectionLevel.M,
    )
    val matrix = com.google.zxing.qrcode.QRCodeWriter().encode(
        content,
        com.google.zxing.BarcodeFormat.QR_CODE,
        0,
        0,
        hints,
    )
    val w = matrix.width
    val h = matrix.height
    val pixels = IntArray(w * h)
    for (y in 0 until h) {
        for (x in 0 until w) {
            pixels[y * w + x] = if (matrix[x, y]) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
        }
    }
    val bmp = android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888)
    bmp.setPixels(pixels, 0, w, 0, 0, w, h)
    bmp.asImageBitmap()
}.getOrNull()
