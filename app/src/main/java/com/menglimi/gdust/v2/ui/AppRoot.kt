package com.menglimi.gdust.v2.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.menglimi.gdust.v2.data.CalendarInfo
import com.menglimi.gdust.v2.data.CourseBlock
import com.menglimi.gdust.v2.data.Repository
import com.menglimi.gdust.v2.data.isNetworkDown
import com.menglimi.gdust.v2.data.syncFailureMessage
import com.menglimi.gdust.v2.ui.schedule.ScheduleScreen
import com.menglimi.gdust.v2.ui.screens.LoginScreen
import com.menglimi.gdust.v2.ui.screens.InfoDialog
import com.menglimi.gdust.v2.ui.screens.PowerScreen
import com.menglimi.gdust.v2.ui.screens.SettingsScreen
import com.menglimi.gdust.v2.ui.screens.TermImportScreen
import com.menglimi.gdust.v2.ui.screens.TodayScreen
import com.menglimi.gdust.v2.ui.screens.TodoScreen
import com.menglimi.gdust.v2.ui.screens.WidgetPreviewScreen
import com.menglimi.gdust.v2.ui.screens.ProfileScreen
import com.menglimi.gdust.v2.ui.screens.AboutScreen
import com.menglimi.gdust.v2.ui.screens.CampusService
import com.menglimi.gdust.v2.ui.screens.CampusServicesScreen
import com.menglimi.gdust.v2.ui.screens.NoticeScreen
import com.menglimi.gdust.v2.ui.screens.ReminderScreen
import com.menglimi.gdust.v2.ui.screens.ChxNotificationScreen
import com.menglimi.gdust.v2.ui.screens.ChxEntryScreen
import com.menglimi.gdust.v2.ui.screens.ChxCoursesScreen
import com.menglimi.gdust.v2.ui.screens.ChxWebActivity
import com.menglimi.gdust.v2.ui.screens.GradesScreen
import com.menglimi.gdust.v2.ui.screens.AiChatLine
import com.menglimi.gdust.v2.ui.screens.AiChatScreen
import com.menglimi.gdust.v2.ui.screens.AiServiceSettingsScreen
import com.menglimi.gdust.v2.data.ChxSso
import com.menglimi.gdust.v2.data.Notice
import com.menglimi.gdust.v2.data.NoticeFeed
import com.menglimi.gdust.v2.data.readId
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.icon.MiuixIcons
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** 首次使用须知：说明本机直连与数据边界，确认一次即可。 */
private const val DISCLAIMER_TEXT =
    "本应用在你的设备上直连学校教务系统与学习通（超星）官方接口：登录、课表、作业、考试与电量查询" +
        "都由本机直接请求学校服务器与超星服务器，不经过作者或任何第三方服务器。\n\n" +
        "· 密码与登录凭证仅保存在本机加密存储；作者不收集、不中转、看不到你的任何数据。\n" +
        "· 请仅使用本人账号，并遵守学校相关规定。\n" +
        "· 作业与考试只做查询和到期提醒，本应用不会自动提交任何作业或考试。\n" +
        "· 学校服务器的可用性不受本应用控制，维护安排以学校通知为准。\n\n" +
        "继续使用即表示你已知悉并同意以上内容。"

@Composable
fun AppRoot(
    repo: Repository,
    tab: androidx.compose.runtime.MutableIntState,
    showSettings: androidx.compose.runtime.MutableState<Boolean>,
    loggedIn: androidx.compose.runtime.MutableState<Boolean>,
    checked: androidx.compose.runtime.MutableState<Boolean>,
    aiMessages: androidx.compose.runtime.MutableState<List<AiChatLine>>,
) {
    val scope = rememberCoroutineScope()
    // 登录与启动状态由 MainActivity 保存，配置重建后保留。

    var calendar by remember { mutableStateOf<CalendarInfo?>(repo.store.loadCalendar()) }
    var blocks by remember { mutableStateOf<List<CourseBlock>>(repo.store.loadBlocks()) }
    var syncError by remember { mutableStateOf<String?>(null) }
    var netDown by remember { mutableStateOf(false) }
    var syncing by remember { mutableStateOf(false) }
    var needLogin by remember { mutableStateOf(false) }
    var showWebImport by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    var webImportReport by remember { mutableStateOf<String?>(null) }
    var directFallbackMessage by remember { mutableStateOf<String?>(null) }
    var showProfile by remember { mutableStateOf(false) }
    var showNotices by remember { mutableStateOf(false) }
    var showReminder by remember { mutableStateOf(false) }
    var showTodoReminder by remember { mutableStateOf(false) }
    var showChxNotifications by remember { mutableStateOf(false) }
    var showChxEntry by remember { mutableStateOf(false) }
    var showGrades by remember { mutableStateOf(false) }
    var showChxCourses by remember { mutableStateOf(false) }
    var showWidgets by remember { mutableStateOf(false) }
    var showAiSettings by remember { mutableStateOf(false) }
    var settingsSelected by remember { mutableStateOf(false) }
    var aiMessagesState by remember { mutableStateOf(aiMessages.value) }
    var webReturnVersion by remember { mutableIntStateOf(0) }
    var schoolLoginMessage by remember { mutableStateOf<String?>(null) }
    val sessionVersion by repo.sessionVersion.collectAsState()
    val publicNotices = com.menglimi.gdust.v2.data.SchoolNoticeApi()
    val noticeFeed = remember(repo) { NoticeFeed(repo::portalSession, repo::notices, publicNotices::notices) }
    val noticeState by noticeFeed.state.collectAsState()
    val notices = noticeState.notices
    val noticesLoading = noticeState.loading
    val noticesError = noticeState.error
    val noticeAccount = repo.store.jobNumber
    var readNoticeIds by remember(noticeAccount) { mutableStateOf(repo.store.readNoticeIds(noticeAccount)) }
    var syncJob by remember { mutableStateOf<Job?>(null) }
    var loggingOut by remember { mutableStateOf(false) }
    var logoutFailed by remember { mutableStateOf(repo.store.webLogoutPending) }
    var showAbout by remember { mutableStateOf(false) }
    var showServices by remember { mutableStateOf(false) }
    // 免责声明：普通 prefs 不触发重组，用状态控制弹窗，确认后写盘不再出现。
    var disclaimerShown by remember { mutableStateOf(!repo.store.disclaimerAccepted) }

    DisposableEffect(repo) {
        val stop = repo.store.observeSchedule {
            calendar = repo.store.loadCalendar()
            blocks = repo.store.loadBlocks()
        }
        onDispose { stop() }
    }
    DisposableEffect(repo, noticeAccount) {
        val stop = repo.store.observeNoticeReads {
            readNoticeIds = repo.store.readNoticeIds(noticeAccount)
        }
        onDispose { stop() }
    }
    // 联动状态是 plain prefs，静态读取不会重组；观察保存/清除事件保持设置页文案同步
    var chxSessionSaved by remember { mutableStateOf(repo.store.hasChxSession) }
    DisposableEffect(repo) {
        val stop = repo.store.observeChxSession { chxSessionSaved = repo.store.hasChxSession }
        onDispose { stop() }
    }
    DisposableEffect(noticeFeed) {
        onDispose { noticeFeed.clear() }
    }
    // 备份导出：SAF 建文档，写入后提示
    val context = androidx.compose.ui.platform.LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var resumeRevision by remember { mutableIntStateOf(0) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) resumeRevision++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val serviceWeb = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult(),
    ) { webReturnVersion++ }
    fun openServiceWeb(url: String) {
        repo.invalidateChxRequests()
        serviceWeb.launch(android.content.Intent(context, ChxWebActivity::class.java)
            .putExtra(ChxWebActivity.EXTRA_URL, url))
    }
    val exportLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        if (uri != null) {
            scope.launch {
                runCatching {
                    context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use {
                        it.write(repo.store.exportBackup())
                    } ?: error("无法写入所选位置")
                }
            }
        }
    }
    var importReport by remember { mutableStateOf<String?>(null) }
    // 设置页当前展开的编辑区（切风格重建时收起即可，不影响页面栈）
    val openSection = remember { mutableStateOf<String?>(null) }
    val settingsGroup = androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf<String?>(null) }
    val hasAccount = repo.store.hasSession || repo.store.casTicket.isNotBlank() || repo.hasTeachingSession()

    fun loadNotices() { noticeFeed.refresh(scope) }

    fun openCampusService(service: CampusService) {
        when (service) {
            CampusService.SCHEDULE -> { showServices = false; tab.intValue = 1 }
            CampusService.TODO -> { showServices = false; tab.intValue = 3 }
            CampusService.POWER -> { showServices = false; tab.intValue = 2 }
            CampusService.GRADES -> showGrades = true
            CampusService.CHX_COURSES -> showChxCourses = true
            CampusService.IMPORT -> showWebImport = true
            CampusService.NOTICES -> { showNotices = true; loadNotices() }
            CampusService.FIVE_EDUCATION -> openServiceWeb("http://172.16.1.232/index")
            CampusService.LESSON_REMINDER -> showReminder = true
            CampusService.TODO_REMINDER -> showTodoReminder = true
            CampusService.WIDGETS -> showWidgets = true
            CampusService.CHX_LINK -> showChxEntry = true
            CampusService.CHX_NOTIFICATIONS -> showChxNotifications = true
            CampusService.SUMMARY, CampusService.SETTINGS -> showSettings.value = true
        }
    }


    fun sync(silentIfDue: Boolean = false) {
        if (syncing) return
        if (silentIfDue && (repo.nextAutomaticSyncAt() ?: Long.MAX_VALUE) > System.currentTimeMillis()) return
        if (!repo.store.casTicket.isNotBlank() && !repo.hasTeachingSession() && !repo.store.hasSession) {
            needLogin = true
            return
        }
        syncing = true
        syncError = null
        netDown = false
        val version = repo.sessionVersion.value
        syncJob = scope.launch {
            val result = if (silentIfDue) {
                runCatching {
                    kotlinx.coroutines.withTimeoutOrNull(60_000L) { repo.syncTermSilentlyIfDue() }
                }
            } else {
                runCatching {
                    kotlinx.coroutines.withTimeoutOrNull(60_000L) {
                        when {
                            repo.hasTeachingSession() -> repo.syncTermWithCookies()
                            repo.store.hasSession -> repo.syncSchedule()
                            else -> repo.syncTermWithCookies()
                        }
                    } ?: error("读取课表超过 60 秒，已停止等待，请稍后重试。")
                }
            }
            if (!repo.isCurrentSession(version)) {
                syncing = false
                return@launch
            }
            result
                .onSuccess { data ->
                    data?.let { (cal, list) -> calendar = cal; blocks = list; syncError = null }
                }
                .onFailure {
                    if (it is kotlinx.coroutines.CancellationException) {
                        syncing = false
                        syncJob = null
                        throw it
                    }
                    val displayError = syncFailureMessage(
                        it, java.time.LocalTime.now(), repo.store.maintenanceStartMin,
                        repo.store.maintenanceEndMin, repo.store.maintenanceReminderEnabled,
                    )
                    netDown = it.isNetworkDown()
                    syncError = displayError
                    // 只有明确的「票据失效」才提示重登；SSO 被学校拒绝时给可读文案并可切网页登录
                    if (it is com.menglimi.gdust.v2.data.AuthFailed) {
                        if (silentIfDue) com.menglimi.gdust.v2.work.ScheduleSyncWorker.schedule(context)
                        needLogin = true
                    } else if (it.message?.let { m ->
                            m.contains("学校未用登录票据放行教务会话") || m.contains("旧版本门户登录态") ||
                                m.contains("未放行") || m.contains("返回的是网页") ||
                                m.contains("缺少 kbList") || m.contains("教务课表读取失败")
                        } == true
                    ) {
                        // 网络/通道层面拿不到教务数据：重登没用，直接引导网页登录
                        directFallbackMessage = it.message
                    }
                }
            syncing = false
            syncJob = null
        }
    }

    LaunchedEffect(resumeRevision) {
        if (resumeRevision > 0 && repo.store.autoSyncEnabled &&
            repo.nextAutomaticSyncAt()?.let { it <= System.currentTimeMillis() } == true
        ) sync(silentIfDue = true)
    }

    val schoolLogin = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val data = result.data
        val portal = data?.getBooleanExtra(com.menglimi.gdust.v2.ui.screens.SchoolLoginActivity.EXTRA_PORTAL_READY, false) == true
        val teaching = data?.getBooleanExtra(com.menglimi.gdust.v2.ui.screens.SchoolLoginActivity.EXTRA_TEACHING_READY, false) == true
        webReturnVersion++
        if (portal || teaching) {
            loggedIn.value = true
            if (repo.store.autoSyncEnabled) com.menglimi.gdust.v2.work.ScheduleSyncWorker.schedule(context)
            sync()
            if (teaching && repo.store.birthdayMonthDay.isBlank()) scope.launch { repo.refreshTeachingBirthday() }
        }
        schoolLoginMessage = data?.getStringExtra(com.menglimi.gdust.v2.ui.screens.SchoolLoginActivity.EXTRA_MESSAGE)?.takeIf { it.isNotBlank() }
    }
    fun openSchoolLogin() {
        schoolLogin.launch(com.menglimi.gdust.v2.ui.screens.SchoolLoginActivity.intent(context))
    }
    fun openLearningLogin() {
        schoolLogin.launch(com.menglimi.gdust.v2.ui.screens.SchoolLoginActivity.learningIntent(context))
    }

    fun logout() {
        if (loggingOut) return
        loggingOut = true
        logoutFailed = false
        noticeFeed.clear()
        syncJob?.cancel()
        syncing = false
        blocks = emptyList()
        calendar = null
        syncError = null
        netDown = false
        needLogin = false
        directFallbackMessage = null
        webImportReport = null
        importReport = null
        showNotices = false
        showWebImport = false
        aiMessagesState = emptyList()
        aiMessages.value = emptyList()
        showChxEntry = false
        showGrades = false
        showChxCourses = false
        showProfile = false
        showSettings.value = false
        loggedIn.value = false
        scope.launch {
            try {
                logoutFailed = !repo.logout()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                logoutFailed = true
            } finally {
                loggingOut = false
            }
        }
    }

    LaunchedEffect(sessionVersion) {
        if (sessionVersion != 0L && !repo.store.hasSession) {
            noticeFeed.clear()
            syncJob?.cancel()
            syncing = false
            calendar = repo.store.loadCalendar()
            blocks = repo.store.loadBlocks()
        }
    }

    LaunchedEffect(Unit) {
        // App 打开即刷新桌面小组件（课程/电量可能变了）
        runCatching {
            com.menglimi.gdust.v2.widget.WidgetRefresher.refreshAll(context)
        }
        // 先恢复本机会话和缓存页面，网络校验在后；超时不让启动卡在空白页。
        val offlineSchedule = blocks.isNotEmpty()
        loggedIn.value = loggedIn.value || repo.store.hasSession || offlineSchedule
        checked.value = true
        if (repo.store.hasSession) {
            val alive = repo.isSessionAlive()
            if (!alive && !offlineSchedule) loggedIn.value = false
            if (alive && blocks.isEmpty()) sync()
            loadNotices()
        }
        // 启动时补一次已到期的四小时静默课表维护；Worker 负责后续周期。
        if (repo.store.autoSyncEnabled && repo.nextAutomaticSyncAt()?.let { it <= System.currentTimeMillis() } == true) {
            sync(silentIfDue = true)
        }
    }

    Crossfade(
        targetState = when {
            !checked.value -> "loading"
            showWebImport -> "web_import"
            showGrades -> "grades"
            showChxCourses -> "chx_courses"
            showNotices -> "notices"
            showReminder -> "reminder"
            showTodoReminder -> "todo_reminder"
            showChxNotifications -> "chx_notifications"
            showChxEntry -> "chx_entry"
            showProfile -> "profile"
            showAbout -> "about"
            showWidgets -> "widgets"
            showAiSettings -> "ai_settings"
            showSettings.value -> "settings"
            showServices -> "services"
            tab.intValue == 4 -> "main"
            !loggedIn.value -> "login"
            else -> "main"
        },
        animationSpec = tween(220),
        label = "root",
    ) { state ->
        when (state) {
            "web_import" -> {
                androidx.activity.compose.BackHandler { showWebImport = false }
                TermImportScreen(
                    repo = repo,
                    onBack = { showWebImport = false },
                    onImported = { cal, list ->
                        calendar = cal
                        blocks = list
                        showWebImport = false
                        showSettings.value = false
                        loggedIn.value = true
                        tab.intValue = 1
                        webImportReport = "网页导入成功：${list.size} 条真实排课，覆盖 ${list.map { it.week }.distinct().size} 周。"
                    },
                )
            }
            "reminder" -> {
                androidx.activity.compose.BackHandler { showReminder = false }
                ReminderScreen(repo = repo, onBack = { showReminder = false })
            }
            "todo_reminder" -> {
                androidx.activity.compose.BackHandler { showTodoReminder = false }
                com.menglimi.gdust.v2.ui.screens.TodoReminderScreen(repo = repo, onBack = { showTodoReminder = false })
            }
            "chx_notifications" -> {
                androidx.activity.compose.BackHandler { showChxNotifications = false }
                ChxNotificationScreen(repo = repo, onBack = { showChxNotifications = false })
            }
            "grades" -> androidx.compose.runtime.key(webReturnVersion) {
                GradesScreen(repo = repo, onBack = { showGrades = false },
                    onWebLogin = ::openSchoolLogin)
            }
            "chx_courses" -> androidx.compose.runtime.key(webReturnVersion) {
                ChxCoursesScreen(repo = repo, onBack = { showChxCourses = false },
                    onLogin = ::openLearningLogin, onOpenCourse = ::openServiceWeb)
            }
            "chx_entry" -> {
                androidx.activity.compose.BackHandler { showChxEntry = false }
                androidx.compose.runtime.key(webReturnVersion) {
                    ChxEntryScreen(repo = repo, onBack = { showChxEntry = false },
                        onCourses = { showChxCourses = true }, onConnect = ::openLearningLogin,
                        onNotifications = { showChxNotifications = true })
                }
            }
            "notices" -> {
                androidx.activity.compose.BackHandler { showNotices = false }
                NoticeScreen(
                    repo = repo,
                    notices = notices,
                    loading = noticesLoading,
                    error = noticesError,
                    readIds = readNoticeIds,
                    updatedAt = noticeState.updatedAt,
                    onMarkRead = { items -> repo.store.markNoticesRead(noticeAccount, items) },
                    onBack = { showNotices = false },
                    onRefresh = { loadNotices() },
                    source = noticeState.source,
                    onSourceChange = { noticeFeed.refresh(scope, it) },
                )
            }
            "profile" -> {
                androidx.activity.compose.BackHandler { showProfile = false }
                ProfileScreen(repo = repo, onBack = { showProfile = false })
            }
            "about" -> {
                androidx.activity.compose.BackHandler { showAbout = false }
                AboutScreen(repo = repo, onBack = { showAbout = false })
            }
            "widgets" -> {
                androidx.activity.compose.BackHandler { showWidgets = false }
                WidgetPreviewScreen(onBack = { showWidgets = false })
            }
            "ai_settings" -> {
                AiServiceSettingsScreen(
                    repo = repo,
                    onBack = { showAiSettings = false },
                    onOpenNotices = { showNotices = true; loadNotices() },
                )
            }
            "services" -> CampusServicesScreen(
                signedIn = hasAccount,
                accountName = repo.store.realName,
                onLogin = { showServices = false; showSettings.value = false; loggedIn.value = false },
                onOpen = ::openCampusService,
                onBack = { showServices = false },
            )
            "login" -> LoginScreen(
                repo = repo,
                onLoggedIn = {
                    loggedIn.value = true
                    openSchoolLogin()
                },
                onOpenSettings = { showSettings.value = true; settingsSelected = true },
                onPreview = { loggedIn.value = true },
                onSchoolLogin = ::openSchoolLogin,
            )
            "settings" -> SettingsScreen(
                repo = repo,
                syncError = syncError,
                onSync = { sync() },
                onWebImport = { showWebImport = true },
                onReminder = { showReminder = true },
                onTodoReminder = { showTodoReminder = true },
                onChxNotifications = { showChxNotifications = true },
                onChxEntry = { showChxEntry = true },
                onGrades = { showGrades = true },
                onChxCourses = { showChxCourses = true },
                onSchoolNotices = { showNotices = true; loadNotices() },
                onFiveEducation = { openServiceWeb("http://172.16.1.232/index") },
                onProfile = { showProfile = true },
                onAbout = { showAbout = true },
                onOpenAiService = { showAiSettings = true },
                onExportBackup = exportLauncher,
                onImportBackup = { uri ->
                    runCatching {
                        context.contentResolver.openInputStream(uri)?.bufferedReader()?.readText().orEmpty()
                    }.map { repo.store.importBackup(it) }
                        .onSuccess { result ->
                            calendar = repo.store.loadCalendar()
                            blocks = repo.store.loadBlocks()
                            syncError = null
                            importReport = "备份已恢复：$result"
                        }
                        .onFailure { syncError = it.message ?: "导入失败" }
                },
                onLogout = { logout() },
                loggedIn = loggedIn.value,
                chxSessionSaved = chxSessionSaved,
                openSection = openSection,
                openGroupState = settingsGroup,
                syncing = syncing,
                onBack = { showSettings.value = false },
            )
            "main" -> MainScaffold(
                repo = repo,
                tab = tab.intValue,
                onTab = { tab.intValue = it },
                onSettings = { showSettings.value = true; settingsSelected = true },
                onOpenServices = { showServices = true },
                onService = ::openCampusService,
                onOpenNotices = { showNotices = true; loadNotices() },
                notices = notices,
                unreadCount = notices.count { it.readId !in readNoticeIds },
                onImport = { showWebImport = true },
                calendar = calendar,
                blocks = blocks,
                syncError = syncError,
                aiMessages = aiMessagesState,
                onAiMessagesChange = { aiMessagesState = it; aiMessages.value = it },
                onOpenAiService = { showAiSettings = true },
                settingsSelected = settingsSelected,
                onSwitchTerm = { sem ->
                    val cal = calendar
                    if (cal != null && sem != cal.semester) {
                        val updated = cal.copy(semester = sem)
                        repo.store.saveCalendar(updated)
                        calendar = updated
                        // 切学期后旧课表不再代表当前学期：清空再同步（失败会明确提示）
                        blocks = emptyList()
                        sync()
                    }
                },
            )
            else -> Box(Modifier.fillMaxSize().background(LocalDs.current.bg))
        }
    }

    if (logoutFailed && !loggingOut) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { logoutFailed = false },
            title = { androidx.compose.material3.Text("退出未完全完成") },
            text = { androidx.compose.material3.Text("本机账号数据已隔离，但网页会话清理未确认。可重试清理，不影响重新登录。") },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = { logoutFailed = false; logout() }) {
                    androidx.compose.material3.Text("重试清理")
                }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { logoutFailed = false }) {
                    androidx.compose.material3.Text("稍后处理")
                }
            },
        )
    }
    if (syncing && !showWebImport && schoolLoginMessage == null) {
        androidx.compose.ui.window.Dialog(onDismissRequest = {
            syncJob?.cancel(); syncJob = null; syncing = false
        }) {
            com.menglimi.gdust.v2.ui.screens.ScheduleLoadingCard(
                title = "正在同步课表",
                detail = if (repo.hasTeachingSession()) "正在读取教务课程与周次，原课表会保留到读取成功。"
                    else "正在读取学校门户课表，原课表会保留到读取成功。",
                onCancel = { syncJob?.cancel(); syncJob = null; syncing = false },
            )
        }
    }
    com.menglimi.gdust.v2.ui.screens.BirthdayCelebration(repo,
        enabled = loggedIn.value && !loggingOut && !disclaimerShown && schoolLoginMessage == null &&
            webImportReport == null && directFallbackMessage == null && importReport == null &&
            !netDown && !needLogin && syncError == null && !showWebImport)
    if (schoolLoginMessage != null) InfoDialog("学校服务连接", schoolLoginMessage!!, onDismiss = { schoolLoginMessage = null })
    if (webImportReport != null) InfoDialog("网页登录导入完成", webImportReport!!, onDismiss = { webImportReport = null })
    if (directFallbackMessage != null) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { directFallbackMessage = null },
            title = { androidx.compose.material3.Text("自动登录未获教务授权") },
            text = { androidx.compose.material3.Text("${directFallbackMessage}\n\n可以改用拾光兼容网页登录导入；登录一次后，网页登录 Cookie 会单独保存在本机。") },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = { directFallbackMessage = null; showWebImport = true }) {
                    androidx.compose.material3.Text("打开网页登录")
                }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { directFallbackMessage = null }) {
                    androidx.compose.material3.Text("稍后再说")
                }
            },
        )
    }
    if (importReport != null) {
        InfoDialog("教务课表已导入", importReport!!, onDismiss = { importReport = null })
    }
    // 网络层失败（连不上学校官网）时弹窗提示，业务错误仍走行内提示
    if (netDown && !syncing) InfoDialog("连接暂时中断", syncError ?: "请检查网络后重试。",
        onDismiss = { netDown = false; syncError = null })
    if (disclaimerShown) {
        InfoDialog(
            title = "使用须知与免责声明",
            message = DISCLAIMER_TEXT,
            confirmText = "我已阅读并同意",
            onDismiss = {
                repo.store.disclaimerAccepted = true
                disclaimerShown = false
            },
        )
    }
    if (needLogin) {
        InfoDialog(
            title = "还没登录",
            message = "同步课表需要先登录统一身份认证。\n回到登录页用学号密码或钉钉扫码登录，登录后会自动同步。",
            onDismiss = { needLogin = false },
        )
    }
    if (!netDown && syncError != null && !syncing) {
        InfoDialog(
            title = "同步失败",
            message = syncError!!.take(200),
            onDismiss = { syncError = null },
        )
    }
}

@Composable
private fun MainScaffold(
    repo: Repository,
    tab: Int,
    aiMessages: List<AiChatLine>,
    onAiMessagesChange: (List<AiChatLine>) -> Unit,
    onTab: (Int) -> Unit,
    onSettings: () -> Unit,
    onOpenAiService: () -> Unit,
    settingsSelected: Boolean,
    onOpenServices: () -> Unit,
    onService: (CampusService) -> Unit,
    onOpenNotices: () -> Unit = {},
    notices: List<Notice> = emptyList(),
    unreadCount: Int = 0,
    calendar: CalendarInfo?,
    blocks: List<CourseBlock>,
    syncError: String?,
    onSwitchTerm: (String) -> Unit = {},
    onImport: () -> Unit = {},
) {
    val ds = LocalDs.current
    var chatInputFocused by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxSize().background(ds.bg)) {
        Crossfade(
            targetState = tab,
            animationSpec = tween(220),
            label = "tabs",
            modifier = Modifier.fillMaxSize(),
        ) { page ->
            when (page) {
                0 -> TodayScreen(
                    repo = repo,
                    calendar = calendar,
                    blocks = blocks,
                    onOpenNotices = onOpenNotices,
                    unreadCount = unreadCount,
                    onOpenSchedule = { onTab(1) },
                    onImport = onImport,
                    onOpenServices = onOpenServices,
                    onService = onService,
                    hasImportedSchedule = repo.store.scheduleSource == "zf_term" || blocks.isNotEmpty(),
                )
                1 -> ScheduleScreen(
                    repo = repo,
                    calendar = calendar,
                    blocks = blocks,
                    syncError = syncError,
                    onPrevWeek = {},
                    onNextWeek = {},
                    onSwitchTerm = onSwitchTerm,
                    fullTermImported = repo.store.scheduleSource == "zf_term",
                    onImport = onImport,
                )
                2 -> PowerScreen(repo)
                3 -> TodoScreen(repo)
                4 -> AiChatScreen(
                    repo = repo,
                    calendar = calendar,
                    blocks = blocks,
                    todos = repo.store.loadTodos(),
                    messages = aiMessages,
                    onMessagesChange = onAiMessagesChange,
                    onInputFocusChanged = { chatInputFocused = it },
                    onBack = { onTab(0) },
                    onSettings = onOpenAiService,
                )
            }
        }
        if (!chatInputFocused) com.menglimi.gdust.v2.ui.nexio.NexioBottomBar(
            selectedTab = tab,
            onTabSelected = onTab,
            onSettings = onSettings,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
        if (tab != 4) {
            val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
            androidx.compose.foundation.layout.Column(
                Modifier.align(Alignment.BottomStart)
                    .padding(start = 0.dp, bottom = bottom + 104.dp)
                    .width(42.dp)
                    .clip(RoundedCornerShape(topEnd = 18.dp, bottomEnd = 18.dp))
                    .background(ds.card)
                    .clickable(
                        role = androidx.compose.ui.semantics.Role.Button,
                        onClick = { onTab(4) },
                    )
                    .semantics { contentDescription = "打开 AI 学习助手" }
                    .padding(vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Icon(AiSparkleIcon, null, Modifier.size(20.dp), tint = ds.accent)
                top.yukonga.miuix.kmp.basic.Text(
                    "AI", fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = ds.text,
                )
            }
        }
    }
}
