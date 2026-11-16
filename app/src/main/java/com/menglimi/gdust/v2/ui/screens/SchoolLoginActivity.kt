package com.menglimi.gdust.v2.ui.screens

import android.annotation.SuppressLint
import android.content.Context
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
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.menglimi.gdust.v2.App
import com.menglimi.gdust.v2.data.AuthFailed
import com.menglimi.gdust.v2.data.ChxSso
import com.menglimi.gdust.v2.data.ChxWebSession
import com.menglimi.gdust.v2.data.Repository
import com.menglimi.gdust.v2.data.SchoolLoginFlow
import com.menglimi.gdust.v2.data.SchoolLoginWeb
import com.menglimi.gdust.v2.data.SchoolService
import com.menglimi.gdust.v2.ui.AppTheme
import com.menglimi.gdust.v2.ui.LocalDs
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Close
import top.yukonga.miuix.kmp.icon.extended.Refresh
import kotlin.coroutines.resume

/**
 * 可见学校网页认证 → 门户 TOKEN → 教务 Cookie → 学习通 Cookie。
 * 所有 service 都在同一原生 WebView / CookieManager 内导航，由 CAS 为各站签发自己的票。
 * 不注入密码、不截获 ticket、不合成 TGC，不以页面地址或 Cookie 存在宣告登录成功。
 */
class SchoolLoginActivity : ComponentActivity() {
    companion object {
        const val EXTRA_PORTAL_READY = "school_portal_ready"
        const val EXTRA_TEACHING_READY = "school_teaching_ready"
        const val EXTRA_CHX_READY = "school_chx_ready"
        const val EXTRA_MESSAGE = "school_login_message"
        private const val EXTRA_START_SERVICE = "school_start_service"
        private const val EXTRA_TEACHING_ONLY = "school_teaching_only"
        private val processInstance = java.util.UUID.randomUUID().toString()

        fun intent(context: Context): Intent = Intent(context, SchoolLoginActivity::class.java)
        fun teachingIntent(context: Context): Intent = intent(context)
            .putExtra(EXTRA_START_SERVICE, SchoolService.TEACHING.name).putExtra(EXTRA_TEACHING_ONLY, true)
        fun learningIntent(context: Context): Intent = intent(context).putExtra(EXTRA_START_SERVICE, SchoolService.CHX.name)
    }

    private lateinit var repo: Repository
    private lateinit var web: WebView
    private var flow = SchoolLoginFlow()
    private var summary by mutableStateOf(flow.summary())
    private var message by mutableStateOf("正在准备学校统一登录…")
    private var pageError by mutableStateOf<String?>(null)
    private var host by mutableStateOf("")
    private var pageProgress by mutableIntStateOf(0)
    private var initialized by mutableStateOf(false)
    private var probing by mutableStateOf(false)
    private var sessionVersion = 0L
    private var chxVersion = 0L
    private var currentPage by mutableStateOf<String?>(null)
    private var currentService by mutableStateOf<SchoolService?>(SchoolService.PORTAL)
    private var pageFailed = false
    private var openedChxHome = false
    private var teachingOnly = false
    private var teachingEntry by mutableStateOf(ChxSso.teachingWebUrl)
    private var triedCampus = false
    private var closing = false
    private var probeJob: Job? = null
    private var pageJob: Job? = null
    private var pageRevision = 0L
    private var preparationJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        repo = (application as App).repo
        val sameProcess = savedInstanceState?.getString("process_instance") == processInstance
        val requestedStart = intent.getStringExtra(EXTRA_START_SERVICE)?.let { name ->
            SchoolService.entries.firstOrNull { it.name == name }
        } ?: SchoolService.PORTAL
        teachingOnly = intent.getBooleanExtra(EXTRA_TEACHING_ONLY, false)
        teachingEntry = savedInstanceState?.getString("teaching_entry")
            ?.takeIf { it in setOf(ChxSso.teachingWebUrl, ChxSso.campusTeachingWebUrl, ChxSso.TEACHING_VPN_LOGIN) }
            ?: ChxSso.teachingWebUrl
        triedCampus = teachingEntry != ChxSso.teachingWebUrl
        sessionVersion = if (sameProcess) savedInstanceState!!.getLong("session_version") else repo.sessionVersion.value
        if (!repo.isCurrentSession(sessionVersion)) {
            finish()
            return
        }
        web = WebView(this)
        configure(web)
        createContent()
        // 返回直接交还已经验证的结果；不倒退到带一次性票据的历史 URL。
        onBackPressedDispatcher.addCallback(this) { finishFlow() }
        preparationJob = lifecycleScope.launch {
            try {
                val restoring = sameProcess && savedInstanceState?.getBoolean("flow_prepared") == true
                if (restoring) {
                    val saved = requireNotNull(savedInstanceState)
                    chxVersion = saved.getLong("chx_version")
                    if (!currentSession()) return@launch finishFlow()
                    flow = SchoolLoginFlow(saved.getStringArrayList("flow_connections"), requestedStart)
                } else {
                    flow = SchoolLoginFlow(startAt = requestedStart)
                    val preparedVersion = repo.prepareSchoolWebLogin()
                    currentCoroutineContext().ensureActive()
                    if (closing || preparedVersion != sessionVersion || !repo.isCurrentSession(sessionVersion)) {
                        return@launch finishFlow()
                    }
                    chxVersion = repo.chxSessionVersion.value
                }
                initialized = true
                summary = flow.summary()
                // 已保存的 TOKEN 不证明本次网页账号；门户页面仍须完成认证并独立核对身份。
                if (teachingOnly && flow.isVerified(SchoolService.TEACHING)) finishFlow()
                else startCurrentService()
            } catch (cancelled: CancellationException) {
                if (!closing && !repo.isCurrentSession(sessionVersion)) finishFlow()
                throw cancelled
            } catch (_: Exception) {
                if (!closing) {
                    pageError = "学校登录暂时无法开始，请关闭后重试。"
                    pageProgress = 100
                }
            }
        }
        lifecycleScope.launch {
            repo.sessionVersion.collect { version ->
                if (version != sessionVersion) finishFlow()
            }
        }
        lifecycleScope.launch {
            repo.chxSessionVersion.collect { version ->
                if (initialized && version != chxVersion) finishFlow()
            }
        }
    }

    private fun createContent() {
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val toolbar = ComposeView(this)
        toolbar.setContent {
            AppTheme {
                val ds = LocalDs.current
                SideEffect {
                    root.setBackgroundColor(ds.bg.toArgb())
                }
                Column(Modifier.fillMaxWidth().background(ds.bg)) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("学校统一登录", color = ds.text, fontSize = 18.sp, fontWeight = FontWeight.Medium)
                            Text(host.ifBlank { "门户 · 教务 · 学习通" }, color = ds.sub, fontSize = 12.sp, maxLines = 1)
                        }
                        IconButton(onClick = ::retryService, enabled = initialized,
                            modifier = Modifier.size(48.dp)) {
                            Icon(MiuixIcons.Refresh, "重试当前服务", Modifier.size(24.dp), tint = ds.text)
                        }
                        IconButton(onClick = ::finishFlow, modifier = Modifier.size(48.dp)) {
                            Icon(MiuixIcons.Close, "关闭登录", Modifier.size(24.dp), tint = ds.text)
                        }
                    }
                    if (pageProgress < 100 || probing) {
                        LinearProgressIndicator(color = ds.accent, trackColor = ds.breakBar,
                            modifier = Modifier.fillMaxWidth())
                    }
                    Column(Modifier.fillMaxWidth().heightIn(max = 150.dp).verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp, vertical = 10.dp)) {
                        Text(summary, color = ds.text, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        Text(pageError ?: message, color = if (pageError != null) ds.danger else ds.sub,
                            fontSize = 13.sp, lineHeight = 19.sp, modifier = Modifier.padding(top = 6.dp))
                    }
                    if (initialized && currentService == SchoolService.TEACHING) {
                        Button(onClick = {
                            teachingEntry = if (teachingEntry == ChxSso.teachingWebUrl)
                                ChxSso.campusTeachingWebUrl else ChxSso.teachingWebUrl
                            triedCampus = teachingEntry == ChxSso.campusTeachingWebUrl
                            startCurrentService()
                        }, modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp)) {
                            Text(if (teachingEntry == ChxSso.teachingWebUrl) "切换校园网教务入口" else "切换公网教务入口", fontSize = 13.sp)
                        }
                    }
                    if (initialized && currentService == SchoolService.TEACHING && pageError != null) {
                        Button(onClick = {
                            triedCampus = true
                            teachingEntry = ChxSso.TEACHING_VPN_LOGIN
                            startCurrentService()
                        }, modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp)) {
                            Text("使用校外 WebVPN", fontSize = 13.sp)
                        }
                    }
                    if (initialized && currentService == SchoolService.CHX) {
                        Button(onClick = {
                            replaceWebView()
                            navigate(ChxSso.DIRECT_LOGIN)
                            summary = flow.summary()
                        }, modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp)) {
                            Text("使用学习通账号登录", fontSize = 13.sp)
                        }
                    }
                    if (initialized && currentService != null) {
                        Button(onClick = ::skipService,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp)) {
                            Text(if (teachingOnly) "返回课表导入"
                                else if (currentService == SchoolService.CHX) "稍后连接学习通"
                                else "暂时跳过${currentService?.label.orEmpty()}，继续连接", fontSize = 13.sp)
                        }
                    }
                }
            }
        }
        // 学校 CAS 使用 100vh。网页由原生布局测量，避免 Compose AndroidView 的零高视口。
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
    }

    private fun currentSession(): Boolean = repo.isCurrentSession(sessionVersion) &&
        repo.chxSessionVersion.value == chxVersion && !repo.store.webLogoutPending

    private fun validPage(probe: SchoolLoginFlow.Probe): Boolean =
        !closing && !isFinishing && !isDestroyed && initialized && currentSession() &&
            flow.accepts(probe) && SchoolLoginWeb.isServicePage(probe.service, web.url.orEmpty())

    private fun cancelProbe() {
        val previous = probeJob
        probeJob = null
        probing = false
        previous?.cancel()
    }

    private fun cancelPageWatch() {
        pageRevision++
        pageJob?.cancel()
        pageJob = null
    }

    private fun watchPageLoad() {
        cancelPageWatch()
        pageJob = lifecycleScope.launch {
            delay(30_000L)
            if (closing || pageFailed || flow.complete) return@launch
            if (!currentSession()) return@launch finishFlow()
            handleLoadFailure("学校网页加载超时，请重试或切换入口。已连接的服务会保留。")
        }
    }

    private fun navigate(url: String) {
        if (closing || !flow.navigating(url)) return
        cancelProbe()
        currentPage = null
        pageFailed = false
        pageError = null
        pageProgress = 0
        watchPageLoad()
        // 先使旧页面失效，再 stop/load；同步或延迟到达的旧回调都不能接管新阶段。
        web.stopLoading()
        web.loadUrl(url)
    }

    private fun replaceWebView() {
        cancelPageWatch()
        cancelProbe()
        flow.navigating()
        currentPage = null
        val previous = web
        val parent = previous.parent as ViewGroup
        val index = parent.indexOfChild(previous)
        val params = previous.layoutParams
        previous.webChromeClient = null
        previous.webViewClient = WebViewClient()
        previous.stopLoading()
        parent.removeView(previous)
        previous.destroy()
        // 重新建立视口与渲染器；CookieManager 和站点持久存储仍由 WebView 共享。
        web = WebView(this)
        configure(web)
        parent.addView(web, index, params)
    }

    private fun startCurrentService() {
        if (closing) return
        if (!currentSession()) return finishFlow()
        currentService = flow.current
        val service = currentService ?: return finishFlow()
        message = if (service == SchoolService.PORTAL) "请在学校网页完成认证，随后自动连接教务和学习通。"
            else "正在复用学校网页会话连接${service.label}，请等待验证。"
        openedChxHome = false
        replaceWebView()
        navigate(if (service == SchoolService.TEACHING) teachingEntry else SchoolLoginWeb.entry(service))
        summary = flow.summary()
    }

    private fun retryService() {
        if (!initialized || closing || flow.complete) return
        if (!currentSession()) return finishFlow()
        // 从不 reload 含 ticket 的中转页，重试仍由 CAS 为此 service 生成新票。
        startCurrentService()
    }

    private fun skipService() {
        if (!initialized || closing) return
        if (!currentSession() || teachingOnly) return finishFlow()
        flow.skip()
        cancelProbe()
        summary = flow.summary()
        startCurrentService()
    }

    private fun serviceVerified(probe: SchoolLoginFlow.Probe) {
        if (!validPage(probe) || !flow.verified(probe)) return
        // 当前协程会立即返回；脱开引用后下一站无需等待它的 finally。
        probeJob = null
        probing = false
        CookieManager.getInstance().flush()
        summary = flow.summary()
        if (teachingOnly && probe.service == SchoolService.TEACHING) finishFlow()
        else startCurrentService()
    }

    private fun handleLoadFailure(error: String) {
        if (closing || !initialized || pageFailed || flow.complete) return
        if (!currentSession()) return finishFlow()
        failed(error)
        web.stopLoading()
        // 默认错误文档会显示带 ticket 的地址；恢复提示和操作由原生工具栏承载。
        web.visibility = android.view.View.INVISIBLE
        if (flow.current != SchoolService.TEACHING) return
        if (!triedCampus) {
            triedCampus = true
            pageError = "$error 正在切换校园网教务入口…"
            pageJob = lifecycleScope.launch {
                delay(800L)
                if (closing || !currentSession()) return@launch
                teachingEntry = ChxSso.campusTeachingWebUrl
                startCurrentService()
            }
        } else if (!teachingOnly) {
            pageError = "$error 教务暂未连接，正在继续连接学习通。"
            pageJob = lifecycleScope.launch {
                delay(1_500L)
                if (closing || !currentSession() || !flow.continueAfterFailure()) return@launch
                startCurrentService()
            }
        }
    }

    private fun failed(message: String, probe: SchoolLoginFlow.Probe? = null) {
        if (closing || !initialized || !flow.unavailable(probe)) return
        cancelProbe()
        cancelPageWatch()
        pageFailed = true
        pageProgress = 100
        summary = flow.summary()
        pageError = message
    }

    private fun checkPage(url: String) {
        if (closing || !initialized || pageFailed || flow.complete) return
        if (!currentSession()) return finishFlow()
        val service = flow.current ?: return
        if (SchoolLoginWeb.isAuthenticationPage(url)) {
            if (pageJob?.isActive == true) return
            val revision = pageRevision
            message = "正在显示${service.label}认证页面…"
            val job = lifecycleScope.launch(start = CoroutineStart.LAZY) {
                fun activePage() = !closing && !pageFailed && revision == pageRevision &&
                    currentSession() && flow.matchesPage(url, web.url)
                val visible = withTimeoutOrNull(12_000L) {
                    while (activePage()) {
                        val result = evaluate(SchoolLoginWeb.pageContentScript)
                        if (!activePage()) return@withTimeoutOrNull false
                        if (web.height > 0 && result == "true") return@withTimeoutOrNull true
                        delay(500L)
                    }
                    false
                }
                if (!activePage()) return@launch
                pageJob = null
                if (visible == true) {
                    flow.authenticationRequired()
                    summary = flow.summary()
                    message = if (service == SchoolService.PORTAL) "请在下方学校页面认证，完成后会自动继续。"
                        else "学校仍要求${service.label}认证，请在此完成；网页会话会保留，完成后自动继续。"
                } else {
                    failed("认证页面未能正常显示，请点右上角重试，或暂时跳过当前服务。已连接的服务会保留。")
                }
            }
            pageJob = job
            job.start()
            return
        }
        if (!SchoolLoginWeb.isServicePage(service, url)) {
            // 中转页还可能继续跳转；若一直不落地，也必须提供超时恢复。
            watchPageLoad()
            return
        }
        val probe = flow.beginProbe() ?: return
        probing = true
        // 先保存 Job 再运行，避免 Main.immediate 同步完成后把旧 Job 重新写回。
        val job = lifecycleScope.launch(start = CoroutineStart.LAZY) {
            try {
                when (service) {
                    SchoolService.PORTAL -> {
                        // 门户是 SPA：onPageFinished 可能早于 user/login 写入 sessionStorage。
                        repeat(30) {
                            if (!validPage(probe)) return@launch
                            val result = evaluate(SchoolLoginWeb.portalSessionScript)
                            if (!validPage(probe)) return@launch
                            val token = SchoolLoginWeb.portalToken(web.url.orEmpty(), result)
                            if (token != null) {
                                val accepted = repo.acceptPortalWebSession(token, sessionVersion)
                                if (!validPage(probe)) return@launch
                                if (accepted) serviceVerified(probe)
                                else failed("门户尚未确认学校身份，请重试当前服务。", probe)
                                return@launch
                            }
                            delay(500)
                        }
                        if (validPage(probe)) failed("门户尚未返回已认证会话。可重试，或暂时跳过继续连接教务。", probe)
                    }
                    SchoolService.TEACHING -> {
                        val alive = repo.verifyTeachingWebSession(url, sessionVersion)
                        if (!validPage(probe)) return@launch
                        if (alive == true) serviceVerified(probe)
                        else failed(if (alive == false) "教务尚未确认登录，请重试学校认证。"
                            else "教务暂时未返回可验证数据，可重试或继续连接学习通。", probe)
                    }
                    SchoolService.CHX -> {
                        repeat(10) {
                            if (!validPage(probe)) return@launch
                            if (ChxWebSession.candidateCookie() != null) {
                                val alive = repo.chxSessionAlive()
                                if (!validPage(probe)) return@launch
                                if (alive == true) serviceVerified(probe)
                                else if (openedChxHome) {
                                    failed(if (alive == false) "学习通尚未确认登录，请重试学校认证。"
                                        else "学习通暂时无法验证，已连接的学校服务会保留。", probe)
                                } else {
                                    openedChxHome = true
                                    navigate(ChxSso.HOME)
                                }
                                return@launch
                            }
                            delay(500)
                        }
                        if (!validPage(probe)) return@launch
                        if (!openedChxHome) {
                            openedChxHome = true
                            navigate(ChxSso.HOME)
                        } else failed("尚未取得学习通会话，请重试学校认证。", probe)
                    }
                }
            } catch (cancelled: CancellationException) {
                if (!closing && !currentSession()) finishFlow()
                throw cancelled
            } catch (error: Exception) {
                if (validPage(probe)) {
                    failed(if (error is AuthFailed) "${service.label}未接受当前登录，请重试学校认证。"
                        else if (error.message == "学校网页账号与当前账号不同，请先退出当前账号再登录。") error.message.orEmpty()
                        else "${service.label}暂时无法验证，可重试或继续连接其他服务。", probe)
                }
            } finally {
                if (flow.endProbe(probe)) {
                    probeJob = null
                    probing = false
                }
            }
        }
        probeJob = job
        job.start()
    }

    private suspend fun evaluate(script: String): String? = withTimeoutOrNull(3_000L) {
        suspendCancellableCoroutine { continuation ->
            web.evaluateJavascript(script) { result ->
                if (continuation.isActive) continuation.resume(result)
            }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun configure(view: WebView) {
        view.setBackgroundColor(android.graphics.Color.WHITE)
        view.isForceDarkAllowed = false
        view.settings.isAlgorithmicDarkeningAllowed = false
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(view, true)
        view.settings.javaScriptEnabled = true
        view.settings.domStorageEnabled = true
        view.settings.loadWithOverviewMode = true
        view.settings.useWideViewPort = true
        view.settings.javaScriptCanOpenWindowsAutomatically = true
        view.settings.setSupportMultipleWindows(false)
        view.settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
        view.settings.allowFileAccess = false
        view.settings.allowContentAccess = false
        view.settings.userAgentString = "Mozilla/5.0 (Linux; Android 13; K) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
        view.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                if (view !== web || pageFailed) return true
                if (closing || (initialized && !currentSession())) {
                    if (!closing) finishFlow()
                    return true
                }
                val scheme = request.url.scheme.orEmpty().lowercase(java.util.Locale.ROOT)
                if (scheme in setOf("http", "https")) {
                    if (request.isForMainFrame && !SchoolLoginWeb.isFragmentNavigation(view.url, request.url.toString())) {
                        flow.navigating(request.url.toString())
                        cancelProbe()
                        currentPage = null
                        pageFailed = false
                        pageError = null
                        pageProgress = 0
                        summary = flow.summary()
                        message = "正在加载${flow.current?.label.orEmpty()}页面…"
                        watchPageLoad()
                    }
                    return false
                }
                if (!request.isForMainFrame || scheme !in setOf("dingtalk", "alipays")) return true
                if (runCatching { startActivity(Intent(Intent.ACTION_VIEW, request.url)) }.isSuccess) {
                    message = "已打开认证应用，返回此页后继续连接。"
                } else {
                    // 外部应用缺失不破坏仍可操作的学校页面。
                    pageError = "未找到认证应用，请使用学校网页中的其他登录方式。"
                }
                return true
            }

            override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                if (view !== web || closing || !initialized || pageFailed) return
                if (!currentSession()) return finishFlow()
                if (!flow.pageStarted(url)) return
                cancelProbe()
                currentPage = url
                pageFailed = false
                pageError = null
                pageProgress = 0
                host = Uri.parse(url).host.orEmpty()
                summary = flow.summary()
                watchPageLoad()
                ChxWebSession.recordVisited(url)
            }

            override fun onPageFinished(view: WebView, url: String) {
                if (view !== web || closing || !initialized || isFinishing || isDestroyed) return
                if (!currentSession()) return finishFlow()
                if (!flow.isLoadingPage(url, view.url) || !flow.pageFinished(url, view.url)) return
                cancelPageWatch()
                pageProgress = 100
                checkPage(url)
            }

            override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                if (view === web && request.isForMainFrame && flow.matchesFailure(request.url.toString())) {
                    handleLoadFailure("网页加载失败，请检查网络或切换入口。")
                }
            }

            override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, response: WebResourceResponse) {
                if (view === web && request.isForMainFrame && flow.matchesFailure(request.url.toString())) {
                    handleLoadFailure("当前入口返回 HTTP ${response.statusCode}，可重试或切换入口。")
                }
            }

            override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) {
                handler.cancel()
                if (view === web && flow.matchesFailure(error.url)) handleLoadFailure("网页安全证书验证失败，已停止此连接。")
            }
        }
        view.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView, newProgress: Int) {
                val url = currentPage ?: return
                if (view === web && !closing && flow.isLoadingPage(url, view.url)) {
                    pageProgress = maxOf(pageProgress, newProgress.coerceIn(0, 99))
                }
            }
            // 认证页面可能把票据写入 console；不将网页日志转发到应用日志。
            override fun onConsoleMessage(message: android.webkit.ConsoleMessage): Boolean = true
        }
    }

    private fun finishFlow() {
        if (closing || isFinishing || isDestroyed) return
        closing = true
        flow.close()
        preparationJob?.cancel()
        cancelPageWatch()
        cancelProbe()
        if (::web.isInitialized) web.stopLoading()
        CookieManager.getInstance().flush()
        val current = initialized && currentSession()
        setResult(if (current && (flow.schoolReady || flow.isVerified(SchoolService.CHX))) RESULT_OK else RESULT_CANCELED,
            Intent().putExtra(EXTRA_PORTAL_READY, current && flow.isVerified(SchoolService.PORTAL))
                .putExtra(EXTRA_TEACHING_READY, current && flow.isVerified(SchoolService.TEACHING))
                .putExtra(EXTRA_CHX_READY, current && flow.isVerified(SchoolService.CHX))
                .putExtra(EXTRA_MESSAGE, if (current) flow.summary() else "学校登录尚未完成"))
        finish()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putLong("session_version", sessionVersion)
        outState.putString("process_instance", processInstance)
        outState.putBoolean("flow_prepared", initialized)
        outState.putLong("chx_version", chxVersion)
        outState.putString("teaching_entry", teachingEntry)
        outState.putStringArrayList("flow_connections", ArrayList(flow.snapshot()))
        // 不序列化网页历史：配置变化后从当前 service 重启，避免恢复并重放 ticket。
        super.onSaveInstanceState(outState)
    }

    override fun onResume() {
        super.onResume()
        if (::web.isInitialized) web.onResume()
        if (initialized && !currentSession()) finishFlow()
    }
    override fun onPause() { if (::web.isInitialized) web.onPause(); super.onPause() }
    override fun onStop() { CookieManager.getInstance().flush(); super.onStop() }
    override fun onDestroy() {
        closing = true
        flow.close()
        preparationJob?.cancel()
        cancelPageWatch()
        cancelProbe()
        if (::web.isInitialized) {
            web.stopLoading()
            (web.parent as? ViewGroup)?.removeView(web)
            web.webChromeClient = null
            web.webViewClient = WebViewClient()
            web.removeAllViews()
            web.destroy()
        }
        super.onDestroy()
    }
}
