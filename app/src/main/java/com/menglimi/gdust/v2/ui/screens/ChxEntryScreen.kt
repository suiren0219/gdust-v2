package com.menglimi.gdust.v2.ui.screens

import android.net.Uri
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
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
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
 * 学习通联动：同一 WebView + 同一 CAS 会话，登录一次后学习通与教务网页都不再要密码。
 * 只保存 CAS 放行后的超星 Cookie（加密存储），不保存密码。
 */
@Composable
fun ChxEntryScreen(repo: Repository, onBack: () -> Unit) {
    val ds = LocalDs.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var web by remember { mutableStateOf<WebView?>(null) }
    var message by remember { mutableStateOf("点「学习通」开始；没登录过会跳到学校统一身份认证。") }
    var isError by remember { mutableStateOf(false) }
    var progress by remember { mutableIntStateOf(0) }
    var host by remember { mutableStateOf("") }
    var linked by remember { mutableStateOf(repo.store.hasChxSession) }
    var checking by remember { mutableStateOf(false) }
    var checkResult by remember { mutableStateOf<Boolean?>(null) }

    androidx.activity.compose.BackHandler { onBack() }

    // 离开页面时把 CookieManager 落盘，下次前台拉起仍能复用 CAS / 超星会话
    androidx.compose.runtime.DisposableEffect(Unit) {
        onDispose { CookieManager.getInstance().flush() }
    }

    fun saveCookieIfAny(): Boolean {
        val cookie = ChxSso.cookieProbes.firstNotNullOfOrNull { url ->
            CookieManager.getInstance().getCookie(url)?.takeIf { ChxSso.hasSessionCookie(it) }
        } ?: return false
        repo.store.saveChxCookie(cookie)
        linked = true
        checkResult = null
        return true
    }

    fun open(url: String) {
        isError = false
        message = "正在打开…"
        web?.loadUrl(url)
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
                        androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
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
                        repo.store.clearChxCookie()
                        linked = false
                        checkResult = null
                        message = "已清除本机保存的学习通会话；网页里的登录态需要用右上角返回后清理浏览器数据，或退出学习通账号。"
                    }
                }
            }
        }

        Text(
            message,
            fontSize = 12.sp, lineHeight = 17.sp,
            color = if (isError) ds.danger else ds.sub,
            modifier = Modifier.padding(top = 8.dp),
        )
        if (host.isNotBlank()) {
            Text(host, fontSize = 10.sp, color = ds.sub, modifier = Modifier.padding(top = 2.dp))
        }
        if (progress in 1..99) {
            LinearProgressIndicator(
                progress = { progress / 100f },
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp).height(2.dp),
                color = ds.accent,
            )
        }

        Spacer(Modifier.height(8.dp))
        androidx.compose.ui.viewinterop.AndroidView(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            factory = {
                WebView(context).apply {
                    CookieManager.getInstance().setAcceptCookie(true)
                    CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                    settings.allowFileAccess = false
                    settings.allowContentAccess = false
                    settings.userAgentString =
                        "Mozilla/5.0 (Linux; Android 13; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
                    webViewClient = object : WebViewClient() {
                        // 研究线诊断：页面白屏时能看到子资源/主文档失败的具体 URL 与错误码
                        override fun onReceivedError(
                            view: WebView,
                            request: android.webkit.WebResourceRequest,
                            error: android.webkit.WebResourceError,
                        ) {
                            val u = request.url?.toString().orEmpty()
                            android.util.Log.w(
                                "ChxWeb",
                                "onReceivedError ${error.errorCode} ${error.description} " +
                                    "main=${request.isForMainFrame} $u",
                            )
                            if (request.isForMainFrame) {
                                isError = true
                                message = "页面加载失败（${error.description}）：$u"
                            }
                        }

                        override fun onReceivedHttpError(
                            view: WebView,
                            request: android.webkit.WebResourceRequest,
                            errorResponse: android.webkit.WebResourceResponse,
                        ) {
                            android.util.Log.w(
                                "ChxWeb",
                                "http ${errorResponse.statusCode} ${request.url} " +
                                    "main=${request.isForMainFrame}",
                            )
                        }

                        override fun shouldOverrideUrlLoading(
                            view: WebView,
                            request: android.webkit.WebResourceRequest,
                        ): Boolean {
                            val scheme = request.url.scheme.orEmpty().lowercase()
                            if (scheme == "http" || scheme == "https") return false
                            // chaoxing:// 等唤起客户端的链接交系统，别让 WebView 卡住
                            runCatching {
                                view.context.startActivity(
                                    android.content.Intent(android.content.Intent.ACTION_VIEW, request.url),
                                )
                            }
                            message = "已用系统打开 $scheme 链接，回到本页继续即可。"
                            return true
                        }

                        override fun onPageFinished(view: WebView, url: String) {
                            host = runCatching { Uri.parse(url).host.orEmpty() }.getOrDefault("")
                            progress = 100
                            android.util.Log.i("ChxWeb", "onPageFinished $url")
                            // 每次落地都试存一次：CAS 跳转链里可能最后一跳才拿到超星 Cookie
                            val saved = saveCookieIfAny()
                            val onChaoxing = ChxSso.isChaoxingHost(host)
                            when {
                                saved && onChaoxing -> {
                                    isError = false
                                    message = "已进入学习通并拿到会话，之后不用再登录。"
                                }
                                saved -> message = "学习通会话已保存（CAS 已放行）。"
                                onChaoxing -> {
                                    isError = true
                                    message = "已进入超星域名，但没拿到会话：可能停在了登录页，请先完成登录。"
                                }
                                host.endsWith("cas.gdust.edu.cn") -> {
                                    isError = false
                                    message = "学校统一身份认证页：登录一次，学习通和教务网页都不用再输密码。"
                                }
                            }
                        }

                        override fun onReceivedSslError(
                            view: WebView,
                            handler: android.webkit.SslErrorHandler,
                            error: android.net.http.SslError,
                        ) {
                            // 只对校方与超星域放行证书链，其他域一律阻止
                            val h = runCatching { Uri.parse(error.url).host.orEmpty() }.getOrDefault("")
                            android.util.Log.w(
                                "ChxWeb",
                                "sslError ${error.primaryError} $h ${error.url}",
                            )
                            val allowed = h.endsWith("gdust.edu.cn") || ChxSso.isChaoxingHost(h)
                            if (allowed) handler.proceed() else {
                                handler.cancel()
                                isError = true
                                message = "证书校验失败：$h（已阻止）"
                            }
                        }
                    }
                    webChromeClient = object : WebChromeClient() {
                        override fun onProgressChanged(view: WebView, newProgress: Int) {
                            progress = newProgress
                        }

                        // WebView 默认吞掉 JS 报错；白屏排障必须把 console 消息转出来
                        override fun onConsoleMessage(msg: android.webkit.ConsoleMessage): Boolean {
                            android.util.Log.d(
                                "ChxWeb",
                                "console ${msg.messageLevel()} ${msg.message()} " +
                                    "@${msg.sourceId()}:${msg.lineNumber()}",
                            )
                            return true
                        }
                    }
                    web = this
                    loadUrl(ChxSso.fyssoUrl)
                }
            },
        )
    }
}
