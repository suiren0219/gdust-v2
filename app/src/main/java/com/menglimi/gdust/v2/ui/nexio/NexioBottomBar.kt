package com.menglimi.gdust.v2.ui.nexio

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.Backdrop
import com.menglimi.gdust.v2.ui.nexio.components.LiquidBottomTab
import com.menglimi.gdust.v2.ui.nexio.components.LiquidBottomTabs
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Album
import top.yukonga.miuix.kmp.icon.extended.BankCards
import top.yukonga.miuix.kmp.icon.extended.Months
import top.yukonga.miuix.kmp.icon.extended.Settings
import top.yukonga.miuix.kmp.icon.extended.Tasks
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * Nexio 式浮动液态玻璃底栏：四个业务 tab + 一个设置钮。
 * 版式沿用上游 ScheduleBottomBar（胶囊 56dp 高、图标 24dp、标签 11sp、底部留白按系统导航条算）。
 */
@Composable
fun NexioBottomBar(
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
    onSettings: () -> Unit,
    backdrop: Backdrop,
    settingsSelected: Boolean = false,
) {
    val iconTint = MiuixTheme.colorScheme.onSurfaceContainer.copy(alpha = 0.8f)
    val bottomPadding = bottomFloatingPadding()
    val labels = listOf("今日", "课表", "电费", "待办")

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = bottomPadding),
        contentAlignment = Alignment.Center,
    ) {
        LiquidBottomTabs(
            selectedTabIndex = { selectedTab },
            onTabSelected = onTabSelected,
            backdrop = backdrop,
            tabsCount = labels.size + 1,
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .height(56.dp),
        ) {
            LiquidBottomTab(index = 0, onClick = { onTabSelected(0) }) {
                Image(
                    modifier = Modifier.size(24.dp),
                    imageVector = MiuixIcons.Album,
                    contentDescription = null,
                    colorFilter = ColorFilter.tint(iconTint),
                )
                Text(labels[0], fontSize = 11.sp, color = iconTint)
            }
            LiquidBottomTab(index = 1, onClick = { onTabSelected(1) }) {
                Image(
                    modifier = Modifier.size(24.dp),
                    imageVector = MiuixIcons.Months,
                    contentDescription = null,
                    colorFilter = ColorFilter.tint(iconTint),
                )
                Text(labels[1], fontSize = 11.sp, color = iconTint)
            }
            LiquidBottomTab(index = 2, onClick = { onTabSelected(2) }) {
                Image(
                    modifier = Modifier.size(24.dp),
                    imageVector = MiuixIcons.BankCards,
                    contentDescription = null,
                    colorFilter = ColorFilter.tint(iconTint),
                )
                Text(labels[2], fontSize = 11.sp, color = iconTint)
            }
            LiquidBottomTab(index = 3, onClick = { onTabSelected(3) }) {
                Image(
                    modifier = Modifier.size(24.dp),
                    imageVector = MiuixIcons.Tasks,
                    contentDescription = null,
                    colorFilter = ColorFilter.tint(iconTint),
                )
                Text(labels[3], fontSize = 11.sp, color = iconTint)
            }
            LiquidBottomTab(index = 4, onClick = onSettings) {
                Image(
                    modifier = Modifier.size(24.dp),
                    imageVector = MiuixIcons.Settings,
                    contentDescription = "设置",
                    colorFilter = ColorFilter.tint(iconTint),
                )
                Text("设置", fontSize = 11.sp, color = iconTint)
            }
        }
    }
}

/** 底栏与页面底部的间距：与上游同一条规则（导航条 inset + 2dp，硬下限 22dp）。 */
@Composable
fun bottomFloatingPadding() =
    maxOf(WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 2.dp, 22.dp)
