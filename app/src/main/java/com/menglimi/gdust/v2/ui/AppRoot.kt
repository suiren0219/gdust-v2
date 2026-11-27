package com.menglimi.gdust.v2.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.fillMaxSize
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
import com.menglimi.gdust.v2.data.CalendarInfo
import com.menglimi.gdust.v2.data.CourseBlock
import com.menglimi.gdust.v2.data.Repository
import com.menglimi.gdust.v2.data.AuthFailed
import com.menglimi.gdust.v2.data.SchoolService
import com.menglimi.gdust.v2.data.ServiceAuthStatus
import com.menglimi.gdust.v2.data.TeachingSyncFailure
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
    schoolLogin: SchoolLoginHost,
) {
    val scope = rememberCoroutineScope()
    // 登录与启动状态由 MainActivity 保存，配置重建后保留。

    var calendar by remember { mutableStateOf<CalendarInfo?>(repo.store.loadCalendar()) }
    var blocks by remember { mutableStateOf<List<CourseBlock>>(repo.store.loadBlocks()) }
    var syncError by remember { mutableStateOf<String?>(null) }
    var syncing by remember { mutableStateOf(false) }
    var syncInBackground by remember { mutableStateOf(false) }
    var syncFailureService by remember { mutableStateOf<SchoolService?>(null) }
    var syncNeedsAuthentication by remember { mutableStateOf(false) }
    var syncNetworkFailure by remember { mutableStateOf(false) }
    var activeSyncService by remember { mutableStateOf<SchoolService?>(null) }
    var showWebImport by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    var webImportReport by remember { mutableStateOf<String?>(null) }
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
    var aiMessagesState by remember { mutableStateOf(emptyList<com.menglimi.gdust.v2.ui.screens.AiChatLine>()) }
    val settingsGroup = androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf<String?>(null) }
    var webReturnVersion by remember { mutableIntStateOf(0) }
    var schoolLoginMessage by remember { mutableStateOf<String?>(null) }
    val sessionVersion by repo.sessionVersion.collectAsState()
    val connections by repo.serviceConnections.collectAsState()
    val noticeFeed = remember(repo) {
        val publicNotices = com.menglimi.gdust.v2.data.SchoolNoticeApi()
        NoticeFeed(repo::portalSession, repo::notices, publicNotices::notices)
    }
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
            CampusService.SUMMARY -> { showAiSettings = true }
            CampusService.SETTINGS -> showSettings.value = true
        }
    }


    fun sync(preferred: SchoolService? = null, silentIfDue: Boolean = false) {
        if (syncing) return
        if (silentIfDue && (repo.nextAutomaticSyncAt() ?: Long.MAX_VALUE) > System.currentTimeMillis()) return
        val service = selectScheduleService(
            preferred, repo.serviceConnections.value, repo.store.hasSession, repo.hasTeachingSession(),
        )
        if (service == null) {
            if (silentIfDue) return
            syncFailureService = preferred ?: SchoolService.TEACHING
            syncNeedsAuthentication = true
            syncNetworkFailure = false
            syncError = "请先连接${syncFailureService!!.label}；其他已连接服务和本地数据仍可使用。"
            return
        }
        syncing = true
        syncInBackground = false
        activeSyncService = service
        syncError = null
        syncFailureService = null
        syncNeedsAuthentication = false
        syncNetworkFailure = false
        val version = repo.sessionVersion.value
        syncJob = scope.launch(start = kotlinx.coroutines.CoroutineStart.LAZY) {
            try {
                val result = kotlinx.coroutines.withTimeoutOrNull(60_000L) {
                    when {
                        silentIfDue -> repo.syncTermSilentlyIfDue()
                        service == SchoolService.TEACHING -> repo.syncTermWithCookies()
                        else -> repo.syncSchedule()
                    }
                }
                if (!silentIfDue && result == null) {
                    throw java.net.SocketTimeoutException("读取课表超过 60 秒，已停止等待。")
                }
                if (repo.isCurrentSession(version) && result != null) {
                    calendar = result.first
                    blocks = result.second
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                if (repo.isCurrentSession(version)) {
                    syncFailureService = service
                    syncNeedsAuthentication = error is AuthFailed
                    syncNetworkFailure = error.isNetworkDown()
                    if (error is AuthFailed && silentIfDue) {
                        com.menglimi.gdust.v2.work.ScheduleSyncWorker.schedule(context)
                    }
                    val sourcedError = if (service == SchoolService.TEACHING &&
                        error is java.net.SocketTimeoutException) TeachingSyncFailure(error) else error
                    syncError = syncFailureMessage(
                        sourcedError, java.time.LocalTime.now(), repo.store.maintenanceStartMin,
                        repo.store.maintenanceEndMin, repo.store.maintenanceReminderEnabled,
                    )
                }
            } finally {
                // 已取消的旧任务不能收起新任务的进度。
                if (syncJob === coroutineContext[Job]) {
                    syncing = false
                    activeSyncService = null
                    syncJob = null
                }
            }
        }
        syncJob?.start()
    }

    fun openSchoolLogin() { schoolLogin.launch(null) }
    fun openServiceLogin(service: SchoolService) { schoolLogin.launch(service) }
    fun openLearningLogin() { openServiceLogin(SchoolService.CHX) }

    LaunchedEffect(resumeRevision) {
        if (resumeRevision > 0 && repo.store.autoSyncEnabled &&
            repo.nextAutomaticSyncAt()?.let { it <= System.currentTimeMillis() } == true
        ) sync(silentIfDue = true)
    }

    LaunchedEffect(schoolLogin.result?.requestId) {
        val result = schoolLogin.result ?: return@LaunchedEffect
        schoolLogin.consume()
        if (!repo.isCurrentSession(result.sessionVersion)) return@LaunchedEffect
        val entering = !loggedIn.value
        webReturnVersion++
        if (result.anyReady) {
            loggedIn.value = true
            if (entering && result.chx && result.scheduleService == null) {
                showSettings.value = false
                showChxEntry = true
            }
            result.scheduleService?.let { service ->
                if (repo.store.autoSyncEnabled) com.menglimi.gdust.v2.work.ScheduleSyncWorker.schedule(context)
                sync(service)
            }
            if (result.teaching && repo.store.birthdayMonthDay.isBlank()) scope.launch { repo.refreshTeachingBirthday() }
        }
        schoolLoginMessage = result.message
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
        syncFailureService = null
        syncNeedsAuthentication = false
        syncNetworkFailure = false
        activeSyncService = null
        schoolLoginMessage = null
        schoolLogin.consume()
        webImportReport = null
        importReport = null
        showNotices = false
        showWebImport = false
        showChxEntry = false
        showGrades = false
        showChxCourses = false
        showProfile = false
        aiMessagesState = emptyList()
        showAiSettings = false
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
        val available = repo.serviceConnections.value.hasRestorableSession ||
            repo.store.hasSession || repo.hasTeachingSession() || repo.store.hasChxSession
        loggedIn.value = loggedIn.value || available || offlineSchedule
        checked.value = true
        // 单项验证失败不收回本地页面，也不阻塞其他服务的恢复。
        if (repo.store.hasSession) scope.launch {
            repo.refreshAccountLogin()
            if (repo.serviceConnections.value.portal.auth == ServiceAuthStatus.VERIFIED &&
                blocks.isEmpty() && !repo.hasTeachingSession()) sync(SchoolService.PORTAL)
            loadNotices()
        }
        val teachingReady = repo.hasTeachingSession() &&
            repo.serviceConnections.value.teaching.auth != ServiceAuthStatus.AUTH_REQUIRED
        val stale = System.currentTimeMillis() - repo.store.lastSyncAt > 6L * 60 * 60 * 1000
        if (teachingReady && stale && repo.store.autoSyncEnabled) sync(SchoolService.TEACHING)
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
                    onWebLogin = { openServiceLogin(SchoolService.TEACHING) })
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
                onOpenSettings = { showSettings.value = true },
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
                onOpenAiService = { showAiSettings = true },
                onProfile = { showProfile = true },
                onAbout = { showAbout = true },
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
                        .onFailure {
                        syncFailureService = null
                        syncNeedsAuthentication = false
                        syncNetworkFailure = false
                        syncError = it.message ?: "导入失败"
                    }
                },
                onLogout = { logout() },
                onReconnectService = ::openServiceLogin,
                openGroupState = settingsGroup,
                loggedIn = connections.hasRestorableSession || repo.store.hasSession ||
                    repo.hasTeachingSession() || repo.store.hasChxSession,
                chxSessionSaved = chxSessionSaved,
                openSection = openSection,
                syncing = syncing,
                onBack = { showSettings.value = false },
            )
            "main" -> MainScaffold(
                repo = repo,
                tab = tab.intValue,
                aiMessages = aiMessagesState,
                onAiMessagesChange = { aiMessagesState = it },
                onTab = { tab.intValue = it },
                onSettings = { showSettings.value = true },
                serviceSummary = serviceConnectionSummary(connections) +
                    if (syncing) "\n${activeSyncService?.label.orEmpty()}课表同步中，可继续使用其他服务" else "",
                onServiceConnections = { settingsGroup.value = "服务"; showSettings.value = true },
                onOpenServices = { showServices = true },
                onService = ::openCampusService,
                onOpenAiService = { showAiSettings = true },
                settingsSelected = showSettings.value,
                onOpenNotices = { showNotices = true; loadNotices() },
                notices = notices,
                unreadCount = notices.count { it.readId !in readNoticeIds },
                onImport = { showWebImport = true },
                calendar = calendar,
                blocks = blocks,
                syncError = syncError,
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
    if (syncing && !syncInBackground && !showWebImport && schoolLoginMessage == null) {
        androidx.compose.ui.window.Dialog(onDismissRequest = { syncInBackground = true }) {
            com.menglimi.gdust.v2.ui.screens.ScheduleLoadingCard(
                title = "正在同步课表",
                detail = if (activeSyncService == SchoolService.TEACHING) "正在读取教务课程与周次，原课表会保留到读取成功。"
                    else "正在读取学校门户课表，原课表会保留到读取成功。",
                onCancel = { syncJob?.cancel(); syncJob = null; syncing = false; activeSyncService = null },
                onContinue = { syncInBackground = true },
            )
        }
    }
    com.menglimi.gdust.v2.ui.screens.BirthdayCelebration(repo,
        enabled = loggedIn.value && !loggingOut && !disclaimerShown && schoolLoginMessage == null &&
            webImportReport == null && importReport == null && syncError == null && !showWebImport)
    if (schoolLoginMessage != null) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { schoolLoginMessage = null },
            title = { androidx.compose.material3.Text("学校服务连接") },
            text = { androidx.compose.material3.Text("$schoolLoginMessage\n\n已连接的服务可继续使用，其他服务可在设置中单独补连。") },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = { schoolLoginMessage = null }) {
                    androidx.compose.material3.Text("继续使用")
                }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = {
                    schoolLoginMessage = null
                    showSettings.value = true
                    showChxEntry = false
                    showChxCourses = false
                    showGrades = false
                }) { androidx.compose.material3.Text("管理连接") }
            },
        )
    }
    if (webImportReport != null) InfoDialog("网页登录导入完成", webImportReport!!, onDismiss = { webImportReport = null })
    if (importReport != null) {
        InfoDialog("教务课表已导入", importReport!!, onDismiss = { importReport = null })
    }
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
    if (syncError != null && !syncing && schoolLoginMessage == null && !disclaimerShown) {
        val failedService = syncFailureService
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { syncError = null },
            title = { androidx.compose.material3.Text(when {
                syncNeedsAuthentication -> "${failedService?.label ?: "学校服务"}需要认证"
                syncNetworkFailure -> "${failedService?.label ?: "学校服务"}连接暂时中断"
                else -> "数据读取未完成"
            }) },
            text = { androidx.compose.material3.Text(syncError.orEmpty().take(300) +
                "\n\n已保存的数据和其他服务仍可使用。") },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    val authenticate = syncNeedsAuthentication
                    syncError = null
                    if (authenticate && failedService != null) openServiceLogin(failedService)
                    else if (failedService != null) sync(failedService)
                }) {
                    androidx.compose.material3.Text(when {
                        syncNeedsAuthentication -> "连接${failedService?.label.orEmpty()}"
                        failedService != null -> "重试读取"
                        else -> "知道了"
                    })
                }
            },
            dismissButton = {
                androidx.compose.foundation.layout.Column {
                    if (failedService == SchoolService.TEACHING && !syncNeedsAuthentication) {
                        androidx.compose.material3.TextButton(onClick = { syncError = null; showWebImport = true }) {
                            androidx.compose.material3.Text("学期设置与导入")
                        }
                    }
                    androidx.compose.material3.TextButton(onClick = { syncError = null }) {
                        androidx.compose.material3.Text("稍后处理")
                    }
                }
            },
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
    serviceSummary: String,
    onOpenServices: () -> Unit,
    onServiceConnections: () -> Unit,
    onService: (CampusService) -> Unit,
    onOpenAiService: () -> Unit,
    settingsSelected: Boolean,
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
    val keyboardVisible = androidx.compose.foundation.layout.WindowInsets.ime
        .getBottom(androidx.compose.ui.platform.LocalDensity.current) > 0
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
                    serviceSummary = serviceSummary,
                    onServices = onServiceConnections,
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
        if (!chatInputFocused && !keyboardVisible) com.menglimi.gdust.v2.ui.nexio.NexioBottomBar(
            selectedTab = tab,
            onTabSelected = onTab,
            onSettings = onSettings,
            settingsSelected = settingsSelected,
            onAi = if (tab != 4) ({ onTab(4) }) else null,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}
