package com.menglimi.gdust.v2.ui.screens

import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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

/**
 * 分组设置卡：一整张玻璃卡里装多行选项，行间细分隔，不再一张卡一个选项。
 * 两套玻璃风格共用，颜色全部走 Ds（含莫奈取色）。
 */
@Composable
fun SettingGroup(
    modifier: Modifier = Modifier,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    val ds = LocalDs.current
    val shape = RoundedCornerShape(20.dp)
    Column(
        modifier
            .fillMaxWidth()
            .background(ds.card, shape)
            .then(
                Modifier.border(
                    width = 1.dp,
                    color = ds.sub.copy(alpha = 0.16f),
                    shape = shape,
                )
            )
            .padding(horizontal = 4.dp, vertical = 4.dp),
    ) {
        content()
    }
    Spacer(Modifier.height(12.dp))
}

/** 分组里的一行：标题+副标题 / 右侧值或箭头 / 可选开关，点击整行。 */
@Composable
fun SettingOption(
    title: String,
    subtitle: String? = null,
    detail: String? = null,
    danger: Boolean = false,
    switch: Boolean? = null,
    onSwitch: ((Boolean) -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    val ds = LocalDs.current
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = if (danger) ds.danger else ds.text)
            if (subtitle != null) {
                Text(subtitle, fontSize = 11.sp, lineHeight = 15.sp, color = ds.sub, modifier = Modifier.padding(top = 3.dp))
            }
        }
        if (detail != null) Text(detail, fontSize = 13.sp, color = ds.sub, modifier = Modifier.padding(start = 8.dp))
        if (switch != null && onSwitch != null) {
            androidx.compose.material3.Switch(
                checked = switch,
                onCheckedChange = onSwitch,
                modifier = Modifier.padding(start = 8.dp),
                colors = androidx.compose.material3.SwitchDefaults.colors(checkedTrackColor = ds.accent),
            )
        } else if (onClick != null) {
            Text("›", fontSize = 18.sp, color = ds.sub, modifier = Modifier.padding(start = 8.dp))
        }
    }
}

/** 行间细分隔线（分组卡内用）。 */
@Composable
fun SettingDivider() {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(start = 14.dp)
            .height(1.dp)
            .background(LocalDs.current.sub.copy(alpha = 0.12f)),
    )
}

/** 展开式编辑区：只有当前选中的选项才展开输入框/按钮，避免整页平铺。 */
@Composable
fun SettingEditor(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        content = content,
    )
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
            .background(ds.sub.copy(alpha = 0.10f), RoundedCornerShape(12.dp))
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

/**
 * 学校官网不可达弹窗：网络层失败（连不上/超时/DNS）时给用户可读提示。
 * LIQUID 走 GlasmioAlertDialog；SOFT 自绘玻璃卡弹窗。
 */
@Composable
fun NetworkDownDialog(visible: Boolean, onDismiss: () -> Unit) {
    if (!visible) return
    if (LocalUiStyle.current.value == UiStyle.LIQUID) {
        io.github.neoruaa.glasmio.ui.GlasmioAlertDialog(
            visible = true,
            title = "学校官网无法访问",
            message = "连不上学校服务器（教务/水电），可能是校园网波动或学校维护中。\n检查手机网络后稍后再试。",
            onDismissRequest = onDismiss,
            buttons = listOf(
                io.github.neoruaa.glasmio.ui.GlasmioAlertButtonSpec(label = "知道了", onClick = { onDismiss() }),
            ),
        )
        return
    }
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        val ds = LocalDs.current
        Box(
            Modifier
                .fillMaxWidth()
                .background(ds.card, RoundedCornerShape(20.dp))
                .padding(20.dp),
        ) {
            Column {
                Text("学校官网无法访问", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = ds.text)
                Text(
                    "连不上学校服务器（教务/水电），可能是校园网波动或学校维护中。\n检查手机网络后稍后再试。",
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    color = ds.sub,
                    modifier = Modifier.padding(top = 10.dp),
                )
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp)
                        .background(ds.accent, RoundedCornerShape(12.dp))
                        .clickable { onDismiss() }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("知道了", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
            }
        }
    }
}
