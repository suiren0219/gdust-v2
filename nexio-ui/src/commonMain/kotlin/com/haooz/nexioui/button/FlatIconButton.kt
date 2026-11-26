// 派生自 compose-miuix-ui（Apache-2.0），已按 NexioUI 的 API 与包结构重写。

package com.haooz.nexioui.button

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.haooz.softglass.shape.continuousSurface

/**
 * 无玻璃材质的图标按钮：只吃底色、圆角与点击，用于工具栏 / 对话框等紧凑场景。
 *
 * 与液态玻璃的 `IconButton` 区分：本组件不做 backdrop 采样，也不带跟手形变。
 *
 * @param backgroundColor 底色；`Color.Unspecified` 表示透明。
 * @param cornerRadius 圆角。
 * @param minHeight 最小高度。
 * @param minWidth 最小宽度。
 */
@Composable
fun FlatIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    backgroundColor: Color = Color.Unspecified,
    cornerRadius: Dp = FlatIconButtonDefaults.CornerRadius,
    minHeight: Dp = FlatIconButtonDefaults.MinHeight,
    minWidth: Dp = FlatIconButtonDefaults.MinWidth,
    content: @Composable () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .defaultMinSize(minWidth = minWidth, minHeight = minHeight)
            .continuousSurface(color = backgroundColor, cornerRadius = cornerRadius)
            .then(
                if (enabled) {
                    Modifier.clickable(
                        role = Role.Button,
                        interactionSource = interactionSource,
                        onClick = onClick,
                    )
                } else {
                    Modifier
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

object FlatIconButtonDefaults {
    val MinWidth = 40.dp
    val MinHeight = 40.dp
    val CornerRadius = 40.dp
}
