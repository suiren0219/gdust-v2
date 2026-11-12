package com.menglimi.gdust.v2.ui.screens

import android.net.Uri
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.menglimi.gdust.v2.data.ChxSso
import com.menglimi.gdust.v2.data.Repository
import com.menglimi.gdust.v2.ui.LocalDs
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
    var web by remember { mutableStateOf<WebView?>(null) }
    var message by remember { mutableStateOf("点上方按钮进入学习通；没登录过会跳到学校统一身份认证。") }
    var cookieSaved by remember { mutableStateOf(repo.store.hasChxSession) }

    androidx.activity.compose.BackHandler { onBack() }

    fun probe() {
        val probe = ChxSso.cookieProbes.firstNotNullOfOrNull { url ->
            CookieManager.getInstance().getCookie(url)?.takeIf { it.isNotBlank() }
        }
        if (probe.isNullOrBlank()) return
        repo.store.saveChxCookie(probe)
        cookieSaved = true
        message = "已拿到学习通会话，之后不用再登录。"
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(ds.bg)
            .statusBarsPadding()
            .padding(horizontal = 16.dp),
    ) {
        Spacer(Modifier.height(24.dp))
        PageTitle(title = "学习通联动", subtitle = "一次统一身份认证，学习通与教务共用", onBack = onBack)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            androidx.compose.foundation.layout.Box(Modifier.weight(1f)) {
                Button("学习通") { web?.loadUrl(ChxSso.fyssoUrl) }
            }
            androidx.compose.foundation.layout.Box(Modifier.weight(1f)) {
                Button("课程中心") { web?.loadUrl(ChxSso.mhUrl) }
            }
            androidx.compose.foundation.layout.Box(Modifier.weight(1f)) {
                Button("教务网页") { web?.loadUrl(ChxSso.teachingWebUrl) }
            }
        }
        Text(
            message,
            fontSize = 12.sp, lineHeight = 17.sp, color = if (cookieSaved) ds.accent else ds.sub,
            modifier = Modifier.padding(top = 8.dp),
        )
        Text(
            if (cookieSaved) {
                "会话已在 ${SimpleDateFormat("MM-dd HH:mm", Locale.CHINA).format(Date(repo.store.chxCookieAt))} 保存"
            } else {
                "只保存登录放行后的会话 Cookie，不保存密码"
            },
            fontSize = 11.sp, color = ds.sub, modifier = Modifier.padding(top = 2.dp),
        )
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
                            val host = runCatching { Uri.parse(url).host }.getOrNull()
                            if (ChxSso.isChaoxingHost(host)) {
                                probe()
                                if (!cookieSaved) message = "已进入学习通，但还没拿到会话；若停在登录页请先完成登录。"
                            }
                        }

                        override fun onReceivedSslError(
                            view: WebView,
                            handler: android.webkit.SslErrorHandler,
                            error: android.net.http.SslError,
                        ) {
                            // 只对校方与超星域放行证书链，其他域一律阻止
                            val host = runCatching { Uri.parse(error.url).host.orEmpty() }.getOrDefault("")
                            val allowed = host.endsWith("gdust.edu.cn") ||
                                ChxSso.isChaoxingHost(host)
                            if (allowed) handler.proceed() else {
                                handler.cancel()
                                message = "证书校验失败：$host（已阻止）"
                            }
                        }
                    }
                    web = this
                    loadUrl(ChxSso.fyssoUrl)
                }
            },
        )
    }
}
