package com.menglimi.study.learning

import android.annotation.SuppressLint
import android.net.http.SslError
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.CookieManager
import android.webkit.PermissionRequest
import android.webkit.ServiceWorkerClient
import android.webkit.ServiceWorkerController
import android.webkit.SslErrorHandler
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.menglimi.chaoxing.ChxSso
import com.menglimi.ksu.ui.Glyph
import com.menglimi.ksu.ui.KIcon
import com.menglimi.ksu.ui.KsuTheme
import com.menglimi.study.StudyApplication
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.io.ByteArrayInputStream

/** 正常官方网页登录和原页面查看；无桥接、无脚本注入、无自动做题/提交。 */
class LearningWebActivity : ComponentActivity() {
    private val app get() = application as StudyApplication
    private val controller get() = app.learning
    private lateinit var webView: WebView
    private var message by mutableStateOf("请在学习通网页完成登录，再点“完成检查”。")
    private var progress by mutableFloatStateOf(0f)
    private var ready by mutableStateOf(false)
    private var checking by mutableStateOf(false)
    private var preparing = false
    @Volatile private var blocked = false
    @Volatile private var destroyed = false
    @Volatile private var sessionGeneration = -1L
    private var loginPrepared = false
    private var lastSafeUrl = LearningUrls.LOGIN
    private val invalidateBrowser: () -> Unit = { destroyBrowser(); finish() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        webView = WebView(this).apply { importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS }
        configureWebView()
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = navigateBack()
        })
        setContent {
            val appearance by app.appearance.state.collectAsStateWithLifecycle()
            KsuTheme(appearance) {
                val colors = MaterialTheme.colorScheme
                val dark = colors.background.luminance() < .5f
                SideEffect {
                    WindowCompat.getInsetsController(window, window.decorView).apply {
                        isAppearanceLightStatusBars = !dark
                        isAppearanceLightNavigationBars = !dark
                    }
                    window.isNavigationBarContrastEnforced = false
                    if (!destroyed) {
                        webView.setBackgroundColor(colors.background.toArgb())
                        webView.settings.isAlgorithmicDarkeningAllowed = dark
                    }
                }
                Column(Modifier.fillMaxSize().background(colors.background)
                    .windowInsetsPadding(WindowInsets.safeDrawing).imePadding()) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = ::navigateBack) { KIcon(Glyph.BACK, description = "返回") }
                        Column(Modifier.weight(1f).padding(start = 4.dp)) {
                            Text("学习通网页", style = MaterialTheme.typography.titleLarge)
                            Text("官方页面 · 本应用不自动执行提交", fontSize = 12.sp, color = colors.onSurfaceVariant)
                        }
                        IconButton(onClick = { finish() }) { KIcon(Glyph.CLOSE, description = "关闭网页") }
                    }
                    if (checking) LinearProgressIndicator(Modifier.fillMaxWidth())
                    else if (progress < 1f) LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                    AndroidView(factory = { webView }, modifier = Modifier.fillMaxWidth().weight(1f))
                    Surface(color = colors.surfaceContainerLow) {
                        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(message, fontSize = 13.sp, lineHeight = 20.sp, color = colors.onSurfaceVariant)
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                TextButton(onClick = { loadTrusted(lastSafeUrl) }, enabled = ready && !checking,
                                    modifier = Modifier.weight(1f)) { Text("重新载入") }
                                Button(onClick = ::verifySession, enabled = ready && !checking,
                                    modifier = Modifier.weight(1f)) { Text("完成检查") }
                            }
                        }
                    }
                }
            }
        }
        val detail = intent.getStringExtra("url")
        if (detail != null && LearningUrls.trusted(detail) == null) {
            message = "无法打开这个地址。仅支持安全的学习通 HTTPS 网页。"
            return
        }
        lifecycleScope.launch {
            if (detail == null) { controller.prepareLogin(); loginPrepared = true }
            if (isFinishing || destroyed) return@launch
            sessionGeneration = controller.attachBrowser(invalidateBrowser)
            ready = true
            loadTrusted(detail ?: LearningUrls.LOGIN)
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    @Suppress("DEPRECATION")
    private fun configureWebView() {
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = false
            allowContentAccess = false
            allowFileAccessFromFileURLs = false
            allowUniversalAccessFromFileURLs = false
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            javaScriptCanOpenWindowsAutomatically = false
            setSupportMultipleWindows(false)
            cacheMode = WebSettings.LOAD_NO_CACHE
            saveFormData = false
            savePassword = false
            mediaPlaybackRequiresUserGesture = true
            setGeolocationEnabled(false)
            safeBrowsingEnabled = true
        }
        CookieManager.getInstance().apply { setAcceptCookie(true); setAcceptThirdPartyCookies(webView, false) }
        ServiceWorkerController.getInstance().apply {
            serviceWorkerWebSettings.apply { allowFileAccess = false; allowContentAccess = false; blockNetworkLoads = true }
            setServiceWorkerClient(object : ServiceWorkerClient() {
                override fun shouldInterceptRequest(request: WebResourceRequest): WebResourceResponse = blockedResponse()
            })
        }
        webView.setDownloadListener { _, _, _, _, _ -> message = "此页面用于登录与查看课程作业，暂不支持下载。" }
        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView, newProgress: Int) { if (!checking) progress = newProgress / 100f }
            override fun onPermissionRequest(request: PermissionRequest) = request.deny()
        }
        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
                interceptNavigation(request.url.toString(), request.isForMainFrame)
            override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean = interceptNavigation(url, true)
            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? =
                if (destroyed || blocked || !controller.browserIsCurrent(sessionGeneration) ||
                    LearningUrls.trusted(request.url.toString()) == null) blockedResponse() else null
            override fun onPageStarted(view: WebView, url: String, favicon: android.graphics.Bitmap?) {
                if (LearningUrls.trusted(url) == null) {
                    view.stopLoading(); message = "已阻止不受信任的网页跳转。"
                } else lastSafeUrl = url
            }
            override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) {
                handler.cancel(); message = "网页安全连接校验失败，已停止加载，请稍后重试。"
            }
            override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                if (request.isForMainFrame && !checking) message = "网页暂时无法打开，请检查网络后重新载入。"
            }
            override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, error: WebResourceResponse) {
                if (request.isForMainFrame) message = "网页服务暂时不可用，请稍后重新载入。"
            }
        }
    }

    private fun interceptNavigation(url: String, mainFrame: Boolean): Boolean {
        if (checking || preparing || destroyed || !controller.browserIsCurrent(sessionGeneration)) return true
        if (LearningUrls.trusted(url) == null) {
            if (mainFrame) message = "已阻止不受信任的地址，请继续使用学习通官方网页。"
            return true
        }
        if (mainFrame && !loginPrepared && ChxSso.isLoginUrl(url)) {
            preparing = true; blocked = true; ready = false
            controller.detachBrowser(invalidateBrowser)
            webView.stopLoading()
            webView.settings.javaScriptEnabled = false
            lifecycleScope.launch {
                controller.prepareLogin()
                if (!destroyed && !isFinishing) {
                    loginPrepared = true
                    sessionGeneration = controller.attachBrowser(invalidateBrowser)
                    preparing = false; blocked = false; ready = true
                    webView.settings.javaScriptEnabled = true
                    loadTrusted(url)
                }
            }
            return true
        }
        return false
    }
    private fun loadTrusted(url: String) {
        val trusted = LearningUrls.trusted(url) ?: return
        if (destroyed || !ready || checking || !controller.browserIsCurrent(sessionGeneration)) return
        lastSafeUrl = trusted
        message = "操作完成后可点“完成检查”更新作业。本应用不代做、不自动提交。"
        progress = 0f
        webView.loadUrl(trusted)
    }
    private fun verifySession() {
        if (checking || preparing || destroyed || !controller.browserIsCurrent(sessionGeneration)) return
        checking = true; blocked = true
        webView.stopLoading(); webView.onPause()
        webView.settings.javaScriptEnabled = false
        webView.visibility = View.INVISIBLE
        message = "正在确认学习通会话并读取作业…"
        lifecycleScope.launch {
            try {
                CookieManager.getInstance().flush()
                controller.refresh()
                if (!destroyed && controller.browserIsCurrent(sessionGeneration)) {
                    val result = controller.state.value
                    if (result.connected) { setResult(RESULT_OK); finish() }
                    else message = result.error ?: "尚未确认登录，请在官方网页完成登录后重试。"
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { if (!destroyed) message = "检查未完成，请稍后重试。" }
            finally {
                checking = false; blocked = false
                if (!destroyed && !isFinishing) {
                    webView.settings.javaScriptEnabled = true
                    webView.visibility = View.VISIBLE
                    webView.onResume(); progress = 1f
                }
            }
        }
    }
    private fun navigateBack() {
        if (!checking && !preparing && !destroyed && webView.canGoBack()) webView.goBack() else finish()
    }
    override fun onResume() {
        super.onResume()
        if (::webView.isInitialized && !destroyed && !checking) webView.onResume()
    }
    override fun onPause() {
        if (::webView.isInitialized && !destroyed) { webView.onPause(); CookieManager.getInstance().flush() }
        super.onPause()
    }
    override fun onDestroy() {
        controller.detachBrowser(invalidateBrowser)
        destroyBrowser()
        super.onDestroy()
    }
    private fun destroyBrowser() {
        if (destroyed || !::webView.isInitialized) return
        destroyed = true; blocked = true; ready = false
        webView.stopLoading(); webView.settings.javaScriptEnabled = false
        webView.clearFormData(); webView.clearHistory(); webView.clearCache(true)
        (webView.parent as? ViewGroup)?.removeView(webView)
        webView.destroy()
    }
    private companion object {
        fun blockedResponse() = WebResourceResponse("text/plain", "UTF-8", 403, "Blocked", emptyMap(), ByteArrayInputStream(ByteArray(0)))
    }
}
