// 派生自 compose-miuix-ui（Apache-2.0），已按 NexioUI 的 API 与包结构重写。

package com.haooz.nexioui.surface

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Indication
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.NonRestartableComposable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.haooz.nexioui.theme.NexioTheme

/** 通用表面容器：底色 + 裁切 + 可选边框 / 阴影。 */
@Composable
@NonRestartableComposable
fun Surface(
    modifier: Modifier = Modifier,
    shape: Shape = SurfaceDefaults.Shape,
    color: Color = NexioTheme.colorScheme.surface,
    contentColor: Color = NexioTheme.colorScheme.onSurface,
    border: BorderStroke? = null,
    shadowElevation: Dp = SurfaceDefaults.ShadowElevation,
    content: @Composable () -> Unit,
) {
    val shadowElevationPx = with(LocalDensity.current) { shadowElevation.toPx() }
    CompositionLocalProvider(LocalContentColor provides contentColor) {
        Box(
            modifier = modifier
                .semantics(mergeDescendants = false) { isTraversalGroup = true }
                .surface(
                    shape = shape,
                    backgroundColor = color,
                    border = border,
                    shadowElevation = shadowElevationPx,
                ),
            propagateMinConstraints = true,
        ) {
            content()
        }
    }
}

/** 可点击的 [Surface]。 */
@Composable
fun Surface(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = SurfaceDefaults.Shape,
    color: Color = NexioTheme.colorScheme.surface,
    contentColor: Color = NexioTheme.colorScheme.onSurface,
    border: BorderStroke? = null,
    shadowElevation: Dp = SurfaceDefaults.ShadowElevation,
    interactionSource: MutableInteractionSource? = null,
    indication: Indication? = LocalIndication.current,
    content: @Composable () -> Unit,
) {
    @Suppress("NAME_SHADOWING")
    val interactionSource = interactionSource ?: remember { MutableInteractionSource() }
    val shadowElevationPx = with(LocalDensity.current) { shadowElevation.toPx() }
    CompositionLocalProvider(LocalContentColor provides contentColor) {
        Box(
            modifier = modifier
                .surface(
                    shape = shape,
                    backgroundColor = color,
                    border = border,
                    shadowElevation = shadowElevationPx,
                )
                .clickable(
                    interactionSource = interactionSource,
                    indication = indication,
                    enabled = enabled,
                    onClick = onClick,
                ),
            propagateMinConstraints = true,
        ) {
            content()
        }
    }
}

@Stable
private fun Modifier.surface(
    shape: Shape,
    backgroundColor: Color,
    border: BorderStroke?,
    shadowElevation: Float,
): Modifier = this.then(
    if (shadowElevation > 0f) {
        Modifier.graphicsLayer(
            shadowElevation = shadowElevation,
            shape = shape,
            clip = false,
        )
    } else {
        Modifier
    },
)
    .then(if (border != null) Modifier.border(border, shape) else Modifier)
    .clip(shape)
    .background(color = backgroundColor)

object SurfaceDefaults {
    val Shape: Shape = RectangleShape
    val ShadowElevation: Dp = 0.dp
}
