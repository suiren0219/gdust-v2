package com.menglimi.gdust.v2.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.menglimi.gdust.v2.ui.Ds
import com.menglimi.gdust.v2.ui.LocalDs
import com.menglimi.gdust.v2.ui.LocalUiStyle
import com.menglimi.gdust.v2.ui.UiStyle

/** 统一页头：35sp 大标题 + 副标题，所有内容页共用。 */
@Composable
fun PageTitle(title: String, subtitle: String? = null, onBack: (() -> Unit)? = null) {
    val ds = LocalDs.current
    Column(Modifier.fillMaxWidth().padding(bottom = 20.dp)) {
        if (onBack != null) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 8.dp)) {
                Box(
                    Modifier
                        .background(ds.card.copy(alpha = 0.9f), CircleShape)
                        .clickable { onBack() }
                        .size(34.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("‹", fontSize = 22.sp, color = ds.accent)
                }
            }
        }
        Text(title, fontSize = 35.sp, fontWeight = FontWeight.Bold, color = ds.text)
        if (subtitle != null) {
            Text(subtitle, fontSize = 14.sp, color = ds.sub, modifier = Modifier.padding(top = 6.dp))
        }
    }
}

/** 分组小标题（Nexio 风格，卡片之间的层次）。 */
@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        fontSize = 13.sp,
        fontWeight = FontWeight.Bold,
        color = LocalDs.current.sub,
        modifier = modifier.padding(start = 6.dp, bottom = 8.dp, top = 8.dp),
    )
}

/**
 * 设置行：两套风格完整适配。
 * LIQUID 用 Glasmio 原生分组行；SOFT 走自绘玻璃卡行。
 */
@Composable
fun SettingRow(
    title: String,
    subtitle: String? = null,
    detail: String? = null,
    danger: Boolean = false,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val ds = LocalDs.current
    if (LocalUiStyle.current.value == UiStyle.LIQUID) {
        io.github.neoruaa.glasmio.ui.GlasmioGroupedList {
            io.github.neoruaa.glasmio.ui.GlasmioListRow(
                title = title,
                subtitle = subtitle,
                detail = detail,
                onClick = onClick,
                showTopSeparator = false,
                trailing = trailing?.let { { it() } },
            )
        }
        return
    }
    Box(
        Modifier
            .fillMaxWidth()
            .background(ds.card.copy(alpha = 0.55f), RoundedCornerShape(16.dp))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = if (danger) ds.danger else ds.text)
                if (subtitle != null) {
                    Text(subtitle, fontSize = 11.sp, color = ds.sub, modifier = Modifier.padding(top = 3.dp))
                }
            }
            if (detail != null) Text(detail, fontSize = 13.sp, color = ds.sub)
            if (trailing != null) trailing()
            if (onClick != null && trailing == null) {
                Text("›", fontSize = 18.sp, color = ds.sub, modifier = Modifier.padding(start = 8.dp))
            }
        }
    }
}

/** 两个/多个选项的分段选择器（两套风格都用同一视觉，颜色随 Ds）。 */
@Composable
fun <T> SegmentedChoice(
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    val ds = LocalDs.current
    Row(
        modifier
            .fillMaxWidth()
            .background(ds.bg.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        options.forEach { (value, label) ->
            val picked = value == selected
            Box(
                Modifier
                    .weight(1f)
                    .background(
                        if (picked) ds.accent else Color.Transparent,
                        RoundedCornerShape(9.dp),
                    )
                    .clickable { onSelect(value) }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    fontSize = 13.sp,
                    fontWeight = if (picked) FontWeight.Bold else FontWeight.Normal,
                    color = if (picked) Color.White else ds.sub,
                )
            }
        }
    }
}
