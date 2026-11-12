package com.menglimi.gdust.v2.ui

import androidx.compose.ui.graphics.Color
import com.haooz.softglass.backdrop.drawBackdrop
import com.haooz.softglass.backdrop.effects.blur
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.menglimi.gdust.v2.data.CalendarInfo
import com.menglimi.gdust.v2.data.CourseBlock
import com.menglimi.gdust.v2.data.Repository
import com.menglimi.gdust.v2.data.isNetworkDown
import com.menglimi.gdust.v2.ui.schedule.ScheduleScreen
import com.menglimi.gdust.v2.ui.screens.LoginScreen
import com.menglimi.gdust.v2.ui.screens.InfoDialog
import com.menglimi.gdust.v2.ui.screens.NetworkDownDialog
import com.menglimi.gdust.v2.ui.screens.PowerScreen
import com.menglimi.gdust.v2.ui.screens.SettingsScreen
import com.menglimi.gdust.v2.ui.screens.TermImportScreen
import com.menglimi.gdust.v2.ui.screens.TodayScreen
import com.menglimi.gdust.v2.ui.screens.TodoScreen
import com.menglimi.gdust.v2.ui.screens.ProfileScreen
import com.menglimi.gdust.v2.ui.screens.AboutScreen
import com.menglimi.gdust.v2.ui.screens.NoticeScreen
import com.menglimi.gdust.v2.data.Notice
import kotlinx.coroutines.launch

private val TABS = listOf("今日", "课表", "电费", "待办")

@Composable
fun AppRoot(
    repo: Repository,
    tab: androidx.compose.runtime.MutableIntState,
    showSettings: androidx.compose.runtime.MutableState<Boolean>,
    loggedIn: androidx.compose.runtime.MutableState<Boolean>,
    checked: androidx.compose.runtime.MutableState<Boolean>,
    monetEnabled: androidx.compose.runtime.MutableState<Boolean>,
) {
    val scope = rememberCoroutineScope()
    // loggedIn/checked 由 MainActivity（主题树外）持有：切风格重建主题树也不丢

    var calendar by remember { mutableStateOf<CalendarInfo?>(repo.store.loadCalendar()) }
    var blocks by remember { mutableStateOf<List<CourseBlock>>(repo.store.loadBlocks()) }
    var syncError by remember { mutableStateOf<String?>(null) }
    var netDown by remember { mutableStateOf(false) }
    var syncing by remember { mutableStateOf(false) }
    var needLogin by remember { mutableStateOf(false) }
    var showWebImport by remember { mutableStateOf(false) }
    var webImportReport by remember { mutableStateOf<String?>(null) }
    var directFallbackMessage by remember { mutableStateOf<String?>(null) }
    var wallpaperTick by remember { mutableStateOf(0) }
    var showProfile by remember { mutableStateOf(false) }
    var showNotices by remember { mutableStateOf(false) }
    var notices by remember { mutableStateOf<List<Notice>>(emptyList()) }
    var noticesLoading by remember { mutableStateOf(false) }
    var showAbout by remember { mutableStateOf(false) }
    // 备份导出：SAF 建文档，写入后提示
    val context = androidx.compose.ui.platform.LocalContext.current
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


    fun sync() {
        if (syncing) return
        if (!repo.store.casTicket.isNotBlank() && !repo.hasTeachingSession() && !repo.store.hasSession) {
            needLogin = true
            return
        }
        syncing = true
        syncError = null
        scope.launch {
            val result = when {
                // 已有教务会话（网页或直连）→ 拉整学期
                repo.hasTeachingSession() -> runCatching { repo.syncTermWithCookies() }
                // 只有门户会话（例如钉钉只换了门户）→ 老实同步门户本周，不假称整学期
                repo.store.hasSession -> runCatching { repo.syncSchedule() }
                // 只有票据 → 尝试换教务会话
                else -> runCatching { repo.syncTermWithLogin() }
            }
            result
                .onSuccess { (cal, list) -> calendar = cal; blocks = list; syncError = null }
                .onFailure {
                    if (it is kotlinx.coroutines.CancellationException) throw it
                    netDown = it.isNetworkDown()
                    syncError = it.message ?: "网络失败"
                    // 只有明确的「票据失效」才提示重登；SSO 被学校拒绝时给可读文案并可切网页登录
                    if (it is com.menglimi.gdust.v2.data.AuthFailed) {
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
        }
    }

    fun loadNotices() {
        if (!repo.store.hasSession) return
        noticesLoading = true
        scope.launch {
            runCatching { repo.notices() }
                .onSuccess { notices = it }
            noticesLoading = false
        }
    }

    LaunchedEffect(Unit) {
        // App 打开即刷新桌面小组件（课程/电量可能变了）
        runCatching {
            com.menglimi.gdust.v2.widget.TodayWidget().refreshAll(context)
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
        // 打开 App 顺手续租：有教务会话且距上次同步超过 6 小时，就静默刷新一次
        val teachingReady = repo.store.casTicket.isNotBlank() || repo.hasTeachingSession()
        val stale = System.currentTimeMillis() - repo.store.lastSyncAt > 6L * 60 * 60 * 1000
        if (teachingReady && stale && repo.store.autoSyncEnabled) sync()
    }

    Crossfade(
        targetState = when {
            !checked.value -> "loading"
            showWebImport -> "web_import"
            showNotices -> "notices"
            showProfile -> "profile"
            showAbout -> "about"
            showSettings.value -> "settings"
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
            "notices" -> {
                androidx.activity.compose.BackHandler { showNotices = false }
                NoticeScreen(
                    repo = repo,
                    notices = notices,
                    loading = noticesLoading,
                    onBack = { showNotices = false },
                    onRefresh = { loadNotices() },
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
            "login" -> LoginScreen(
                repo = repo,
                onLoggedIn = {
                    loggedIn.value = true
                    // 登录成功即开启定时同步
                    runCatching { com.menglimi.gdust.v2.work.ScheduleSyncWorker.schedule(context) }
                    sync()
                },
                onOpenSettings = { showSettings.value = true },
                onPreview = { loggedIn.value = true },
            )
            "settings" -> SettingsScreen(
                repo = repo,
                syncError = syncError,
                onSync = { sync() },
                onWebImport = { showWebImport = true },
                onWallpaperChanged = { wallpaperTick++ },
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
                        .onFailure { syncError = it.message ?: "导入失败" }
                },
                onLogout = {
                    repo.store.logout()
                    blocks = emptyList()
                    calendar = null
                    syncError = null
                    loggedIn.value = false
                    showSettings.value = false
                },
                loggedIn = loggedIn.value,
                monetEnabled = monetEnabled,
                openSection = openSection,
                syncing = syncing,
                onBack = { showSettings.value = false },
            )
            "main" -> MainScaffold(
                repo = repo,
                tab = tab.intValue,
                onTab = { tab.intValue = it },
                onSettings = { showSettings.value = true },
                onOpenNotices = { showNotices = true; loadNotices() },
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
                wallpaperTick = wallpaperTick,
            )
            else -> Box(Modifier.fillMaxSize().background(LocalDs.current.bg))
        }
    }

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
    NetworkDownDialog(visible = netDown, onDismiss = { netDown = false })
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
    onTab: (Int) -> Unit,
    onSettings: () -> Unit,
    onOpenNotices: () -> Unit = {},
    notices: List<Notice> = emptyList(),
    calendar: CalendarInfo?,
    blocks: List<CourseBlock>,
    syncError: String?,
    onSwitchTerm: (String) -> Unit = {},
    wallpaperTick: Int = 0,
) {
    val ds = LocalDs.current
    // 壁纸：路径非空即启用；页面底色转透明，让壁纸透出来（tick 变化时重读）
    val wallpaperPath = androidx.compose.runtime.remember(wallpaperTick) { repo.store.wallpaperPath }
    val wallpaperOn = wallpaperPath.isNotBlank()
    androidx.compose.runtime.CompositionLocalProvider(LocalWallpaperActive provides wallpaperOn) {
        Box(Modifier.fillMaxSize()) {
            if (wallpaperOn) {
                WallpaperBackdrop(
                    path = wallpaperPath,
                    blurDp = androidx.compose.runtime.remember(wallpaperTick) { repo.store.wallpaperBlur },
                    dim = androidx.compose.runtime.remember(wallpaperTick) { repo.store.wallpaperDim },
                )
            } else {
                Box(Modifier.fillMaxSize().background(ds.bg))
            }
            Crossfade(targetState = tab, animationSpec = tween(220), label = "tabs") { page ->
                when (page) {
                    0 -> TodayScreen(
                        calendar = calendar,
                        blocks = blocks,
                        onOpenNotices = onOpenNotices,
                        unreadCount = notices.count { it.time > repo.store.lastNoticeReadAt },
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
                    )
                    2 -> PowerScreen(repo)
                    else -> TodoScreen(repo)
                }
            }

        // 底栏两套风格共用同一套点击热区实现（不受 Glasmio 内部动画/compaction 影响）
            UnifiedBottomBar(tab = tab, onTab = onTab, onSettings = onSettings, ds = ds)
        }
    }
}

/** 统一底栏：四个 tab + 设置钮，两套玻璃风格只在外观上分派，点击逻辑只有一份。 */
@Composable
private fun BoxScope.UnifiedBottomBar(
    tab: Int, onTab: (Int) -> Unit, onSettings: () -> Unit, ds: Ds,
) {
    val liquid = LocalUiStyle.current.value == UiStyle.LIQUID
    val backdrop = if (liquid) null else LocalBackdrop.current
    val pillShape = com.haooz.softglass.shape.ContinuousCapsule()
    val wallpaper = LocalWallpaperActive.current
    fun Modifier.pill(): Modifier = when {
        // 壁纸下也分风格：SOFT=白色磨砂胶囊；LIQUID=通透胶囊 + accent 细描边
        wallpaper && liquid -> this
            .background(
                if (ds.isDark) Color.Black.copy(alpha = 0.30f) else Color.White.copy(alpha = 0.30f),
                RoundedCornerShape(28.dp),
            )
            .border(1.dp, ds.accent.copy(alpha = 0.30f), RoundedCornerShape(28.dp))
        wallpaper -> this
            .background(
                if (ds.isDark) Color(0xFF1C1C21).copy(alpha = 0.62f) else Color.White.copy(alpha = 0.72f),
                RoundedCornerShape(28.dp),
            )
            .border(1.dp, Color.White.copy(alpha = if (ds.isDark) 0.12f else 0.7f), RoundedCornerShape(28.dp))
        backdrop != null -> this.drawBackdrop(backdrop = backdrop, shape = { pillShape }, effects = { blur(20f) })
        else -> this.background(ds.card, RoundedCornerShape(28.dp))
            .border(1.dp, ds.sub.copy(alpha = 0.16f), RoundedCornerShape(28.dp))
    }

    Row(
        Modifier
            .align(Alignment.BottomCenter)
            .navigationBarsPadding()
            .padding(bottom = 14.dp)
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // tab 胶囊：等宽 + 统一 46dp 高（原来按文字宽度加 padding，显得矮胖）
        Row(
            Modifier.weight(1f).pill().padding(horizontal = 6.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TABS.forEachIndexed { i, label ->
                val selected = tab == i
                Box(
                    Modifier
                        .weight(1f)
                        .height(46.dp)
                        .background(
                            if (selected) ds.accentSoft else Color.Transparent,
                            RoundedCornerShape(20.dp),
                        )
                        .clickable { onTab(i) },
                    contentAlignment = Alignment.Center,
                ) {
                    // 两套风格都带图标，避免只靠文字看不出指代
                    val symbol = when (i) {
                        0 -> io.github.neoruaa.glasmio.icons.SfSymbol.House
                        1 -> io.github.neoruaa.glasmio.icons.SfSymbol.Clock
                        2 -> io.github.neoruaa.glasmio.icons.SfSymbol.Sparkles
                        else -> io.github.neoruaa.glasmio.icons.SfSymbol.Star
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        io.github.neoruaa.glasmio.icons.SfIcon(
                            symbol = symbol,
                            contentDescription = label,
                            size = if (liquid) 19.dp else 18.dp,
                            tint = if (selected) ds.accent else ds.sub,
                        )
                        Text(
                            label,
                            fontSize = if (liquid) 10.sp else 10.sp,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                            color = if (selected) ds.accent else ds.sub,
                        )
                    }
                }
            }
        }
        // 设置钮：独立圆钮 + 齿轮图标 + 强调色描边，和四个 tab 明确区分
        Box(
            Modifier
                .pill()
                .size(46.dp)
                .border(1.dp, ds.accent.copy(alpha = 0.35f), CircleShape)
                .clickable { onSettings() },
            contentAlignment = Alignment.Center,
        ) {
            io.github.neoruaa.glasmio.icons.SfIcon(
                symbol = io.github.neoruaa.glasmio.icons.SfSymbol.Gearshape,
                contentDescription = "设置",
                size = 21.dp,
                tint = ds.accent,
            )
        }
    }
}
