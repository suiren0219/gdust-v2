package com.menglimi.gdust.v2.debug

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.View
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.viewinterop.AndroidView

/**
 * 研究线诊断页（仅 debug 源集）：WebView 承载方式对照。
 *
 * 背景：学习通联动页（Compose AndroidView 里的 WebView）加载 CAS 前端时整页白屏，
 * CDP 证实 DOM 完整但 100vh/100dvh 全为 0，Tailwind 的 min-h-screen 容器塌成 0 高。
 * 裸 View（mode=bare）和带完整 WebSettings（mode=full）都正常 → 问题在 Compose 承载。
 *
 * 用法：
 *   adb shell am start -n com.menglimi.gdust.v2.debug/com.menglimi.gdust.v2.debug.WebViewProbeActivity --es mode compose-box
 *
 * mode:
 *   bare                裸 WebView（对照，已知正常）
 *   full                裸 WebView + 与联动页相同的 WebSettings（对照，已知正常）
 *   compose-box         Box 里 AndroidView（最小复现，已知白屏）
 *   compose-column      Column + weight(1f)（联动页的真实布局）
 *   compose-crossfade   Crossfade 包一层（AppRoot 的路由方式）
 *   compose-software    AndroidView + LAYER_TYPE_SOFTWARE
 *   compose-delay       AndroidView + attach 后延迟 300ms 再 loadUrl
 *   compose-resize      AndroidView + attach 后强制布局一次（触发 onSizeChanged）
 */
class WebViewProbeActivity : ComponentActivity() {

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WebView.setWebContentsDebuggingEnabled(true)

        val mode = intent.getStringExtra("mode") ?: "bare"
        val url = intent.getStringExtra("url")
            ?: "https://cas.gdust.edu.cn/cas/?service=http%3A%2F%2Fgdkjxy.fysso.chaoxing.com%2Fsso%2Fgdkjxy"

        if (mode.startsWith("compose")) {
            val variant = mode.removePrefix("compose-")
            val cv = ComposeView(this)
            setContentView(cv)
            cv.setContent {
                when (variant) {
                    "column" -> Column(Modifier.fillMaxSize()) {
                        Box(Modifier.weight(1f)) { WebHost(url, variant) }
                    }

                    "crossfade" -> Crossfade(targetState = 1) {
                        Box(Modifier.fillMaxSize()) { WebHost(url, variant) }
                    }

                    else -> Box(Modifier.fillMaxSize()) { WebHost(url, variant) }
                }
            }
            return
        }

        val web = WebView(this)
        configure(web, full = mode == "full")
        web.webViewClient = WebViewClient()
        setContentView(web)
        web.loadUrl(url)
    }

    @Composable
    private fun WebHost(url: String, variant: String) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                WebView(ctx).apply {
                    configure(this, full = true)
                    webViewClient = WebViewClient()
                    if (variant == "software") {
                        setLayerType(View.LAYER_TYPE_SOFTWARE, null)
                    }
                    if (variant == "delay") {
                        addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
                            override fun onViewAttachedToWindow(v: View) {
                                v.postDelayed({ loadUrl(url) }, 300)
                            }

                            override fun onViewDetachedFromWindow(v: View) = Unit
                        })
                    } else {
                        loadUrl(url)
                    }
                    if (variant == "resize") {
                        addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
                            override fun onViewAttachedToWindow(v: View) {
                                v.post {
                                    val w = width - 1
                                    val h = height - 1
                                    if (w > 0 && h > 0) layout(left, top, left + w, top + h)
                                }
                            }

                            override fun onViewDetachedFromWindow(v: View) = Unit
                        })
                    }
                }
            },
        )
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun configure(web: WebView, full: Boolean) {
        web.settings.javaScriptEnabled = true
        web.settings.domStorageEnabled = true
        if (!full) return
        web.settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
        web.settings.allowFileAccess = false
        web.settings.allowContentAccess = false
        web.settings.userAgentString =
            "Mozilla/5.0 (Linux; Android 13; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(web, true)
    }
}
