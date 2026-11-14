package com.menglimi.gdust.v2.ui.screens

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.net.http.SslError
import android.os.Bundle
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.SslErrorHandler
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.gson.JsonParser
import com.menglimi.gdust.v2.App
import com.menglimi.gdust.v2.data.ChxSso
import com.menglimi.gdust.v2.data.ChxWebSession
import com.menglimi.gdust.v2.data.Repository
import com.menglimi.gdust.v2.ui.AppTheme
import com.menglimi.gdust.v2.ui.LocalDs
import com.menglimi.gdust.v2.ui.rememberUiStyle
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Close
import top.yukonga.miuix.kmp.icon.extended.Refresh

/** 独立承载学校 CAS 与超星网页登录的 WebView，使用应用既有主题与 CookieManager。 */
class ChxWebActivity : ComponentActivity() {
    companion object {
        const val EXTRA_URL = "url"
        const val EXTRA_LOGOUT = "chx_logout"
        const val EXTRA_LOGOUT_CONFIRMED = "chx_logout_confirmed"
        const val EXTRA_SESSION_SAVED = "chx_session_saved"
    }

    private lateinit var repo: Repository
    private lateinit var web: WebView
    private var hostLabel by mutableStateOf("网页登录")
    private var pageMessage by mutableStateOf("")
    private var pageError by mutableStateOf<String?>(null)
    private var pageProgress by mutableIntStateOf(0)
    private var logout = false
    private var logoutConfirmed = false
    private var currentPage: String? = null
    private var pageVersion = 0
    private var mainPageFailed = false
    private var logoutAttemptFailed = false
    private var sentCasTicket = false

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        repo = (application as App).repo
        val initialUrl = intent.getStringExtra(EXTRA_URL) ?: ChxSso.fyssoUrl
        logout = intent.getBooleanExtra(EXTRA_LOGOUT, false) || ChxSso.isSchoolLogoutUrl(initialUrl)
        setResult(RESULT_CANCELED, Intent().putExtra(EXTRA_LOGOUT_CONFIRMED, false))
        web = WebView(this)
        configure(web)
        onBackPressedDispatcher.addCallback(this) { navigateBack() }
        setContent {
            AppTheme(rememberUiStyle(repo.store), monetEnabled = repo.store.monetEnabled) {
                val ds = LocalDs.current
                Column(Modifier.fillMaxSize().background(ds.bg).safeDrawingPadding().imePadding()) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = ::navigateBack, modifier = Modifier.size(48.dp)) {
                            Icon(MiuixIcons.Back, "返回上一页", Modifier.size(24.dp), tint = ds.text)
                        }
                        Text(hostLabel, color = ds.text, fontSize = 14.sp, maxLines = 2,
                            overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        IconButton(onClick = ::reloadPage, modifier = Modifier.size(48.dp)) {
                            Icon(MiuixIcons.Refresh, "重新加载网页", Modifier.size(24.dp), tint = ds.text)
                        }
                        IconButton(onClick = ::finishBrowsing, modifier = Modifier.size(48.dp)) {
                            Icon(MiuixIcons.Close, "关闭网页", Modifier.size(24.dp), tint = ds.text)
                        }
                    }
                    if (pageProgress < 100) {
                        LinearProgressIndicator(progress = { pageProgress / 100f }, color = ds.accent,
                            trackColor = ds.breakBar, modifier = Modifier.fillMaxWidth())
                    }
                    if (pageMessage.isNotBlank() || pageError != null) {
                        Column(Modifier.fillMaxWidth().heightIn(max = 144.dp).verticalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp, vertical = 8.dp)) {
                            Text(pageError ?: pageMessage, color = if (pageError != null) ds.danger else ds.sub,
                                fontSize = 13.sp, lineHeight = 18.sp)
                        }
                    }
                    AndroidView(factory = { web }, modifier = Modifier.fillMaxWidth().weight(1f),
                        update = { it.setBackgroundColor(ds.bg.toArgb()) })
                }
            }
        }
        // 登出始终重新请求，不能用缓存中的成功页面确认这次服务器退出。
        if (logout || savedInstanceState?.getBundle("web_state")?.let(web::restoreState) == null) {
            if (logout) loadLogout(initialUrl) else web.loadUrl(initialUrl)
        } else {
            currentPage = web.url
            hostLabel = web.url?.let { Uri.parse(it).host } ?: "网页登录"
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBundle("web_state", Bundle().also { web.saveState(it) })
        super.onSaveInstanceState(outState)
    }

    override fun onResume() {
        super.onResume()
        if (::web.isInitialized) web.onResume()
    }

    override fun onPause() {
        if (::web.isInitialized) web.onPause()
        super.onPause()
    }

    override fun onStop() {
        runCatching { CookieManager.getInstance().flush() }
        super.onStop()
    }

    override fun onDestroy() {
        if (::web.isInitialized) {
            pageVersion++
            web.stopLoading()
            (web.parent as? ViewGroup)?.removeView(web)
            web.webChromeClient = null
            web.webViewClient = WebViewClient()
            web.removeAllViews()
            web.destroy()
        }
        super.onDestroy()
    }

    private fun navigateBack() {
        if (!logout && web.canGoBack()) web.goBack() else finishBrowsing()
    }

    private fun reloadPage() {
        pageVersion++
        logoutConfirmed = false
        logoutAttemptFailed = false
        web.stopLoading()
        if (logout) loadLogout(ChxSso.LOGOUT_URL) else web.reload()
    }

    private fun loadLogout(url: String) {
        // 空票据的通用退出页不能证明原学校会话已经被撤销。
        sentCasTicket = ChxSso.isSchoolLogoutUrl(url) &&
            ChxSso.hasCasTicket(CookieManager.getInstance().getCookie(url))
        web.loadUrl(url, mapOf("Cache-Control" to "no-cache"))
    }

    private fun finishBrowsing() {
        if (isFinishing || isDestroyed) return
        pageVersion++ // 关闭后到达的 JS/网络回调不再更新结果。
        web.stopLoading()
        runCatching { CookieManager.getInstance().flush() }
        // 普通登录页返回仅带“已保存”信息；已有 Cookie 不得把 RESULT_OK 当成已验证登录。
        setResult(if (logout && logoutConfirmed) RESULT_OK else RESULT_CANCELED,
            Intent().putExtra(EXTRA_LOGOUT_CONFIRMED, logout && logoutConfirmed)
                .putExtra(EXTRA_SESSION_SAVED, !logout && repo.store.hasChxSession))
        finish()
    }

    private fun saveCookieIfAny(): Boolean {
        val cookie = ChxSso.cookieProbes.firstNotNullOfOrNull { url ->
            CookieManager.getInstance().getCookie(url)?.takeIf(ChxSso::hasSessionCookie)
        } ?: return false
        if (repo.store.chxCookie != cookie) repo.store.saveChxCookie(cookie)
        return true
    }

    private fun showLoadError(message: String) {
        mainPageFailed = true
        if (logout) logoutAttemptFailed = true
        logoutConfirmed = false
        pageProgress = 100
        pageError = message + if (logout) " 学校服务器退出未确认，可重试或关闭后清理本地会话。" else " 可点刷新重试。"
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun configure(view: WebView) {
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(view, true)
        view.settings.javaScriptEnabled = true
        view.settings.domStorageEnabled = true
        view.settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
        view.settings.allowFileAccess = false
        view.settings.allowContentAccess = false
        if (logout) view.settings.cacheMode = WebSettings.LOAD_NO_CACHE
        view.settings.userAgentString =
            "Mozilla/5.0 (Linux; Android 13; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
        view.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val scheme = request.url.scheme.orEmpty().lowercase(java.util.Locale.ROOT)
                if (scheme == "http" || scheme == "https") return false
                if (logout || !request.isForMainFrame || scheme in setOf("file", "content", "javascript", "data", "intent")) {
                    showLoadError("当前网页的跳转无法在应用中完成。")
                    return true
                }
                val opened = runCatching { startActivity(Intent(Intent.ACTION_VIEW, request.url)) }.isSuccess
                if (opened) pageMessage = "已打开外部应用，返回本页继续。" else showLoadError("未找到可以打开该链接的应用。")
                return true
            }

            override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                pageVersion++
                currentPage = url
                mainPageFailed = logout && logoutAttemptFailed
                if (!mainPageFailed) pageError = null
                pageProgress = if (mainPageFailed) 100 else 0
                hostLabel = Uri.parse(url).host ?: "网页登录"
                ChxWebSession.recordVisited(url)
                pageMessage = if (logout) "正在请求学校退出；请等待页面确认。" else "请在学校或学习通网页完成认证。"
            }

            override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                if (request.isForMainFrame) showLoadError("网页加载失败，请检查网络连接。")
            }

            override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, response: WebResourceResponse) {
                if (request.isForMainFrame) showLoadError("网页请求失败（HTTP ${response.statusCode}）。")
            }

            override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) {
                handler.cancel()
                showLoadError("网页安全证书验证失败，已停止连接。")
            }

            override fun onPageFinished(view: WebView, url: String) {
                if (isFinishing || isDestroyed || currentPage != url) return
                pageProgress = 100
                if (mainPageFailed) return
                if (logout) {
                    val version = pageVersion
                    view.evaluateJavascript("document.body ? document.body.innerText : ''") { result ->
                        if (isFinishing || isDestroyed || version != pageVersion || view.url != url) return@evaluateJavascript
                        val text = runCatching { JsonParser.parseString(result).asString }.getOrNull()
                        logoutConfirmed = ChxSso.confirmsSchoolLogout(url, text, !mainPageFailed && !logoutAttemptFailed, sentCasTicket)
                        if (logoutConfirmed) finishBrowsing()
                        else pageMessage = "学校服务器退出尚未确认。可刷新重试，或关闭网页后清理本地会话。"
                    }
                    return
                }
                pageMessage = if (saveCookieIfAny()) "会话已保存，返回后可检查是否有效。" else "尚未取得可验证的学习通会话。"
            }
        }
        view.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView, newProgress: Int) {
                if (!isFinishing && !isDestroyed && !mainPageFailed) pageProgress = newProgress.coerceIn(0, 100)
            }
        }
    }
}
