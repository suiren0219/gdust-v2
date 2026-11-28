package com.menglimi.gdust.v2.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.haooz.nexioui.card.Card
import com.haooz.nexioui.card.CardDefaults
import top.yukonga.miuix.kmp.theme.LocalContentColor

object AppUiTokens {
    val PagePadding = 16.dp
    val TitleSize = 34.sp
    val TitleWeight = FontWeight.Medium
    val CardCorner = 20.dp
    val ItemCorner = 16.dp
    val CardGap = 12.dp
    val CardPadding = 16.dp
    val PreferenceRowMinHeight = 56.dp
    val PreferenceHorizontalPadding = 16.dp
    val PreferenceVerticalPadding = 14.dp
}

@Composable
fun secondaryTextColor(): Color = LocalDs.current.sub

@Composable
fun Modifier.appSurface(cornerRadius: Dp = AppUiTokens.CardCorner): Modifier {
    val shape = remember(cornerRadius) { RoundedCornerShape(cornerRadius) }
    return clip(shape).background(LocalDs.current.card)
}

/** NexioUI 连续圆角卡，统一语义表面与按压反馈。 */
@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = AppUiTokens.CardCorner,
    insideMargin: PaddingValues = PaddingValues(0.dp),
    onClick: (() -> Unit)? = null,
    color: Color = LocalDs.current.card,
    content: @Composable ColumnScope.() -> Unit,
) {
    val ds = LocalDs.current
    val wallpaper = LocalWallpaperState.current
    val translucent = wallpaper?.bitmap != null && color == ds.card
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val base = if (translucent) color.copy(alpha = if (ds.isDark) 0.78f else 0.82f) else color.compositeOver(ds.card)
    val surface = if (pressed) ds.accent.copy(alpha = 0.07f).compositeOver(base) else base
    val shape = remember(cornerRadius) { RoundedCornerShape(cornerRadius) }
    val wallpaperBackground = if (translucent) Modifier.clip(shape).background(surface) else Modifier
    CompositionLocalProvider(
        LocalContentColor provides ds.text,
        androidx.compose.material3.LocalContentColor provides ds.text,
    ) {
        Card(
            modifier = modifier.then(wallpaperBackground).then(
                if (onClick != null) Modifier.clickable(
                    interactionSource = interaction,
                    indication = null,
                    role = Role.Button,
                    onClick = onClick,
                ) else Modifier,
            ),
            cornerRadius = cornerRadius,
            insideMargin = insideMargin,
            colors = CardDefaults.defaultColors(
                color = if (translucent) Color.Transparent else surface,
                contentColor = ds.text,
            ),
            content = content,
        )
    }
}
