package com.menglimi.gdust.v2.ui.screens

import android.annotation.SuppressLint
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.gson.JsonParser
import com.menglimi.gdust.v2.data.CalendarInfo
import com.menglimi.gdust.v2.data.CourseBlock
import com.menglimi.gdust.v2.data.Repository
import com.menglimi.gdust.v2.data.TeachingSession
import com.menglimi.gdust.v2.data.ZfScheduleParser
import com.menglimi.gdust.v2.ui.LocalDs
import java.net.URI
import java.time.LocalDate
import java.time.temporal.ChronoUnit

private const val VPN_LOGIN = "https://webvpn.gdust.edu.cn/https/77726476706e69737468656265737421f3f652d220347d436a468ca88d1b203b/cas/?service=https%3A%2F%2Fwebvpn.gdust.edu.cn%2Flogin%3Fcas_login%3Dtrue"
/** 默认入口：学校门户。校园网 IP 下 WebVPN 会明确拒绝登录，校外才需要 VPN。 */
private const val PORTAL_ENTRY = "https://portal.gdust.edu.cn"
private const val VPN_SCHEDULE = "/http/77726476706e69737468656265737421a1a013d2766626022b5cc7fd/kbcx/xskbcx_cxXsgrkb.html?vpn-12-o1-172.16.254.1&gnmkdm=N2151"
private const val DIRECT_SCHEDULE = "/kbcx/xskbcx_cxXsgrkb.html?gnmkdm=N2151"

/** 显示版本号，方便确认装的是哪一次构建。 */
private fun appVersionLabel(ctx: android.content.Context): String = runCatching {
    val info = ctx.packageManager.getPackageInfo(ctx.packageName, 0)
    val code = if (android.os.Build.VERSION.SDK_INT >= 28) info.longVersionCode else @Suppress("DEPRECATION") info.versionCode.toLong()
    "v${info.versionName}($code)"
}.getOrDefault("")

/** 把底层英文报错翻译成可操作中文（即使某个分支漏改 JS，也不会甩英文给用户）。 */
private fun localizeImportError(raw: String): String = when {
    raw.contains("Unexpected token", true) || raw.contains("is not valid JSON", true) ||
        raw.contains("Unexpected end", true) ->
        "教务返回的是网页而不是课表（当前入口没放行接口）。请在页面里进入「教务系统」后再读取；校外建议连校园网用直连同步。"
    raw.contains("Failed to fetch", true) || raw.contains("NetworkError", true) ->
        "网络请求失败（可能是断网或跨域拦截），换网络或入口后重试。"
    raw.contains("HTTP 5", true) || raw.contains("HTTP 4", true) ->
        "学校服务器返回错误（$raw），稍后重试。"
    else -> raw
}

/** 使用浏览器登录态读取正方学期响应；不向网页注入具有本机权限的桥接对象。 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun TermImportScreen(
    repo: Repository,
    onBack: () -> Unit,
    onImported: (CalendarInfo, List<CourseBlock>) -> Unit,
) {
    val ds = LocalDs.current
    val cached = remember { repo.store.loadCalendar() }
    var year by remember { mutableStateOf(cached?.year ?: LocalDate.now().let { if (it.monthValue < 8) it.year - 1 else it.year }.toString()) }
    var semester by remember { mutableStateOf(cached?.semester ?: "3") }
    var start by remember { mutableStateOf(repo.store.termStartDate.ifBlank { cached?.startDate.orEmpty() }) }
    var weeks by remember { mutableStateOf((repo.store.termWeeks.takeIf { it > 0 } ?: cached?.allWeek ?: 20).toString()) }
    var web by remember { mutableStateOf<WebView?>(null) }
    var page by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var browser by remember { mutableStateOf(false) }
    var importTarget by remember { mutableStateOf<CalendarInfo?>(null) }
    var requestedSession by remember { mutableStateOf<TeachingSession?>(null) }
    var loadProgress by remember { mutableIntStateOf(0) }
    var progressAt by remember { mutableLongStateOf(0L) }
    // 网页内扫码需要摄像头：等网页真的申请权限时再问用户，不提前骚扰
    val hostContext = androidx.compose.ui.platform.LocalContext.current
    var pendingPermission by remember { mutableStateOf<android.webkit.PermissionRequest?>(null) }
    val cameraLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission(),
    ) { granted ->
        val request = pendingPermission
        pendingPermission = null
        if (request != null) {
            if (granted) request.grant(request.resources) else {
                request.deny()
                message = "网页扫码需要相机权限，已拒绝；可继续用其他方式登录。"
            }
        }
    }

    fun schoolUrl(url: String): Boolean = runCatching {
        val uri = URI(url)
        val host = uri.host.orEmpty().lowercase()
        uri.scheme in listOf("https", "http") &&
            (host == "gdust.edu.cn" || host.endsWith(".gdust.edu.cn") || host == "172.16.254.1")
    }.getOrDefault(false)

    BackHandler {
        if (web?.canGoBack() == true) web?.goBack() else onBack()
    }
    DisposableEffect(Unit) {
        onDispose {
            CookieManager.getInstance().flush()
            web?.stopLoading(); web?.destroy(); web = null
        }
    }
    // 表单与浏览器两个分支共享的状态在外层，内容拆成两个内部组合
    @Composable
    fun ImportForm() {
        Column(Modifier.fillMaxSize().background(ds.bg).statusBarsPadding().navigationBarsPadding().imePadding().padding(horizontal = 16.dp)) {
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                Spacer(Modifier.height(16.dp))
                PageTitle(
                    "教务学期导入",
                    "按拾光 GDUST 适配的正方接口读取 · ${appVersionLabel(hostContext)}",
                    onBack,
                )
                Text(
                    "这是兼容备用路线：在网页里登录教务后读取原始周次。\n" +
                        "扫码登录不用走这里——回登录页点「生成登录二维码」，直接、免验证码。",
                    color = ds.sub, fontSize = 13.sp, lineHeight = 18.sp,
                )
                Spacer(Modifier.height(16.dp))
                InputField(year, { year = it }, "起始学年，如 2026", KeyboardType.Number)
                Spacer(Modifier.height(10.dp))
                SegmentedChoice(listOf("3" to "第一学期", "12" to "第二学期"), semester, { semester = it })
                Spacer(Modifier.height(10.dp))
                InputField(start, { start = it }, "第一周周一，如 2026-08-31")
                Spacer(Modifier.height(10.dp))
                InputField(weeks, { weeks = it }, "学期总周数", KeyboardType.Number)
                Spacer(Modifier.height(16.dp))
                Button("登录教务并导入") {
                    runCatching {
                        require(year.matches(Regex("[0-9]{4}"))) { "请输入四位起始学年（如 2026）" }
                        require(start.isNotBlank()) { "请填写第一周周一日期（如 2026-08-31）" }
                        val date = runCatching { LocalDate.parse(start.trim()) }.getOrElse {
                            error("开学日期格式不对，应为 2026-08-31 这种")
                        }
                        require(date.dayOfWeek.value == 1) { "开学日期须为周一" }
                        val total = weeks.toIntOrNull() ?: error("请输入总周数")
                        require(total in 1..60) { "总周数须为 1–60" }
                        val current = (ChronoUnit.DAYS.between(date, LocalDate.now()) / 7 + 1).toInt().coerceIn(1, total)
                        importTarget = CalendarInfo(current, total, date.toString(), year, semester)
                        browser = true
                        message = "正在打开学校门户…请在门户里进入教务系统，再点下方读取学期课表。"
                    }.onFailure { message = it.message ?: "学期设置无效" }
                }
                if (message.isNotEmpty()) Text(message, color = ds.danger, fontSize = 12.sp, modifier = Modifier.padding(top = 12.dp))
            }
        }
    }

    @Composable
    fun ImportBrowser() {
        Column(Modifier.fillMaxSize().background(ds.bg).statusBarsPadding().navigationBarsPadding().padding(horizontal = 16.dp)) {
            Text(
                "教务处 · 当前入口：${when {
                    page.contains("webvpn.gdust.edu.cn") -> "WebVPN（仅校外使用）"
                    page.contains("portal.gdust.edu.cn") -> "学校门户"
                    page.contains("gkjx.gdust.edu.cn") || page.contains("172.16.254.1") -> "教务系统直连"
                    else -> "学校门户"
                }}",
                color = ds.text, fontSize = 16.sp,
                modifier = Modifier.padding(vertical = 10.dp),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.weight(1f)) { Button("返回") { onBack() } }
                Box(Modifier.weight(1.3f)) { Button("学校门户") { web?.loadUrl(PORTAL_ENTRY) } }
                Box(Modifier.weight(1f)) { Button("WebVPN") { web?.loadUrl(VPN_LOGIN) } }
            }
            Text(
                "校园网内请用「学校门户」（WebVPN 会拒绝校园网 IP）；校外网络才点 WebVPN。\n" +
                    "要扫码登录请回登录页用 App 自带的二维码——这个网页不提供扫码。",
                color = ds.sub, fontSize = 11.sp, lineHeight = 15.sp,
                modifier = Modifier.padding(top = 4.dp),
            )
            Text(runCatching { URI(page).host }.getOrNull().orEmpty(), color = ds.sub, fontSize = 11.sp)
            AndroidView(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                factory = { context ->
                    WebView(context).apply {
                        CookieManager.getInstance().setAcceptCookie(true)
                        // 学校登录页的扫码二维码多为跨域 iframe/canvas：必须允许第三方 Cookie 与混合内容
                        CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                        settings.userAgentString = "Mozilla/5.0 (Linux; Android 13; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
                        settings.loadWithOverviewMode = true
                        settings.useWideViewPort = true
                        settings.domStorageEnabled = true
                        settings.databaseEnabled = true
                        settings.javaScriptCanOpenWindowsAutomatically = true
                        settings.setSupportMultipleWindows(false)
                        settings.mediaPlaybackRequiresUserGesture = false
                        settings.allowFileAccess = false
                        settings.allowContentAccess = false
                        webViewClient = object : WebViewClient() {
                            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                                // 允许所有 http/https 在 WebView 内继续（学校门户二级页、钉钉授权页、SSO 跳转都要能走）
                                val scheme = request.url.scheme.orEmpty().lowercase()
                                if (scheme == "http" || scheme == "https") return false
                                // 非 http（dingtalk:// alipays:// 等）交系统打开，别让 WebView 卡在加载中
                                runCatching {
                                    view.context.startActivity(
                                        android.content.Intent(android.content.Intent.ACTION_VIEW, request.url)
                                    )
                                }
                                message = "已用系统打开 $scheme 链接；页面内容若已出来可直接点下方读取。"
                                return true
                            }
                            override fun onPageStarted(view: WebView, url: String, favicon: android.graphics.Bitmap?) {
                                page = url
                                message = "正在加载学校页面…"
                            }
                            override fun onPageFinished(view: WebView, url: String) {
                                page = url
                                val uri = runCatching { URI(url) }.getOrNull()
                                val host = uri?.host.orEmpty().lowercase()
                                if (host.contains("kbcx") || url.contains("/kbcx/")) {
                                    // 页面真正进入正方教务后，记住入口；Cookie 由 Android CookieManager 自己持久化
                                    runCatching { repo.store.saveWebImport(url) }
                                }
                                if (message == "正在加载学校页面…") {
                                    message = "页面已载入。请在门户中进入教务系统，再点下方「读取学期课表」。"
                                }
                                // WebVPN 在校园网 IP 下会返回拒绝页；把网页内容翻译成明确提示
                                view.evaluateJavascript(
                                    "(document.body&&document.body.innerText||'').slice(0,400)",
                                ) { text ->
                                    if (text != null && (text.contains("拒绝登录") || text.contains("校园网"))) {
                                        message = "WebVPN 拒绝了校园网 IP：这是学校的正常策略，不是 App 故障。请点「学校门户」，从门户进入教务系统。"
                                    }
                                }
                            }
                            override fun onReceivedSslError(
                                view: WebView,
                                handler: android.webkit.SslErrorHandler,
                                error: android.net.http.SslError,
                            ) {
                                // 学校站点证书链偶尔不全：仅对校方域名放行，其他域一律取消
                                val host = runCatching { URI(error.url).host.orEmpty() }.getOrDefault("")
                                if (host == "gdust.edu.cn" || host.endsWith(".gdust.edu.cn") || host == "172.16.254.1") {
                                    handler.proceed()
                                } else {
                                    handler.cancel()
                                    message = "证书校验失败：$host（已阻止）"
                                }
                            }
                            override fun onReceivedError(view: WebView, request: WebResourceRequest, error: android.webkit.WebResourceError) {
                                if (request.isForMainFrame) message = "学校页面加载失败：${error.description}。可尝试上方门户入口。"
                            }
                            override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, errorResponse: android.webkit.WebResourceResponse) {
                                if (request.isForMainFrame) message = "学校页面返回 HTTP ${errorResponse.statusCode}，请稍后重试或尝试门户入口。"
                            }
                        }
                        webChromeClient = object : android.webkit.WebChromeClient() {
                            override fun onProgressChanged(view: WebView, newProgress: Int) {
                                loadProgress = newProgress
                                progressAt = System.currentTimeMillis()
                                if (newProgress in 1..99) {
                                    message = "正在加载学校页面… $newProgress%（页面内容出来即可点下方读取）"
                                }
                            }
                            override fun onPermissionRequest(request: android.webkit.PermissionRequest) {
                                val wantsCamera = request.resources.any {
                                    it == android.webkit.PermissionRequest.RESOURCE_VIDEO_CAPTURE
                                }
                                val granted = pendingPermission == null && androidx.core.content.ContextCompat.checkSelfPermission(
                                    hostContext, android.Manifest.permission.CAMERA,
                                ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                                if (wantsCamera && !granted) {
                                    // 交给系统弹窗，结果在 cameraLauncher 里 grant/deny
                                    pendingPermission = request
                                    cameraLauncher.launch(android.Manifest.permission.CAMERA)
                                } else {
                                    request.grant(request.resources)
                                }
                            }
                        }
                        web = this
                        val savedPage = repo.store.loadWebImportPage()
                        loadUrl(savedPage.takeIf(::schoolUrl) ?: PORTAL_ENTRY)
                    }
                },
            )
            // 卡住检测：进度停在 60~99 超过 8 秒，说明多半是统计/广告脚本挂住，页面其实已可用
            LaunchedEffect(loadProgress) {
                if (loadProgress !in 60..99) return@LaunchedEffect
                val stamp = progressAt
                kotlinx.coroutines.delay(8_000)
                if (progressAt == stamp && message.startsWith("正在加载学校页面")) {
                    message = "页面卡在 $loadProgress%：通常是学校页面的统计/广告脚本慢。若内容已经显示，直接点下方「读取学期课表」即可（读取不依赖加载完成）。"
                }
            }
            // 加载超过 20 秒仍未完成就给明确提示，不让用户盯着空白页猜
            LaunchedEffect(page) {
                if (page.isBlank() || message != "正在加载学校页面…") return@LaunchedEffect
                kotlinx.coroutines.delay(20_000)
                if (message == "正在加载学校页面…") {
                    message = "学校页面加载较慢或没有响应。可点「学校门户」重试；若显示「当前 IP 属于校园网」，属正常，请从门户进入教务系统。"
                }
            }
            Text(message, color = ds.sub, fontSize = 12.sp, modifier = Modifier.padding(vertical = 6.dp))
            Button(if (busy) "正在读取…" else "读取学期课表", enabled = !busy) {
                val uri = runCatching { URI(web?.url.orEmpty()) }.getOrNull()
                val host = uri?.host.orEmpty().lowercase()
                val path = when (host) {
                    "webvpn.gdust.edu.cn" -> VPN_SCHEDULE
                    "gkjx.gdust.edu.cn", "172.16.254.1" -> DIRECT_SCHEDULE
                    else -> null
                }
                if (path == null) {
                    message = if (host == "portal.gdust.edu.cn") {
                        "当前还在学校门户首页。请先在页面里点「教务系统 / 教学综合服务平台」，进入教务后再点读取。（门户登录态不等于教务会话）"
                    } else {
                        "请先从学校门户进入教务系统，或通过 WebVPN 完成登录并进入教务。"
                    }
                } else {
                    busy = true
                    message = "正在读取教务返回的实际周次…"
                    val target = requireNotNull(importTarget)
                    val endpoint = requireNotNull(uri).resolve(path).toString()
                    requestedSession = TeachingSession(
                        endpoint = endpoint,
                        cookie = CookieManager.getInstance().getCookie(endpoint).orEmpty(),
                        pageUrl = "${uri.scheme}://${uri.authority}${uri.path.orEmpty()}",
                    )
                    val body = "xnm=${target.year}&xqm=${target.semester}&kzlx=ck&xsdm=&kclbdm="
                    val js = """
                        (async function() {
                            const controller = new AbortController();
                            const timer = setTimeout(() => controller.abort(), 25000);
                            try {
                                const r = await fetch(${com.google.gson.Gson().toJson(path)}, {
                                    method:'POST', credentials:'include', signal:controller.signal,
                                    headers:{'Content-Type':'application/x-www-form-urlencoded;charset=UTF-8'},
                                    body:${com.google.gson.Gson().toJson(body)}
                                });
                                if (!r.ok) throw new Error('教务请求失败 HTTP ' + r.status);
                                const text = await r.text();
                                const t = text.trim();
                                if (t.startsWith('<')) {
                                    throw new Error('该入口返回的是网页而不是课表：多半是还没进「教务系统」，或 WebVPN 通道只放行网页。请在页面里进入教务系统后再读取；校外建议连校园网用直连同步。');
                                }
                                let data;
                                try { data = JSON.parse(text); } catch(e) {
                                    throw new Error('教务返回的不是课表数据（该入口未放行接口），请换入口或连校园网后重试。');
                                }
                                if (!Array.isArray(data.kbList)) throw new Error('未取得学期课表，请确认已进入正方教务');
                                return JSON.stringify({ok:true,data:data});
                            } catch(e) { return JSON.stringify({ok:false,error:e.name === 'AbortError' ? '教务响应超时，请稍后重试' : e.message}); }
                            finally { clearTimeout(timer); }
                        })()
                    """.trimIndent()
                    // evaluateJavascript 不等待 Promise，先将结果保存在当前页面，再轮询读取。
                    web?.evaluateJavascript("window.__gdustImportResult=null; $js.then(v => {window.__gdustImportResult=v;});", null)
                }
            }
            Spacer(Modifier.height(10.dp))
        }
    }

    // 轮询读取网页内 fetch 结果（evaluateJavascript 不等待 Promise）
    LaunchedEffect(busy) {
        if (!busy) return@LaunchedEffect
        repeat(60) {
            kotlinx.coroutines.delay(500)
            web?.evaluateJavascript("window.__gdustImportResult || null") { value ->
                if (busy && value != "null") {
                    busy = false
                    runCatching {
                        val envelope = JsonParser.parseString(JsonParser.parseString(value).asString).asJsonObject
                        check(envelope.get("ok")?.asBoolean == true) {
                            envelope.get("error")?.asString ?: "读取失败"
                        }
                        val target = requireNotNull(importTarget)
                        val blocks = ZfScheduleParser.parse(envelope.get("data").toString(), target)
                        repo.store.saveCalendar(target)
                        repo.store.saveBlocks(blocks, "zf_term")
                        requestedSession?.pageUrl?.let(repo.store::saveWebImport)
                        CookieManager.getInstance().flush()
                        onImported(target, blocks)
                    }.onFailure { message = localizeImportError(it.message ?: "课表解析失败，旧课表已保留") }
                }
            }
            if (!busy) return@LaunchedEffect
        }
        busy = false
        message = "教务读取超时，旧课表已保留，请重试。"
    }

    // 左滑返回：仅在设置表单页生效（浏览器页交给 WebView 处理）
    val density = androidx.compose.ui.platform.LocalDensity.current
    val importBackThreshold = with(density) { 96.dp.toPx() }
    var importBackPx by remember { mutableStateOf(Float.NaN) }
    if (!browser) {
        androidx.compose.foundation.layout.Box(
            Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragStart = { start -> importBackPx = if (start.x < 80f) 0f else Float.NaN },
                        onDragEnd = {
                            if (!importBackPx.isNaN() && importBackPx >= importBackThreshold) onBack()
                            importBackPx = 0f
                        },
                        onDragCancel = { importBackPx = 0f },
                    ) { change, dragAmount ->
                        if (!importBackPx.isNaN()) importBackPx += dragAmount
                        change.consume()
                    }
                },
        ) {
            ImportForm()
        }
    } else {
        ImportBrowser()
    }
}
