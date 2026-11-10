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
import com.menglimi.gdust.v2.ui.screens.TodayScreen
import com.menglimi.gdust.v2.ui.screens.TodoScreen
import com.menglimi.gdust.v2.ui.screens.TermImportScreen
import com.menglimi.gdust.v2.ui.screens.ProfileScreen
import com.menglimi.gdust.v2.ui.screens.AboutScreen
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
    var showImport by remember { mutableStateOf(false) }
    var showProfile by remember { mutableStateOf(false) }
    var showAbout by remember { mutableStateOf(false) }
    var importReport by remember { mutableStateOf<String?>(null) }
    // 设置页当前展开的编辑区（切风格重建时收起即可，不影响页面栈）
    val openSection = remember { mutableStateOf<String?>(null) }


    fun sync() {
        if (syncing) return
        val teaching = repo.store.scheduleSource == "zf_term"
        if (teaching && repo.store.loadTeachingSession() == null) {
            showImport = true
            return
        }
        if (!teaching && !repo.store.hasSession) {
            needLogin = true
            return
        }
        syncing = true
        syncError = null
        scope.launch {
            runCatching { if (teaching) repo.syncTeachingSchedule() else repo.syncSchedule() }
                .onSuccess { (cal, list) -> calendar = cal; blocks = list; syncError = null }
                .onFailure {
                    if (it is kotlinx.coroutines.CancellationException) throw it
                    netDown = it.isNetworkDown()
                    syncError = it.message ?: "网络失败"
                    if (it is com.menglimi.gdust.v2.data.AuthFailed && teaching) showImport = true
                }
            syncing = false
        }
    }

    LaunchedEffect(Unit) {
        // 先恢复本机会话和缓存页面，网络校验在后；超时不让启动卡在空白页。
        val offlineSchedule = blocks.isNotEmpty()
        loggedIn.value = loggedIn.value || repo.store.hasSession || offlineSchedule
        checked.value = true
        if (repo.store.hasSession) {
            val alive = repo.isSessionAlive()
            if (!alive && !offlineSchedule) loggedIn.value = false
            if (alive && blocks.isEmpty()) sync()
        }
    }

    Crossfade(
        targetState = when {
            !checked.value -> "loading"
            showImport -> "import"
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
            "import" -> TermImportScreen(
                repo = repo,
                onBack = { showImport = false },
                onImported = { cal, list ->
                    calendar = cal
                    blocks = list
                    showImport = false
                    showSettings.value = false
                    loggedIn.value = true
                    tab.intValue = 1
                    importReport = "已读取 ${list.size} 条实际排课，覆盖 ${list.map { it.week }.distinct().size} 个周次。仅显示教务返回的课程；后续调课请重新导入核对。"
                },
            )
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
                    sync()
                },
                onOpenSettings = { showSettings.value = true },
                onPreview = { loggedIn.value = true },
            )
            "settings" -> SettingsScreen(
                repo = repo,
                syncError = syncError,
                onSync = { sync() },
                onImport = { showImport = true },
                onProfile = { showProfile = true },
                onAbout = { showAbout = true },
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
                calendar = calendar,
                blocks = blocks,
                syncError = syncError,
            )
            else -> Box(Modifier.fillMaxSize().background(LocalDs.current.bg))
        }
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
    calendar: CalendarInfo?,
    blocks: List<CourseBlock>,
    syncError: String?,
) {
    val ds = LocalDs.current
    Box(Modifier.fillMaxSize().background(ds.bg)) {
        Crossfade(targetState = tab, animationSpec = tween(220), label = "tabs") { page ->
            when (page) {
                0 -> TodayScreen(calendar = calendar, blocks = blocks)
                1 -> ScheduleScreen(
                    calendar = calendar,
                    blocks = blocks,
                    syncError = syncError,
                    onPrevWeek = {},
                    onNextWeek = {},
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

/** 统一底栏：四个 tab + 设置钮，两套玻璃风格只在外观上分派，点击逻辑只有一份。 */
@Composable
private fun BoxScope.UnifiedBottomBar(
    tab: Int, onTab: (Int) -> Unit, onSettings: () -> Unit, ds: Ds,
) {
    val liquid = LocalUiStyle.current.value == UiStyle.LIQUID
    val backdrop = if (liquid) null else LocalBackdrop.current
    val pillShape = com.haooz.softglass.shape.ContinuousCapsule()
    fun Modifier.pill(): Modifier =
        if (backdrop != null) this.drawBackdrop(backdrop = backdrop, shape = { pillShape }, effects = { blur(20f) })
        else this.background(ds.card, RoundedCornerShape(28.dp)).border(1.dp, ds.sub.copy(alpha = 0.16f), RoundedCornerShape(28.dp))

    Row(
        Modifier
            .align(Alignment.BottomCenter)
            .navigationBarsPadding()
            .padding(bottom = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            Modifier.pill().padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            TABS.forEachIndexed { i, label ->
                val selected = tab == i
                Box(
                    Modifier
                        .background(
                            if (selected) ds.accentSoft else Color.Transparent,
                            RoundedCornerShape(20.dp),
                        )
                        .clickable { onTab(i) }
                        .padding(horizontal = 17.dp, vertical = 10.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    if (liquid) {
                        val symbol = when (i) {
                            0 -> io.github.neoruaa.glasmio.icons.SfSymbol.House
                            1 -> io.github.neoruaa.glasmio.icons.SfSymbol.Clock
                            2 -> io.github.neoruaa.glasmio.icons.SfSymbol.Sparkles
                            else -> io.github.neoruaa.glasmio.icons.SfSymbol.Star
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            io.github.neoruaa.glasmio.icons.SfIcon(
                                symbol = symbol,
                                contentDescription = null,
                                size = 20.dp,
                                tint = if (selected) ds.accent else ds.sub,
                            )
                            Text(label, fontSize = 11.sp, color = if (selected) ds.accent else ds.sub)
                        }
                    } else {
                        Text(
                            label,
                            fontSize = 13.sp,
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
                .size(56.dp)
                .border(1.5.dp, ds.accent.copy(alpha = 0.35f), CircleShape)
                .clickable { onSettings() },
            contentAlignment = Alignment.Center,
        ) {
            if (liquid) {
                io.github.neoruaa.glasmio.icons.SfIcon(
                    symbol = io.github.neoruaa.glasmio.icons.SfSymbol.Gearshape,
                    contentDescription = "设置",
                    size = 24.dp,
                    tint = ds.accent,
                )
            } else {
                Text("设", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = ds.accent)
            }
        }
    }
}
