package com.haooz.nexioui.card

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.haooz.softglass.shape.continuousSurface

/**
 * 基础卡片容器，不处理输入事件。
 *
 * 圆角使用连续曲率曲面；内容按 [insideMargin] 内缩。
 *
 * @param modifier 外层修饰符。
 * @param cornerRadius 圆角半径。
 * @param insideMargin 内容内边距。
 * @param colors 底色与内容色。
 * @param content 卡片内容。
 */
@Composable
fun Card(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = CardDefaults.CornerRadius,
    insideMargin: PaddingValues = CardDefaults.InsideMargin,
    colors: CardColors = CardDefaults.defaultColors(),
    content: @Composable ColumnScope.() -> Unit,
) {
    CardSurface(
        modifier = modifier,
        cornerRadius = cornerRadius,
        colors = colors,
    ) {
        Column(
            modifier = Modifier.padding(insideMargin),
            content = content,
        )
    }
}

/**
 * 卡片按压反馈类型。
 *
 * 当前库支持无反馈与按压下沉两种模式。
 */
enum class PressFeedbackType {
    None,
    Sink,
}

/**
 * 可点击 / 长按的卡片。
 *
 * @param onClick 点击回调。
 * @param onLongPress 长按回调。
 * @param showIndication 是否使用系统涟漪；默认关闭，交给卡片自身反馈。
 * @param holdDownState 为 true 时表示卡片暂时被弹窗压住，不参与按压态。
 */
@Composable
fun Card(
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = CardDefaults.CornerRadius,
    insideMargin: PaddingValues = CardDefaults.InsideMargin,
    colors: CardColors = CardDefaults.defaultColors(),
    pressFeedbackType: PressFeedbackType = PressFeedbackType.None,
    showIndication: Boolean = false,
    holdDownState: Boolean = false,
    onLongPress: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val currentOnClick by rememberUpdatedState(onClick)
    val currentOnLongPress by rememberUpdatedState(onLongPress)
    val indication = if (showIndication) LocalIndication.current else null
    val clickable = onClick != null || onLongPress != null
    val pressed by interactionSource.collectIsPressedAsState()
    val sinkTarget = if (pressFeedbackType == PressFeedbackType.Sink && (pressed || holdDownState)) 0.94f else 1f
    val sinkScale by animateFloatAsState(
        targetValue = sinkTarget,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = 600f),
        label = "cardSink",
    )
    val sinkModifier = if (pressFeedbackType == PressFeedbackType.Sink) {
        Modifier.graphicsLayer {
            scaleX = sinkScale
            scaleY = sinkScale
        }
    } else {
        Modifier
    }

    CardSurface(
        modifier = modifier.then(sinkModifier),
        // 点击反馈必须挂在裁剪之后：按在 combinedClickable 外层时，指示器画的是整个矩形，
        // 会越过圆角。放在 continuousSurface（clip）内层就与卡片轮廓一致。
        pressModifier = if (clickable) {
            Modifier.combinedClickable(
                interactionSource = interactionSource,
                indication = indication,
                onClick = { currentOnClick?.invoke() },
                onLongClick = onLongPress?.let { { currentOnLongPress?.invoke() } },
            )
        } else {
            Modifier
        },
        cornerRadius = cornerRadius,
        colors = colors,
    ) {
        Column(
            modifier = Modifier.padding(insideMargin),
            content = content,
        )
    }
}

@Composable
private fun CardSurface(
    modifier: Modifier = Modifier,
    pressModifier: Modifier = Modifier,
    cornerRadius: Dp,
    colors: CardColors,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalContentColor provides colors.contentColor) {
        Box(
            modifier = modifier
                .semantics(mergeDescendants = false) { isTraversalGroup = true }
                .continuousSurface(color = colors.color, cornerRadius = cornerRadius)
                .then(pressModifier),
            propagateMinConstraints = true,
        ) {
            content()
        }
    }
}

/** 卡片配色。 */
data class CardColors(
    val color: Color,
    val contentColor: Color,
)
