package com.menglimi.gdust.v2.ui

import androidx.compose.ui.graphics.Color
import com.haooz.softglass.backdrop.drawBackdrop
import com.haooz.softglass.backdrop.effects.blur
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import com.menglimi.gdust.v2.ui.schedule.ScheduleScreen
import com.menglimi.gdust.v2.ui.screens.LoginScreen
import com.menglimi.gdust.v2.ui.screens.PowerScreen
import com.menglimi.gdust.v2.ui.screens.SettingsScreen
import com.menglimi.gdust.v2.ui.screens.TodayScreen
import com.menglimi.gdust.v2.ui.screens.TodoScreen
import kotlinx.coroutines.launch

private val TABS = listOf("今日", "课表", "电费", "待办")

@Composable
fun AppRoot(repo: Repository) {
    val scope = rememberCoroutineScope()
    var checked by remember { mutableStateOf(false) }
    var loggedIn by remember { mutableStateOf(false) }

    var calendar by remember { mutableStateOf<CalendarInfo?>(repo.store.loadCalendar()) }
    var blocks by remember { mutableStateOf<List<CourseBlock>>(repo.store.loadBlocks()) }
    var syncError by remember { mutableStateOf<String?>(null) }
    var tab by remember { mutableIntStateOf(1) }
    var showSettings by remember { mutableStateOf(false) }

    fun sync() {
        scope.launch {
            runCatching { repo.syncSchedule() }
                .onSuccess { (cal, list) -> calendar = cal; blocks = list; syncError = null }
                .onFailure { syncError = it.message ?: "网络失败" }
        }
    }

    LaunchedEffect(Unit) {
        if (repo.store.hasSession) {
            loggedIn = repo.isSessionAlive()
            if (loggedIn && blocks.isEmpty()) sync()
        }
        checked = true
    }

    Crossfade(
        targetState = when {
            !checked -> "loading"
            !loggedIn -> "login"
            showSettings -> "settings"
            else -> "main"
        },
        animationSpec = tween(220),
        label = "root",
    ) { state ->
        when (state) {
            "login" -> LoginScreen(
                repo = repo,
                onLoggedIn = {
                    loggedIn = true
                    sync()
                },
            )
            "settings" -> SettingsScreen(
                repo = repo,
                syncError = syncError,
                onSync = { sync() },
                onLogout = {
                    repo.store.logout()
                    loggedIn = false
                    showSettings = false
                },
                onBack = { showSettings = false },
            )
            "main" -> MainScaffold(
                repo = repo,
                tab = tab,
                onTab = { tab = it },
                onSettings = { showSettings = true },
                calendar = calendar,
                blocks = blocks,
                syncError = syncError,
            )
            else -> Box(Modifier.fillMaxSize().background(LocalDs.current.bg))
        }
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
                )
                2 -> PowerScreen(repo)
                else -> TodoScreen(repo)
            }
        }

        // 底栏按风格分派
        if (LocalUiStyle.current.value == UiStyle.LIQUID) {
            LiquidBottomBar(tab = tab, onTab = onTab, onSettings = onSettings)
        } else {
            SoftGlassBottomBar(tab = tab, onTab = onTab, onSettings = onSettings, ds = ds)
        }
    }
}

/** 风格一：softglass 柔光玻璃胶囊。 */
@Composable
private fun BoxScope.SoftGlassBottomBar(
    tab: Int, onTab: (Int) -> Unit, onSettings: () -> Unit, ds: Ds,
) {
    val backdrop = LocalBackdrop.current
    val pillShape = com.haooz.softglass.shape.ContinuousCapsule()
    Row(
        Modifier
            .align(Alignment.BottomCenter)
            .navigationBarsPadding()
            .padding(bottom = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            Modifier
                .drawBackdrop(backdrop = backdrop, shape = { pillShape }, effects = { blur(20f) })
                .padding(horizontal = 8.dp, vertical = 8.dp),
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
                ) {
                    Text(
                        label,
                        fontSize = 13.sp,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                        color = if (selected) ds.accent else ds.sub,
                    )
                }
            }
        }
        Box(
            Modifier
                .drawBackdrop(backdrop = backdrop, shape = { pillShape }, effects = { blur(20f) })
                .size(52.dp)
                .clickable { onSettings() },
            contentAlignment = Alignment.Center,
        ) {
            Text("设", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = ds.sub)
        }
    }
}

/** 风格二：Glasmio 原生液态玻璃导航栏。 */
@Composable
private fun BoxScope.LiquidBottomBar(
    tab: Int, onTab: (Int) -> Unit, onSettings: () -> Unit,
) {
    val compact = remember { androidx.compose.runtime.mutableStateOf(0f) }
    Box(
        Modifier
            .align(Alignment.BottomCenter)
            .navigationBarsPadding()
            .padding(horizontal = 16.dp)
            .padding(bottom = 14.dp),
    ) {
        io.github.neoruaa.glasmio.ui.GlasmioNavigationBar(
            items = listOf(
                io.github.neoruaa.glasmio.ui.GlasmioTabItem(0, "今日", io.github.neoruaa.glasmio.icons.SfSymbol.House),
                io.github.neoruaa.glasmio.ui.GlasmioTabItem(1, "课表", io.github.neoruaa.glasmio.icons.SfSymbol.Clock),
                io.github.neoruaa.glasmio.ui.GlasmioTabItem(2, "电费", io.github.neoruaa.glasmio.icons.SfSymbol.Sparkles),
                io.github.neoruaa.glasmio.ui.GlasmioTabItem(3, "待办", io.github.neoruaa.glasmio.icons.SfSymbol.Star),
            ),
            selectedKey = tab,
            onSelected = { if (it < 4) onTab(it) },
            onExpand = { },
            compactProgress = compact,
            compactSize = 56.dp,
            modifier = Modifier.fillMaxWidth(),
        )
    }
    Box(
        Modifier
            .align(Alignment.BottomEnd)
            .navigationBarsPadding()
            .padding(end = 12.dp)
            .padding(bottom = 92.dp)
            .size(44.dp)
            .background(LocalDs.current.card.copy(alpha = 0.9f), CircleShape)
            .clickable { onSettings() },
        contentAlignment = Alignment.Center,
    ) {
        Text("设", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = LocalDs.current.sub)
    }
}
