package com.menglimi.gdust.v2.ui.nexio

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.haooz.nexioui.bottombar.BottomBar
import com.haooz.nexioui.bottombar.BottomBarAction
import com.haooz.nexioui.bottombar.BottomBarDefaults
import com.haooz.nexioui.bottombar.BottomBarItem
import com.haooz.nexioui.icon.NexioIcons
import com.haooz.nexioui.icon.extended.Album
import com.haooz.nexioui.icon.extended.BankCards
import com.haooz.nexioui.icon.extended.Months
import com.haooz.nexioui.icon.extended.Settings
import com.haooz.nexioui.icon.extended.Tasks
import com.haooz.softglass.backdrop.GlassBackdrop
import com.menglimi.gdust.v2.ui.AiSparkleIcon
import com.menglimi.gdust.v2.ui.currentPageBackdrop

/**
 * 纯 NexioUI SoftGlass 悬浮底栏：
 * - 左侧为 5 页签可拖拽液态玻璃胶囊（今日 / 课表 / 电费 / 待办 / 设置）
 * - 右侧为独立圆形液态玻璃动作按钮（AI 学习助手）
 */
@Composable
fun NexioBottomBar(
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
    onSettings: () -> Unit,
    settingsSelected: Boolean = false,
    modifier: Modifier = Modifier,
    backdrop: GlassBackdrop = currentPageBackdrop(),
    onAi: (() -> Unit)? = null,
) {
    val items = remember {
        listOf(
            BottomBarItem(icon = NexioIcons.Album, label = "今日"),
            BottomBarItem(icon = NexioIcons.Months, label = "课表"),
            BottomBarItem(icon = NexioIcons.BankCards, label = "电费"),
            BottomBarItem(icon = NexioIcons.Tasks, label = "待办"),
            BottomBarItem(icon = NexioIcons.Settings, label = "设置"),
        )
    }
    val resolvedIndex = if (settingsSelected) 4 else selectedTab.coerceIn(0, 3)
    val aiAction = remember(onAi) {
        onAi?.let { click ->
            BottomBarAction(
                icon = AiSparkleIcon,
                contentDescription = "打开 AI 学习助手",
                onClick = click,
            )
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .padding(bottom = bottomFloatingPadding()),
        contentAlignment = Alignment.Center,
    ) {
        val barContainerWidth = maxWidth.coerceAtMost(520.dp)
        val height = BottomBarDefaults.Height
        val actionGap = if (aiAction != null) 8.dp else 0.dp
        val trailingWidth = if (aiAction != null) height else 0.dp
        val availablePill = (barContainerWidth - trailingWidth - actionGap).coerceAtLeast(220.dp)
        val tabFraction = ((availablePill / barContainerWidth) / items.size).coerceIn(0.145f, 0.19f)

        BottomBar(
            items = items,
            selectedIndex = resolvedIndex,
            onSelect = { index ->
                if (index == 4) onSettings() else onTabSelected(index)
            },
            backdrop = backdrop,
            modifier = Modifier.widthIn(max = 520.dp),
            height = height,
            action = aiAction,
            actionGap = actionGap,
            tabWidthFraction = tabFraction,
            iconSize = 21.dp,
            labelFontSize = 10.sp,
        )
    }
}

@Composable
fun bottomFloatingPadding() =
    maxOf(WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 8.dp, 16.dp)
