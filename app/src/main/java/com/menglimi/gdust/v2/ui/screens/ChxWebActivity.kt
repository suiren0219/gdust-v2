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
import android.widget.LinearLayout
import android.widget.ScrollView
import androidx.activity.ComponentActivity
import androidx.activity.addCallback
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.ComposeView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.gson.JsonParser
import com.menglimi.gdust.v2.App
import com.menglimi.gdust.v2.data.ChxSso
import com.menglimi.gdust.v2.data.ChxWebSession
import com.menglimi.gdust.v2.data.Repository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.menglimi.gdust.v2.ui.AppTheme
import com.menglimi.gdust.v2.ui.LocalDs
import top.yukonga.miuix.kmp.basic.Button
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
    private var sessionVersion = 0L
    private var chxVersion = 0L
    private val activityScope = CoroutineScope(Dispatchers.Main.immediate)
    private var requestPaused = false
    private var recoveryRunning = false

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        repo = (application as App).repo
        sessionVersion = savedInstanceState?.getLong("session_version") ?: repo.sessionVersion.value
        if (!repo.isCurrentSession(sessionVersion) || repo.store.webLogoutPending) {
            finish()
            return
        }
        val initialUrl = intent.getStringExtra(EXTRA_URL) ?: ChxSso.fyssoUrl
        logout = intent.getBooleanExtra(EXTRA_LOGOUT, false) || ChxSso.isSchoolLogoutUrl(initialUrl)
        // 打开验证页/课程页只作废旧请求，保留人工验证才能解除的风控暂停；登录与退出才清暂停。
        repo.invalidateChxRequests(clearPause = logout || ChxSso.isLoginUrl(initialUrl))
        chxVersion = repo.chxSessionVersion.value
        activityScope.launch {
            repo.chxRequestPause.collect { paused ->
                requestPaused = paused != null
            }
        }
        setResult(RESULT_CANCELED, Intent().putExtra(EXTRA_LOGOUT_CONFIRMED, false))
        web = WebView(this)
        configure(web)
        onBackPressedDispatcher.addCallback(this) { navigateBack() }
        // 学校 CAS 使用 100vh；WebView 放进 Compose AndroidView 会出现零高视口白屏。
        // Compose 只画工具栏，网页由原生 LinearLayout 独立测量（与教务网页登录一致）。
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val toolbar = ComposeView(this)
        toolbar.setContent {
            AppTheme {
                val ds = LocalDs.current
                SideEffect {
                    root.setBackgroundColor(ds.bg.toArgb())
                    web.setBackgroundColor(ds.bg.toArgb())
                }
                Column(Modifier.fillMaxWidth().background(ds.bg)) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = ::navigateBack, modifier = Modifier.size(48.dp)) {
                            Icon(MiuixIcons.Back, "返回上一页", Modifier.size(24.dp), tint = ds.text)
                        }
                        Text(hostLabel, color = ds.text, fontSize = 17.sp, fontWeight = FontWeight.Medium, maxLines = 2,
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
                    if (requestPaused && !logout) {
                        Button(onClick = ::completeChxCheck, enabled = !recoveryRunning,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                            Text(if (recoveryRunning) "正在重新检查受阻请求…" else "完成检查并重试", fontSize = 14.sp)
                        }
                    }
                    if (pageMessage.isNotBlank() || pageError != null) {
                        Column(Modifier.fillMaxWidth().heightIn(max = 144.dp).verticalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp, vertical = 12.dp)) {
                            Text(pageError ?: pageMessage, color = if (pageError != null) ds.danger else ds.sub,
                                fontSize = 13.sp, lineHeight = 20.sp)
                        }
                    }
                    if (!logout && (pageError != null || currentPage?.let(ChxSso::isLoginUrl) == true)) {
                        Button(onClick = { openLogin(ChxSso.DIRECT_LOGIN) },
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                            Text("使用学习通账号网页登录", fontSize = 14.sp)
                        }
                    }
                }
            }
        }
        // AppTheme 内部使用 fillMaxSize；ScrollView 以不限定高度测量工具栏，避免其抢占网页高度。
        val toolbarContainer = ScrollView(this).apply {
            isFillViewport = false
            addView(toolbar, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        root.addView(toolbarContainer, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        root.addView(web, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(root)
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val padding = insets.getInsets(WindowInsetsCompat.Type.systemBars() or
                WindowInsetsCompat.Type.displayCutout() or WindowInsetsCompat.Type.ime())
            view.setPadding(padding.left, padding.top, padding.right, padding.bottom)
            insets
        }
        ViewCompat.requestApplyInsets(root)
        // 登出始终重新请求，不能用缓存中的成功页面确认这次服务器退出。
        if (logout || savedInstanceState?.getBundle("web_state")?.let(web::restoreState) == null) {
            if (logout) loadLogout(initialUrl) else web.loadUrl(initialUrl)
        } else {
            currentPage = web.url
            hostLabel = web.url?.let { Uri.parse(it).host } ?: "网页登录"
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putLong("session_version", sessionVersion)
        if (::web.isInitialized) outState.putBundle("web_state", Bundle().also { web.saveState(it) })
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

    private fun isCurrentSession(): Boolean = repo.isCurrentSession(sessionVersion) &&
        repo.chxSessionVersion.value == chxVersion && !repo.store.webLogoutPending

    private fun navigateBack() {
        if (!logout && web.canGoBack()) web.goBack() else finishBrowsing()
    }

    private fun openLogin(url: String) {
        if (!isCurrentSession()) return
        pageVersion++
        web.stopLoading()
        web.loadUrl(url)
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

    private fun completeChxCheck() {
        if (logout || !isCurrentSession() || recoveryRunning) return
        val url = web.url.orEmpty()
        if (ChxSso.isLoginUrl(url) || url.contains("verify", ignoreCase = true) ||
            url.contains("captcha", ignoreCase = true) || mainPageFailed) {
            pageError = "请先在网页完成登录或安全验证，再点击此按钮。"
            return
        }
        recoveryRunning = true
        pageError = null
        pageMessage = "正在重新执行此前受阻的学习通请求；只有读取和解析成功才会解除暂停。"
        activityScope.launch {
            try {
                repo.resumeChxAfterWebCheck()
                if (isCurrentSession()) {
                    pageMessage = "受阻业务已重新读取并通过校验，学习通请求已恢复。"
                    pageError = null
                }
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                if (isCurrentSession()) pageError = failure.message ?: "受阻业务仍未确认读取成功，请检查后重试。"
            } finally {
                recoveryRunning = false
            }
        }
    }

    private fun finishBrowsing() {
        if (isFinishing || isDestroyed) return
        pageVersion++ // 关闭后到达的 JS/网络回调不再更新结果。
        web.stopLoading()
        if (!logout && isCurrentSession()) saveCookieIfAny()
        runCatching { CookieManager.getInstance().flush() }
        // 普通登录页返回仅带“已保存”信息；已有 Cookie 不得把 RESULT_OK 当成已验证登录。
        setResult(if (logout && logoutConfirmed) RESULT_OK else RESULT_CANCELED,
            Intent().putExtra(EXTRA_LOGOUT_CONFIRMED, logout && logoutConfirmed)
                .putExtra(EXTRA_SESSION_SAVED, !logout && repo.store.hasChxSession))
        finish()
    }

    private fun saveCookieIfAny(): Boolean {
        if (!isCurrentSession()) return false
        val cookie = ChxWebSession.candidateCookie() ?: return false
        return repo.saveChxCandidate(cookie, sessionVersion, chxVersion)
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
                if (isFinishing || isDestroyed || currentPage != url || !isCurrentSession()) return
                pageProgress = 100
                if (mainPageFailed) return
                if (logout) {
                    val version = pageVersion
                    view.evaluateJavascript("document.body ? document.body.innerText : ''") { result ->
                        if (isFinishing || isDestroyed || version != pageVersion || view.url != url ||
                            !isCurrentSession()) return@evaluateJavascript
                        val text = runCatching { JsonParser.parseString(result).asString }.getOrNull()
                        logoutConfirmed = ChxSso.confirmsSchoolLogout(url, text, !mainPageFailed && !logoutAttemptFailed, sentCasTicket)
                        if (logoutConfirmed) finishBrowsing()
                        else pageMessage = "学校服务器退出尚未确认。可刷新重试，或关闭网页后清理本地会话。"
                    }
                    return
                }
                // 教务网页登录落地即保存入口：学习通联动完成后，「网页登录导入」可直接读取学期课表
                val teachingSaved = ChxSso.isTeachingWebEntry(url) &&
                    runCatching { repo.saveWebSession(url, sessionVersion) }.getOrDefault(false)
                pageMessage = buildString {
                    if (teachingSaved) append("教务网页入口已保存，可在「网页登录导入」直接读取学期课表。")
                    append(
                        when {
                            // 登录页优先于旧 Cookie 候选，避免尚未认证就提示会话已保存。
                            ChxSso.isLoginUrl(url) ->
                                "当前仍在登录页，尚未完成学习通认证。教务登录不等于学习通已登录；" +
                                    "请完成网页认证，学校入口不可用时可使用学习通账号网页登录。"
                            saveCookieIfAny() -> "学习通会话已保存，返回后可检查是否有效。"
                            teachingSaved -> ""
                            else -> "尚未取得可验证的学习通会话。请先在网页里完成认证；" +
                                "若已登录，可进入学习通首页后再返回检查。"
                        },
                    )
                }
            }
        }
        view.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView, newProgress: Int) {
                if (!isFinishing && !isDestroyed && !mainPageFailed) pageProgress = newProgress.coerceIn(0, 100)
            }
        }
    }
}
