package com.menglimi.gdust.v2.ui.screens

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.addCallback
import androidx.core.content.ContextCompat
import com.menglimi.gdust.v2.App
import com.menglimi.gdust.v2.data.Repository
import java.net.URI

/**
 * 教务网页登录（独立 Activity 承载 WebView）。
 *
 * 与学习通联动同一个坑：Compose `AndroidView` 里的 WebView 视口单位恒为 0，
 * 学校页面（Tailwind/`100vh` 布局）会白屏；裸 Activity 承载正常。
 *
 * 流程：在页内登录门户 → 进入正方教务 → 点右上「读取课表」→ 保存当前页地址并返回；
 * 真正的读取由调用方走 [Repository.importTermWithWebSession]（OkHttp + CookieManager 会话）。
 */
class TeachingWebActivity : ComponentActivity() {

    companion object {
        const val EXTRA_URL = "url"
        private const val REQ_CAMERA = 7101
        private const val PORTAL_ENTRY = "https://portal.gdust.edu.cn"
        private const val VPN_LOGIN =
            "https://webvpn.gdust.edu.cn/https/77726476706e69737468656265737421f3f652d220347d436a468ca88d1b203b" +
                "/cas/?service=https%3A%2F%2Fwebvpn.gdust.edu.cn%2Flogin%3Fcas_login%3Dtrue"
    }

    private lateinit var repo: Repository
    private lateinit var web: WebView
    private lateinit var hostLabel: TextView
    private var pendingPermission: PermissionRequest? = null

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repo = (application as App).repo
        val debuggable = applicationInfo.flags and
            android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0
        WebView.setWebContentsDebuggingEnabled(debuggable)

        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(12), dp(10), dp(12), dp(10))
            setBackgroundColor(0xFFF4F5F7.toInt())
        }
        val back = TextView(this).apply {
            text = "‹"
            textSize = 26f
            setPadding(dp(4), 0, dp(12), 0)
            setOnClickListener { finishWithResult(RESULT_CANCELED) }
        }
        hostLabel = TextView(this).apply {
            textSize = 12f
            setTextColor(0xFF666666.toInt())
        }
        val portal = TextView(this).apply {
            text = "门户"
            textSize = 14f
            setPadding(dp(8), dp(4), dp(8), dp(4))
            setOnClickListener { web.loadUrl(PORTAL_ENTRY) }
        }
        val vpn = TextView(this).apply {
            text = "WebVPN"
            textSize = 14f
            setPadding(dp(8), dp(4), dp(8), dp(4))
            setOnClickListener { web.loadUrl(VPN_LOGIN) }
        }
        val read = TextView(this).apply {
            text = "读取课表"
            textSize = 14f
            setPadding(dp(10), dp(4), dp(4), dp(4))
            setTextColor(0xFF1A6BFF.toInt())
            setOnClickListener { tryRead() }
        }
        bar.addView(back)
        bar.addView(hostLabel, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        bar.addView(portal)
        bar.addView(vpn)
        bar.addView(read)
        root.addView(
            bar,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ),
        )

        web = WebView(this)
        configure(web)
        root.addView(
            web,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f),
        )
        setContentView(root)

        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val bars = insets.getInsets(
                androidx.core.view.WindowInsetsCompat.Type.systemBars() or
                    androidx.core.view.WindowInsetsCompat.Type.displayCutout(),
            )
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        onBackPressedDispatcher.addCallback(this) {
            if (web.canGoBack()) web.goBack() else finishWithResult(RESULT_CANCELED)
        }

        val saved = repo.store.loadWebImportPage()
        web.loadUrl(
            intent.getStringExtra(EXTRA_URL)
                ?: saved.takeIf(::schoolUrl)
                ?: PORTAL_ENTRY,
        )
    }

    override fun onStop() {
        super.onStop()
        CookieManager.getInstance().flush()
    }

    private fun finishWithResult(code: Int) {
        CookieManager.getInstance().flush()
        setResult(code)
        finish()
    }

    /** 点在「读取课表」：只在教务页面才允许返回（门户登录态 ≠ 教务会话）。 */
    private fun tryRead() {
        val url = web.url.orEmpty()
        val host = runCatching { URI(url).host.orEmpty().lowercase() }.getOrDefault("")
        when (host) {
            "gkjx.gdust.edu.cn", "172.16.254.1", "webvpn.gdust.edu.cn" -> {
                repo.store.saveWebImport(url)
                finishWithResult(RESULT_OK)
            }

            "portal.gdust.edu.cn" ->
                hostLabel.text = "还在学校门户：请先点进「教务系统」，再点读取"

            else -> hostLabel.text = "请在教务系统页面点读取（当前：$host）"
        }
    }

    private fun schoolUrl(url: String): Boolean = runCatching {
        val uri = URI(url)
        val host = uri.host.orEmpty().lowercase()
        uri.scheme in listOf("https", "http") &&
            (host == "gdust.edu.cn" || host.endsWith(".gdust.edu.cn") || host == "172.16.254.1")
    }.getOrDefault(false)

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    @SuppressLint("SetJavaScriptEnabled")
    private fun configure(web: WebView) {
        CookieManager.getInstance().setAcceptCookie(true)
        // 学校登录页的扫码二维码多为跨域 iframe/canvas：必须允许第三方 Cookie 与混合内容
        CookieManager.getInstance().setAcceptThirdPartyCookies(web, true)
        web.settings.javaScriptEnabled = true
        web.settings.domStorageEnabled = true
        web.settings.databaseEnabled = true
        web.settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
        web.settings.userAgentString =
            "Mozilla/5.0 (Linux; Android 13; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
        web.settings.loadWithOverviewMode = true
        web.settings.useWideViewPort = true
        web.settings.javaScriptCanOpenWindowsAutomatically = true
        web.settings.setSupportMultipleWindows(false)
        web.settings.mediaPlaybackRequiresUserGesture = false
        web.settings.allowFileAccess = false
        web.settings.allowContentAccess = false

        web.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                // 门户二级页、钉钉授权页、SSO 跳转都允许继续
                val scheme = request.url.scheme.orEmpty().lowercase()
                if (scheme == "http" || scheme == "https") return false
                // dingtalk:// alipays:// 等交系统打开，别让 WebView 卡在加载中
                runCatching {
                    view.context.startActivity(
                        android.content.Intent(android.content.Intent.ACTION_VIEW, request.url),
                    )
                }
                hostLabel.text = "已用系统打开 $scheme 链接，返回本页继续"
                return true
            }

            override fun onPageFinished(view: WebView, url: String) {
                val host = runCatching { Uri.parse(url).host.orEmpty() }.getOrDefault("")
                hostLabel.text = host
                android.util.Log.i("TeachWeb", "onPageFinished $url")
                // 页面真正进入正方教务后记住入口；Cookie 由 CookieManager 自己持久化
                if (url.contains("/kbcx/")) runCatching { repo.store.saveWebImport(url) }
                // WebVPN 在校园网 IP 下会返回拒绝页
                view.evaluateJavascript(
                    "(document.body&&document.body.innerText||'').slice(0,400)",
                ) { text ->
                    if (text != null && (text.contains("拒绝登录") || text.contains("校园网"))) {
                        hostLabel.text = "WebVPN 拒绝校园网 IP（学校策略）：请点「门户」从门户进入教务"
                    }
                }
            }

            override fun onReceivedSslError(
                view: WebView,
                handler: android.webkit.SslErrorHandler,
                error: android.net.http.SslError,
            ) {
                // 学校站点证书链偶尔不全：仅对校方域名放行
                val host = runCatching { Uri.parse(error.url).host.orEmpty() }.getOrDefault("")
                val allowed = host == "gdust.edu.cn" || host.endsWith(".gdust.edu.cn") ||
                    host == "172.16.254.1" || host == "webvpn.gdust.edu.cn"
                if (allowed) handler.proceed() else handler.cancel()
            }

            override fun onReceivedError(
                view: WebView,
                request: WebResourceRequest,
                error: WebResourceError,
            ) {
                android.util.Log.w("TeachWeb", "onReceivedError ${error.errorCode} ${request.url}")
                if (request.isForMainFrame) hostLabel.text = "页面加载失败：${error.description}"
            }

            override fun onReceivedHttpError(
                view: WebView,
                request: WebResourceRequest,
                errorResponse: WebResourceResponse,
            ) {
                android.util.Log.w("TeachWeb", "http ${errorResponse.statusCode} ${request.url}")
            }
        }

        web.webChromeClient = object : WebChromeClient() {
            override fun onPermissionRequest(request: PermissionRequest) {
                val wantsCamera = request.resources.any {
                    it == PermissionRequest.RESOURCE_VIDEO_CAPTURE
                }
                val granted = ContextCompat.checkSelfPermission(
                    this@TeachingWebActivity,
                    Manifest.permission.CAMERA,
                ) == PackageManager.PERMISSION_GRANTED
                if (wantsCamera && !granted) {
                    // 等系统弹窗；结果在 onRequestPermissionsResult 里 grant/deny
                    pendingPermission = request
                    requestPermissions(arrayOf(Manifest.permission.CAMERA), REQ_CAMERA)
                } else {
                    request.grant(request.resources)
                }
            }

            override fun onConsoleMessage(msg: android.webkit.ConsoleMessage): Boolean {
                android.util.Log.d("TeachWeb", "console ${msg.message()} @${msg.sourceId()}:${msg.lineNumber()}")
                return true
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != REQ_CAMERA) return
        val req = pendingPermission
        pendingPermission = null
        if (req == null) return
        if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
            req.grant(req.resources)
        } else {
            req.deny()
        }
    }
}
