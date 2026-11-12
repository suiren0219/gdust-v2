package com.menglimi.gdust.v2.ui.nexio

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.haooz.softglass.backdrop.GlassBackdrop
import com.menglimi.gdust.v2.ui.nexio.components.LiquidBottomTab
import com.menglimi.gdust.v2.ui.nexio.components.LiquidBottomTabs
import com.menglimi.gdust.v2.ui.nexio.components.LiquidBottomTrailing
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Album
import top.yukonga.miuix.kmp.icon.extended.BankCards
import top.yukonga.miuix.kmp.icon.extended.Months
import top.yukonga.miuix.kmp.icon.extended.Settings
import top.yukonga.miuix.kmp.icon.extended.Tasks
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * Adapted from NexioSchedule 28a99a3b23458a0d39741b9841c9755f5374fba2
 * ScheduleBottomBar.kt: four gdust tabs and an independent settings button.
 * The complete bar is min(available width - 32dp, 480dp); settings never selects a tab.
 */
@Composable
fun NexioBottomBar(
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
    onSettings: () -> Unit,
    backdrop: GlassBackdrop,
    settingsSelected: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val iconTint = MiuixTheme.colorScheme.onSurfaceContainer.copy(alpha = 0.8f)
    val bottomPadding = bottomFloatingPadding()
    val labels = listOf("今日", "课表", "电费", "待办")
    val icons = listOf(MiuixIcons.Album, MiuixIcons.Months, MiuixIcons.BankCards, MiuixIcons.Tasks)

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = bottomPadding),
        contentAlignment = Alignment.Center,
    ) {
        val barWidth = (maxWidth - 32.dp).coerceIn(0.dp, 480.dp)
        LiquidBottomTabs(
            selectedTabIndex = { selectedTab.coerceIn(labels.indices) },
            onTabSelected = onTabSelected,
            backdrop = backdrop,
            tabsCount = labels.size,
            trailing = LiquidBottomTrailing(
                icon = MiuixIcons.Settings,
                contentDescription = "设置",
                onClick = onSettings,
                gap = 10.dp,
                selected = settingsSelected,
            ),
            modifier = Modifier.width(barWidth).height(56.dp),
        ) {
            labels.forEachIndexed { index, label ->
                LiquidBottomTab(index = index) {
                    Image(
                        modifier = Modifier.size(24.dp),
                        imageVector = icons[index],
                        contentDescription = null,
                        colorFilter = ColorFilter.tint(iconTint),
                    )
                    Text(label, fontSize = 11.sp, color = iconTint, maxLines = 1)
                }
            }
        }
    }
}

/** Nexio's navigation inset + 2dp, with a 22dp minimum even when gestures hide the bar. */
@Composable
fun bottomFloatingPadding() =
    maxOf(WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 2.dp, 22.dp)
