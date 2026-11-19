package com.menglimi.gdust.v2.ui.nexio

import androidx.compose.foundation.background
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.menglimi.gdust.v2.ui.LocalDs
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Album
import top.yukonga.miuix.kmp.icon.extended.BankCards
import top.yukonga.miuix.kmp.icon.extended.Months
import top.yukonga.miuix.kmp.icon.extended.Settings
import top.yukonga.miuix.kmp.icon.extended.Tasks

/** 实底浮动导航；文字与图标一起表达目的地，设置保持独立路由。 */
@Composable
fun NexioBottomBar(
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
    onSettings: () -> Unit,
    settingsSelected: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val ds = LocalDs.current
    val labels = listOf("今日", "课表", "电费", "待办", "设置")
    val icons = listOf(MiuixIcons.Album, MiuixIcons.Months, MiuixIcons.BankCards, MiuixIcons.Tasks, MiuixIcons.Settings)
    BoxWithConstraints(
        modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 0.dp)
            .padding(bottom = bottomFloatingPadding()),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            Modifier.width(maxWidth.coerceAtMost(440.dp))
                .clip(RoundedCornerShape(32.dp)).background(ds.card)
                .selectableGroup().padding(6.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            labels.forEachIndexed { index, label ->
                val selected = if (index == 4) settingsSelected else !settingsSelected && selectedTab == index
                Column(
                    Modifier.weight(1f).heightIn(min = 56.dp)
                        .clip(RoundedCornerShape(26.dp))
                        .background(if (selected) ds.accentSoft else Color.Transparent)
                        .selectable(selected, role = Role.Tab, onClick = {
                            if (index == 4) onSettings() else onTabSelected(index)
                        })
                        .padding(vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
                ) {
                    Icon(icons[index], null, Modifier.size(22.dp), tint = if (selected) ds.accent else ds.sub)
                    Text(label, fontSize = 12.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (selected) ds.text else ds.sub, maxLines = 1)
                }
            }
        }
    }
}

@Composable
fun bottomFloatingPadding() =
    maxOf(WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 6.dp, 16.dp)
