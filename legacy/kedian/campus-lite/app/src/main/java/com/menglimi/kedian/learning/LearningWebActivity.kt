package com.menglimi.kedian.learning

import android.annotation.SuppressLint
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.http.SslError
import android.os.Bundle
import android.view.Gravity
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
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.menglimi.chaoxing.ChxSso
import com.menglimi.kedian.CampusApp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.io.ByteArrayInputStream

/** 普通学习通网页登录；不注入桥接、不收集输入、不根据页面落点推定登录成功。 */
class LearningWebActivity : ComponentActivity() {
    private val controller: LearningController get() = (application as CampusApp).learning
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var webView: WebView
    private lateinit var progress: ProgressBar
    private lateinit var message: TextView
    private lateinit var complete: Button
    private lateinit var retry: Button
    private lateinit var palette: Palette
    private var sessionGeneration = -1L
    private var loginPrepared = false
    @Volatile private var checking = false
    private var preparing = false
    @Volatile private var destroyed = false
    private var lastSafeUrl = LearningUrls.LOGIN
    private val invalidateBrowser: () -> Unit = {
        destroyBrowser()
        finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        palette = Palette(resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES)
        buildLayout()
        configureWebView()
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = navigateBack()
        })
        val detail = intent.getStringExtra("url")
        if (detail != null && LearningUrls.trusted(detail) == null) {
            message.text = "无法打开这个地址。仅支持安全的学习通 HTTPS 网页。"
            complete.isEnabled = false
            retry.isEnabled = false
            return
        }
        scope.launch {
            if (detail == null) {
                // 接入方应在 startActivity 前调用；这里也兜底，防止其他入口漏掉隔离。
                controller.prepareLogin()
                loginPrepared = true
            }
            if (isFinishing || destroyed) return@launch
            sessionGeneration = controller.attachBrowser(invalidateBrowser)
            complete.isEnabled = true
            retry.isEnabled = true
            loadTrusted(detail ?: LearningUrls.LOGIN)
        }
    }

    private fun buildLayout() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = !palette.dark
            isAppearanceLightNavigationBars = !palette.dark
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(palette.background)
        }
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            view.setPadding(bars.left, bars.top, bars.right, maxOf(bars.bottom, ime.bottom))
            insets
        }
        val toolbar = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(6), dp(16), dp(6))
            minimumHeight = dp(64)
            setBackgroundColor(palette.surface)
        }
        toolbar.addView(action("返回") { navigateBack() }, LinearLayout.LayoutParams(dp(72), dp(48)))
        toolbar.addView(TextView(this).apply {
            text = "学习通网页"
            textSize = 19f
            setTextColor(palette.text)
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            setPadding(dp(12), 0, 0, 0)
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        root.addView(toolbar)
        progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            progressTintList = ColorStateList.valueOf(palette.accent)
            indeterminateTintList = ColorStateList.valueOf(palette.accent)
        }
        root.addView(progress, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(3)))
        webView = WebView(this).apply {
            setBackgroundColor(palette.background)
            importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
        }
        root.addView(webView, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        val footer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(12), dp(20), dp(12))
            setBackgroundColor(palette.surface)
        }
        message = TextView(this).apply {
            text = "请在学习通网页完成登录，再点“完成检查”。"
            textSize = 13f
            setTextColor(palette.secondary)
            accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
        }
        footer.addView(message, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        val actions = LinearLayout(this).apply {
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
            setPadding(0, dp(8), 0, 0)
        }
        retry = action("重新载入") { if (!checking && !preparing) loadTrusted(lastSafeUrl) }.apply { isEnabled = false }
        actions.addView(retry, LinearLayout.LayoutParams(0, dp(48), 1f))
        complete = action("完成检查", primary = true) { verifySession() }.apply { isEnabled = false }
        actions.addView(complete, LinearLayout.LayoutParams(0, dp(48), 1f).apply { marginStart = dp(12) })
        footer.addView(actions)
        root.addView(footer)
        setContentView(root)
        ViewCompat.requestApplyInsets(root)
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
            isAlgorithmicDarkeningAllowed = palette.dark
        }
        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(webView, false)
        }
        // 不让离线 Service Worker 绕过页面的资源边界发请求。
        ServiceWorkerController.getInstance().apply {
            serviceWorkerWebSettings.apply {
                allowFileAccess = false
                allowContentAccess = false
                blockNetworkLoads = true
            }
            setServiceWorkerClient(object : ServiceWorkerClient() {
                override fun shouldInterceptRequest(request: WebResourceRequest): WebResourceResponse = blockedResponse()
            })
        }
        webView.setDownloadListener { _, _, _, _, _ ->
            message.text = "此页面仅供登录与查看作业，暂不支持下载。"
        }
        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView, newProgress: Int) {
                if (!checking) {
                    progress.isIndeterminate = false
                    progress.progress = newProgress
                    progress.visibility = if (newProgress < 100) View.VISIBLE else View.INVISIBLE
                }
            }
            override fun onPermissionRequest(request: PermissionRequest) = request.deny()
        }
        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
                interceptNavigation(request.url.toString(), request.isForMainFrame)

            override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean = interceptNavigation(url, true)

            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? =
                if (destroyed || checking || !controller.browserIsCurrent(sessionGeneration) ||
                    LearningUrls.trusted(request.url.toString()) == null) blockedResponse() else null

            override fun onPageStarted(view: WebView, url: String, favicon: android.graphics.Bitmap?) {
                if (LearningUrls.trusted(url) == null) {
                    view.stopLoading()
                    message.text = "已阻止不受信任的网页跳转。"
                } else {
                    lastSafeUrl = url
                }
            }

            override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) {
                handler.cancel()
                message.text = "网页安全连接校验失败，已停止加载，请稍后重试。"
            }

            override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                if (request.isForMainFrame && !checking) message.text = "网页暂时无法打开，请检查网络后重新载入。"
            }

            override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, response: WebResourceResponse) {
                if (request.isForMainFrame) message.text = "网页服务暂时不可用，请稍后重新载入。"
            }
        }
    }

    private fun interceptNavigation(url: String, mainFrame: Boolean): Boolean {
        if (checking || preparing || destroyed || !controller.browserIsCurrent(sessionGeneration)) return true
        if (LearningUrls.trusted(url) == null) {
            if (mainFrame) message.text = "已阻止不受信任的地址，请继续使用学习通网页。"
            return true
        }
        if (mainFrame && !loginPrepared && ChxSso.isLoginUrl(url)) {
            // 从详情返回登录也要先隔离，防止换号后继续显示原账号作业。
            preparing = true
            complete.isEnabled = false
            retry.isEnabled = false
            controller.detachBrowser(invalidateBrowser)
            webView.stopLoading()
            scope.launch {
                controller.prepareLogin()
                if (!destroyed && !isFinishing) {
                    loginPrepared = true
                    sessionGeneration = controller.attachBrowser(invalidateBrowser)
                    preparing = false
                    complete.isEnabled = true
                    retry.isEnabled = true
                    loadTrusted(url)
                }
            }
            return true
        }
        return false
    }

    private fun loadTrusted(url: String) {
        val trusted = LearningUrls.trusted(url) ?: return
        if (destroyed || !controller.browserIsCurrent(sessionGeneration)) return
        lastSafeUrl = trusted
        message.text = "请在学习通网页完成操作，再点“完成检查”。"
        progress.visibility = View.VISIBLE
        webView.loadUrl(trusted)
    }

    private fun verifySession() {
        if (checking || preparing || destroyed || !controller.browserIsCurrent(sessionGeneration)) return
        checking = true
        complete.isEnabled = false
        retry.isEnabled = false
        webView.stopLoading()
        webView.onPause()
        // 检查期间不让网页脚本或用户操作改动账号 Cookie。
        webView.settings.javaScriptEnabled = false
        webView.visibility = View.INVISIBLE
        progress.isIndeterminate = true
        progress.visibility = View.VISIBLE
        message.text = "正在检查学习通登录状态并读取作业…"
        scope.launch {
            try {
                CookieManager.getInstance().flush()
                controller.refresh()
                if (!destroyed && controller.browserIsCurrent(sessionGeneration)) {
                    val result = controller.state.value
                    if (result.connected) {
                        setResult(RESULT_OK)
                        finish()
                    } else {
                        message.text = result.error ?: "尚未确认学习通登录，请在网页完成登录后重试。"
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                if (!destroyed) message.text = "检查未完成，请稍后重试。"
            } finally {
                checking = false
                if (!destroyed && !isFinishing) {
                    webView.settings.javaScriptEnabled = true
                    webView.visibility = View.VISIBLE
                    webView.onResume()
                    complete.isEnabled = true
                    retry.isEnabled = true
                    progress.isIndeterminate = false
                    progress.visibility = View.INVISIBLE
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
        if (::webView.isInitialized && !destroyed) {
            webView.onPause()
            CookieManager.getInstance().flush()
        }
        super.onPause()
    }

    override fun onDestroy() {
        scope.cancel()
        controller.detachBrowser(invalidateBrowser)
        destroyBrowser()
        super.onDestroy()
    }

    private fun destroyBrowser() {
        if (destroyed || !::webView.isInitialized) return
        destroyed = true
        webView.stopLoading()
        webView.settings.javaScriptEnabled = false
        webView.clearFormData()
        webView.clearHistory()
        webView.clearCache(true)
        (webView.parent as? ViewGroup)?.removeView(webView)
        webView.destroy()
    }

    private fun action(label: String, primary: Boolean = false, click: () -> Unit) = Button(this).apply {
        text = label
        isAllCaps = false
        textSize = 15f
        setTextColor(ColorStateList(
            arrayOf(intArrayOf(android.R.attr.state_enabled), intArrayOf()),
            intArrayOf(if (primary) palette.onAccent else palette.accent, palette.secondary),
        ))
        background = GradientDrawable().apply {
            cornerRadius = dp(12).toFloat()
            setColor(if (primary) palette.accent else palette.background)
        }
        setOnClickListener { click() }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private class Palette(val dark: Boolean) {
        val background = if (dark) Color.rgb(18, 19, 23) else Color.rgb(246, 247, 251)
        val surface = if (dark) Color.rgb(28, 29, 34) else Color.WHITE
        val text = if (dark) Color.rgb(242, 242, 246) else Color.rgb(25, 26, 31)
        val secondary = if (dark) Color.rgb(175, 176, 186) else Color.rgb(98, 100, 113)
        val accent = if (dark) Color.rgb(188, 182, 255) else Color.rgb(101, 90, 190)
        val onAccent = if (dark) Color.rgb(39, 31, 83) else Color.WHITE
    }

    private companion object {
        fun blockedResponse() = WebResourceResponse(
            "text/plain", "UTF-8", 403, "Blocked", emptyMap(), ByteArrayInputStream(ByteArray(0)),
        )
    }
}
