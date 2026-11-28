package com.menglimi.gdust.v2.ui.nexio

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import com.menglimi.gdust.v2.ui.AiSparkleIcon
import com.haooz.nexioui.text.NexioText as Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.menglimi.gdust.v2.ui.LocalDs
import com.menglimi.gdust.v2.ui.LocalWallpaperState
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Album
import top.yukonga.miuix.kmp.icon.extended.BankCards
import top.yukonga.miuix.kmp.icon.extended.Months
import top.yukonga.miuix.kmp.icon.extended.Settings
import top.yukonga.miuix.kmp.icon.extended.Tasks

/** The AI assistant is a separate floating entry beside the compact primary navigation. */
@Composable
fun NexioBottomBar(
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
    onSettings: () -> Unit,
    settingsSelected: Boolean = false,
    modifier: Modifier = Modifier,
    onAi: (() -> Unit)? = null,
) {
    val ds = LocalDs.current
    val haptic = LocalHapticFeedback.current
    val wallpaper = LocalWallpaperState.current?.bitmap != null
    val labels = listOf("今日", "课表", "电费", "待办", "设置")
    val icons = listOf(MiuixIcons.Album, MiuixIcons.Months, MiuixIcons.BankCards, MiuixIcons.Tasks, MiuixIcons.Settings)
    BoxWithConstraints(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Row(
            Modifier.width(maxWidth.coerceAtMost(560.dp)).padding(horizontal = 16.dp)
                .padding(bottom = bottomFloatingPadding()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                Modifier.weight(1f).clip(RoundedCornerShape(28.dp))
                    .background(if (wallpaper) ds.card.copy(alpha = 0.82f) else ds.card)
                    .selectableGroup().padding(5.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                labels.forEachIndexed { index, label ->
                    val selected = if (index == 4) settingsSelected else !settingsSelected && selectedTab == index
                    Column(
                        Modifier.weight(1f).heightIn(min = 54.dp)
                            .clip(RoundedCornerShape(22.dp))
                            .background(if (selected) ds.accentSoft else Color.Transparent)
                            .selectable(
                                selected, role = Role.Tab,
                                interactionSource = remember { MutableInteractionSource() }, indication = null,
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.VirtualKey)
                                    if (index == 4) onSettings() else onTabSelected(index)
                                },
                            )
                            .padding(vertical = 5.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(3.dp, Alignment.CenterVertically),
                    ) {
                        Icon(icons[index], null, Modifier.size(20.dp), tint = if (selected) ds.accent else ds.sub)
                        Text(label, fontSize = 11.sp,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (selected) ds.text else ds.sub, maxLines = 1)
                    }
                }
            }
            if (onAi != null) {
                Column(
                    Modifier.size(56.dp).clip(RoundedCornerShape(100.dp))
                        .background(if (wallpaper) ds.card.copy(alpha = 0.82f) else ds.card)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() }, indication = null,
                            role = Role.Button, onClick = onAi,
                        ),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically),
                ) {
                    Icon(AiSparkleIcon, "打开 AI 学习助手", Modifier.size(22.dp), tint = ds.accent)
                    Text("AI", fontSize = 10.sp, color = ds.sub)
                }
            }
        }
    }
}

@Composable
fun bottomFloatingPadding() =
    maxOf(WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 6.dp, 16.dp)
