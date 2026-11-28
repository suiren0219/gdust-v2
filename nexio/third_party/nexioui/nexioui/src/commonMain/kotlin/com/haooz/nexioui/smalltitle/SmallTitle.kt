package com.haooz.nexioui.smalltitle

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.haooz.nexioui.text.NexioText
import com.haooz.nexioui.theme.NexioTheme

/** [SmallTitle] 的默认尺寸。 */
object SmallTitleDefaults {

    /** 水平留白 12dp：与调用方列表的 16dp / 20dp contentPadding 叠加后接近 MIUI 小标题起始位置。 */
    val InsideMargin = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
}

/**
 * 分组小标题。
 *
 * @param text 标题文字。
 * @param textColor 文字颜色。
 * @param insideMargin 外边距。
 * @param hasWallpaper 背景为壁纸时给文字加一层轻阴影，保证可读性。
 */
@Composable
fun SmallTitle(
    text: String,
    modifier: Modifier = Modifier,
    textColor: Color = NexioTheme.colorScheme.onSurfaceVariantSummary,
    insideMargin: PaddingValues = SmallTitleDefaults.InsideMargin,
    hasWallpaper: Boolean = false,
) {
    val baseStyle = NexioTheme.textStyles.subtitle.copy(fontWeight = FontWeight.Medium)
    val style = if (hasWallpaper) {
        val shadowColor = if (NexioTheme.isDark) Color.Black else Color.White
        baseStyle.copy(
            shadow = Shadow(
                color = shadowColor.copy(alpha = 0.92f),
                blurRadius = 12f,
            ),
        )
    } else {
        baseStyle
    }

    NexioText(
        modifier = modifier.padding(insideMargin),
        text = text,
        style = style,
        color = textColor,
    )
}
