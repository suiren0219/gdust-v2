package com.menglimi.gdust.v2.ui.screens

import android.annotation.SuppressLint
import android.net.Uri
import android.os.Bundle
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.addCallback
import com.menglimi.gdust.v2.App
import com.menglimi.gdust.v2.data.ChxSso
import com.menglimi.gdust.v2.data.Repository

/**
 * 学习通 / 教务网页登录：独立 Activity 承载 WebView。
 *
 * 为什么不用 Compose AndroidView（2026-11-12 实测结论）：
 * 同一 CAS 页面在 Compose `AndroidView` 里白屏——CDP 看到 DOM 完整（#app innerHTML 14k、
 * innerHeight 正常），但 `100vh/100dvh/100svh` 全部解析为 0，Tailwind 的 `min-h-screen`
 * 容器高度塌成 0 再被 `overflow:hidden` 裁掉，整页不可见。裸 View / 裸 Activity 承载正常；
 * API 35(WebView 124) 与 API 36(WebView 133) 都复现，与 WebSettings 无关。
 * 最小复现见 debug 源集的 `WebViewProbeActivity`。
 *
 * 只保存 CAS 放行后的超星 Cookie（加密存储），不保存密码。
 */
class ChxWebActivity : ComponentActivity() {

    companion object {
        const val EXTRA_URL = "url"
    }

    private lateinit var repo: Repository
    private lateinit var web: WebView
    private lateinit var hostLabel: TextView
    private lateinit var progressBar: ProgressBar

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repo = (application as App).repo
        // 研究线诊断：debug 包保留远程调试，release 关闭
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
            setOnClickListener { finishWithResult() }
        }
        hostLabel = TextView(this).apply {
            textSize = 13f
            setTextColor(0xFF666666.toInt())
        }
        bar.addView(back)
        bar.addView(
            hostLabel,
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f),
        )
        progressBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            visibility = ProgressBar.GONE
        }
        root.addView(
            bar,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ),
        )
        root.addView(
            progressBar,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(3)),
        )

        web = WebView(this)
        configure(web)
        root.addView(
            web,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f,
            ),
        )
        setContentView(root)

        // 沉浸式（edge-to-edge）窗口下给状态栏/导航栏留出安全区
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val bars = insets.getInsets(
                androidx.core.view.WindowInsetsCompat.Type.systemBars() or
                    androidx.core.view.WindowInsetsCompat.Type.displayCutout(),
            )
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        onBackPressedDispatcher.addCallback(this) {
            if (web.canGoBack()) web.goBack() else finishWithResult()
        }

        web.loadUrl(intent.getStringExtra(EXTRA_URL) ?: ChxSso.fyssoUrl)
    }

    override fun onStop() {
        super.onStop()
        // 落盘 WebView Cookie，回到联动页立刻可见
        CookieManager.getInstance().flush()
    }

    override fun finish() {
        CookieManager.getInstance().flush()
        super.finish()
    }

    private fun finishWithResult() {
        setResult(if (repo.store.hasChxSession) RESULT_OK else RESULT_CANCELED)
        finish()
    }

    private fun dp(v: Int): Int =
        (v * resources.displayMetrics.density).toInt()

    private fun saveCookieIfAny(): Boolean {
        val cookie = ChxSso.cookieProbes.firstNotNullOfOrNull { url ->
            CookieManager.getInstance().getCookie(url)?.takeIf { ChxSso.hasSessionCookie(it) }
        } ?: return false
        repo.store.saveChxCookie(cookie)
        return true
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun configure(web: WebView) {
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(web, true)
        web.settings.javaScriptEnabled = true
        web.settings.domStorageEnabled = true
        web.settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
        web.settings.allowFileAccess = false
        web.settings.allowContentAccess = false
        web.settings.userAgentString =
            "Mozilla/5.0 (Linux; Android 13; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"

        web.webViewClient = object : WebViewClient() {
            override fun onReceivedError(
                view: WebView,
                request: WebResourceRequest,
                error: WebResourceError,
            ) {
                android.util.Log.w(
                    "ChxWeb",
                    "onReceivedError ${error.errorCode} ${error.description} " +
                        "main=${request.isForMainFrame} ${request.url}",
                )
                if (request.isForMainFrame) {
                    hostLabel.text = "页面加载失败：${error.description}"
                }
            }

            override fun onReceivedHttpError(
                view: WebView,
                request: WebResourceRequest,
                errorResponse: WebResourceResponse,
            ) {
                android.util.Log.w(
                    "ChxWeb",
                    "http ${errorResponse.statusCode} ${request.url} " +
                        "main=${request.isForMainFrame}",
                )
            }

            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val scheme = request.url.scheme.orEmpty().lowercase()
                if (scheme == "http" || scheme == "https") return false
                // chaoxing:// 等唤起客户端的链接交系统，别让 WebView 卡住
                runCatching {
                    view.context.startActivity(
                        android.content.Intent(android.content.Intent.ACTION_VIEW, request.url),
                    )
                }
                hostLabel.text = "已用系统打开 $scheme 链接，返回本页继续即可"
                return true
            }

            override fun onPageFinished(view: WebView, url: String) {
                progressBar.visibility = ProgressBar.GONE
                val host = runCatching { Uri.parse(url).host.orEmpty() }.getOrDefault("")
                android.util.Log.i("ChxWeb", "onPageFinished $url")
                // 每次落地都试存一次：CAS 跳转链里可能最后一跳才拿到超星 Cookie
                val saved = saveCookieIfAny()
                hostLabel.text = when {
                    saved && ChxSso.isChaoxingHost(host) -> "已联动学习通：$host"
                    saved -> "已保存学习通会话：$host"
                    ChxSso.isChaoxingHost(host) -> "还没拿到会话，请先完成登录：$host"
                    else -> host
                }
            }

            override fun onReceivedSslError(
                view: WebView,
                handler: android.webkit.SslErrorHandler,
                error: android.net.http.SslError,
            ) {
                // 只对校方与超星域放行证书链，其他域一律阻止
                val h = runCatching { Uri.parse(error.url).host.orEmpty() }.getOrDefault("")
                android.util.Log.w("ChxWeb", "sslError ${error.primaryError} $h ${error.url}")
                val allowed = h.endsWith("gdust.edu.cn") || ChxSso.isChaoxingHost(h)
                if (allowed) handler.proceed() else handler.cancel()
            }
        }

        web.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView, newProgress: Int) {
                if (newProgress in 1..99) {
                    progressBar.visibility = ProgressBar.VISIBLE
                    progressBar.progress = newProgress
                }
            }

            // WebView 默认吞掉 JS 报错；白屏排障需要把 console 消息转出来
            override fun onConsoleMessage(msg: android.webkit.ConsoleMessage): Boolean {
                android.util.Log.d(
                    "ChxWeb",
                    "console ${msg.messageLevel()} ${msg.message()} " +
                        "@${msg.sourceId()}:${msg.lineNumber()}",
                )
                return true
            }
        }
    }
}
