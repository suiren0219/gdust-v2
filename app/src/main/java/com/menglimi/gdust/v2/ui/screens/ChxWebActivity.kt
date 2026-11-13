package com.menglimi.gdust.v2.ui.screens

import android.annotation.SuppressLint
import android.net.Uri
import android.os.Bundle
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
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

/** 独立承载学校 CAS 与超星网页登录的 WebView。 */
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

        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val toolbar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(12), dp(10), dp(12), dp(10))
        }
        val back = TextView(this).apply {
            text = "‹"
            textSize = 26f
            setPadding(dp(4), 0, dp(12), 0)
            setOnClickListener { finishBySession() }
        }
        hostLabel = TextView(this).apply { textSize = 13f }
        toolbar.addView(back)
        toolbar.addView(hostLabel, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        progressBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            visibility = ProgressBar.GONE
        }
        root.addView(toolbar, LinearLayout.LayoutParams(-1, -2))
        root.addView(progressBar, LinearLayout.LayoutParams(-1, dp(3)))

        web = WebView(this)
        configure(web)
        root.addView(web, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(
                androidx.core.view.WindowInsetsCompat.Type.systemBars() or
                    androidx.core.view.WindowInsetsCompat.Type.displayCutout(),
            )
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        onBackPressedDispatcher.addCallback(this) {
            if (web.canGoBack()) web.goBack() else finishBySession()
        }
        web.loadUrl(intent.getStringExtra(EXTRA_URL) ?: ChxSso.fyssoUrl)
    }

    override fun onStop() {
        CookieManager.getInstance().flush()
        super.onStop()
    }

    override fun finish() {
        CookieManager.getInstance().flush()
        super.finish()
    }

    private fun finishBySession() {
        setResult(if (repo.store.hasChxSession) RESULT_OK else RESULT_CANCELED)
        finish()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun saveCookieIfAny(): Boolean {
        val cookie = ChxSso.cookieProbes.firstNotNullOfOrNull { url ->
            CookieManager.getInstance().getCookie(url)?.takeIf(ChxSso::hasSessionCookie)
        } ?: return false
        repo.store.saveChxCookie(cookie)
        return true
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun configure(view: WebView) {
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(view, true)
        view.settings.javaScriptEnabled = true
        view.settings.domStorageEnabled = true
        view.settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
        view.settings.allowFileAccess = false
        view.settings.allowContentAccess = false
        view.settings.userAgentString =
            "Mozilla/5.0 (Linux; Android 13; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"

        view.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val scheme = request.url.scheme.orEmpty().lowercase()
                if (scheme == "http" || scheme == "https") return false
                runCatching {
                    startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, request.url))
                }
                hostLabel.text = "已用系统打开 $scheme 链接，返回本页继续即可"
                return true
            }

            override fun onPageFinished(view: WebView, url: String) {
                progressBar.visibility = ProgressBar.GONE
                val uri = Uri.parse(url)
                if (uri.host == "cas.gdust.edu.cn" && uri.path.orEmpty().endsWith("/logout")) {
                    view.evaluateJavascript(
                        "try{localStorage.clear();sessionStorage.clear();}catch(e){};'ok'",
                    ) { finishWithResult(RESULT_CANCELED) }
                    return
                }
                val saved = saveCookieIfAny()
                val host = uri.host.orEmpty()
                hostLabel.text = when {
                    saved && ChxSso.isChaoxingHost(host) -> "已联动学习通：$host"
                    saved -> "已保存学习通会话：$host"
                    ChxSso.isChaoxingHost(host) -> "尚未取得学习通会话：$host"
                    else -> host
                }
            }
        }
        view.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView, progress: Int) {
                if (progress in 1..99) {
                    progressBar.visibility = ProgressBar.VISIBLE
                    progressBar.progress = progress
                }
            }
        }
    }

    private fun finishWithResult(code: Int) {
        setResult(code)
        finish()
    }
}
