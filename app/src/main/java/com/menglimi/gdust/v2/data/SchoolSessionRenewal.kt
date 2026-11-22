package com.menglimi.gdust.v2.data

import android.annotation.SuppressLint
import android.content.Context
import android.net.http.SslError
import android.webkit.CookieManager
import android.webkit.SslErrorHandler
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.io.IOException
import kotlin.coroutines.resume

/** 只复用学校网页自己的会话；新票由各服务页面消费，不读密码或持久化 ticket。 */
internal class SchoolSessionRenewal(
    private val context: Context,
    private val checkSession: () -> Unit,
    private val acceptPortal: suspend (String) -> Boolean,
    private val verifyTeaching: suspend (String) -> Boolean?,
) {
    private data class Page(val url: String, val revision: Long, val loaded: Boolean = false)

    @SuppressLint("SetJavaScriptEnabled")
    suspend fun renew(teachingPage: String) = withContext(Dispatchers.Main.immediate) {
        checkSession()
        val web = WebView(context.applicationContext)
        var page = Page("", 0L)
        var failure: Exception? = null
        try {
            CookieManager.getInstance().setAcceptCookie(true)
            CookieManager.getInstance().setAcceptThirdPartyCookies(web, true)
            web.settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                allowFileAccess = false
                allowContentAccess = false
                javaScriptCanOpenWindowsAutomatically = false
                mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                userAgentString = "Mozilla/5.0 (Linux; Android 13; K) AppleWebKit/537.36 " +
                    "(KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
            }
            web.layout(0, 0, 1080, 1920)
            web.webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                    if (SchoolSessionRenewalPolicy.allows(request.url.toString())) return false
                    if (request.isForMainFrame) failure = IOException("学校会话跳转到未许可的地址")
                    return true
                }

                override fun onPageStarted(view: WebView, url: String, favicon: android.graphics.Bitmap?) {
                    page = Page(url, page.revision + 1)
                }

                override fun onPageFinished(view: WebView, url: String) {
                    if (view.url?.substringBefore('#') == url.substringBefore('#') &&
                        page.url.substringBefore('#') == url.substringBefore('#')) {
                        page = page.copy(url = view.url ?: url, loaded = true)
                    }
                }

                override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                    if (request.isForMainFrame) failure = IOException("学校会话页面暂时无法连接")
                }

                override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, response: WebResourceResponse) {
                    if (request.isForMainFrame) failure = IOException("学校会话页面 HTTP ${response.statusCode}")
                }

                override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) {
                    handler.cancel()
                    failure = IOException("学校会话页面证书验证失败")
                }
            }
            try {
                withTimeout(45_000L) {
                    for (service in listOf(SchoolService.PORTAL, SchoolService.TEACHING)) {
                        checkSession()
                        failure = null
                        page = Page("", page.revision + 1)
                        web.loadUrl(if (service == SchoolService.PORTAL) SchoolLoginWeb.portalLoginUrl
                            else SchoolSessionRenewalPolicy.teachingEntry(teachingPage))
                        var authenticationPageSince = 0L
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            checkSession()
                            failure?.let { throw it }
                            val current = page
                            val url = web.url.orEmpty()
                            if (!current.loaded || current.url.substringBefore('#') != url.substringBefore('#')) {
                                delay(250L)
                                continue
                            }
                            if (SchoolLoginWeb.isAuthenticationPage(url)) {
                                // CAS 页面可能先显示框架再自动跳转，等它确实停在需要人工认证的页面。
                                if (authenticationPageSince == 0L) authenticationPageSince = android.os.SystemClock.elapsedRealtime()
                                if (android.os.SystemClock.elapsedRealtime() - authenticationPageSince >= 3_000L &&
                                    evaluate(web, SchoolLoginWeb.pageContentScript) == "true" && page.revision == current.revision) {
                                    throw AuthFailed(401, "学校会话需要重新认证，请扫码绑定一次。")
                                }
                            } else {
                                authenticationPageSince = 0L
                                if (SchoolLoginWeb.isServicePage(service, url)) {
                                    val verified = when (service) {
                                        SchoolService.PORTAL -> {
                                            var token: String? = null
                                            for (attempt in 0 until 30) {
                                                checkSession()
                                                if (page.revision != current.revision) break
                                                token = SchoolLoginWeb.portalToken(url, evaluate(web, SchoolLoginWeb.portalSessionScript))
                                                if (token != null) break
                                                delay(500L)
                                            }
                                            if (page.revision != current.revision) continue
                                            token?.let { acceptPortal(it) }
                                                ?: throw IOException("门户暂未返回可验证的登录状态")
                                        }
                                        SchoolService.TEACHING -> verifyTeaching(url)
                                        SchoolService.CHX -> false
                                    }
                                    checkSession()
                                    if (page.revision != current.revision) continue
                                    if (verified == true) {
                                        CookieManager.getInstance().flush()
                                        break
                                    }
                                    if (verified == false) {
                                        throw AuthFailed(401, "学校会话需要重新认证，请扫码绑定一次。")
                                    }
                                    if (service == SchoolService.TEACHING) {
                                        throw IOException("教务暂时未返回可验证数据")
                                    }
                                }
                            }
                            delay(500L)
                        }
                    }
                }
            } catch (_: TimeoutCancellationException) {
                throw IOException("学校会话恢复超时，请稍后重试")
            }
        } finally {
            withContext(NonCancellable + Dispatchers.Main.immediate) {
                web.stopLoading()
                web.destroy()
            }
        }
    }

    private suspend fun evaluate(web: WebView, script: String): String? = withTimeout(3_000L) {
        suspendCancellableCoroutine { continuation ->
            web.evaluateJavascript(script) { result ->
                if (continuation.isActive) continuation.resume(result)
            }
        }
    }
}

internal object SchoolSessionRenewalPolicy {
    fun allows(value: String): Boolean {
        val url = value.toHttpUrlOrNull() ?: return false
        if (url.username.isNotEmpty() || url.password.isNotEmpty() || ChxSso.isSchoolLogoutUrl(value)) return false
        if (url.scheme == "https" && url.port == 443 && url.host == "webvpn.gdust.edu.cn") {
            val login = requireNotNull(ChxSso.TEACHING_VPN_LOGIN.toHttpUrlOrNull())
            if (url.encodedPath == login.encodedPath || url.encodedPath == "/login") return true
        }
        if (SchoolLoginWeb.isPortalPage(value)) return true
        if (url.scheme == "https" && url.port == 443 && url.host == "cas.gdust.edu.cn") {
            return url.encodedPath == "/cas" || url.encodedPath.startsWith("/cas/") ||
                url.encodedPath.startsWith("/lyuapServer/")
        }
        val base = TeachingEndpoints.baseForPage(value) ?: return false
        val prefix = requireNotNull(base.toHttpUrlOrNull()).encodedPath.trimEnd('/')
        return url.encodedPath.removePrefix(prefix).let { path ->
            path.startsWith("/sso/") || path.startsWith("/xtgl/") || path.startsWith("/kbcx/")
        }
    }

    fun teachingEntry(page: String): String = when (page.toHttpUrlOrNull()?.host) {
        "172.16.254.1" -> ChxSso.campusTeachingWebUrl
        "webvpn.gdust.edu.cn" -> ChxSso.TEACHING_VPN_LOGIN
        else -> ChxSso.teachingWebUrl
    }
}
