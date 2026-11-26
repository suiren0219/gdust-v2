package com.haooz.nexioui.basiccomponent

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import com.haooz.nexioui.text.NexioText
import com.haooz.nexioui.theme.NexioTheme
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.delay

/** 设置行的标题 / 摘要配色。 */
@Immutable
data class BasicComponentColors(
    val color: Color,
    val disabledColor: Color,
) {
    /** 按启用状态取色。 */
    fun color(enabled: Boolean): Color = if (enabled) color else disabledColor
}

/** [BasicComponent] 默认参数。 */
object BasicComponentDefaults {

    val InsideMargin = PaddingValues(16.dp)

    @Composable
    fun titleColor(
        color: Color = NexioTheme.colorScheme.onBackground,
        disabledColor: Color = NexioTheme.colorScheme.disabledOnSurface,
    ): BasicComponentColors = remember(color, disabledColor) {
        BasicComponentColors(color, disabledColor)
    }

    @Composable
    fun summaryColor(
        color: Color = NexioTheme.colorScheme.onSurfaceVariantSummary,
        disabledColor: Color = NexioTheme.colorScheme.disabledOnSurface,
    ): BasicComponentColors = remember(color, disabledColor) {
        BasicComponentColors(color, disabledColor)
    }
}

/**
 * 通用设置行，支持标题、摘要、左右插槽与底部插槽。
 */
@Composable
fun BasicComponent(
    modifier: Modifier = Modifier,
    title: String? = null,
    titleColor: BasicComponentColors = BasicComponentDefaults.titleColor(),
    summary: String? = null,
    summaryColor: BasicComponentColors = BasicComponentDefaults.summaryColor(),
    startAction: @Composable (() -> Unit)? = null,
    endActions: (@Composable RowScope.() -> Unit)? = null,
    bottomAction: (@Composable () -> Unit)? = null,
    insideMargin: PaddingValues = BasicComponentDefaults.InsideMargin,
    onClick: (() -> Unit)? = null,
    onClickLabel: String? = null,
    role: Role? = null,
    holdDownState: Boolean = false,
    enabled: Boolean = true,
    interactionSource: MutableInteractionSource? = null,
) {
    // 搜索定位：标题命中时连按两次，提示搜索项在页内的位置
    val flashToken = PreferenceFlash.target?.takeIf { it.title == title }?.token
    var flashHold by remember { mutableStateOf(false) }
    LaunchedEffect(flashToken) {
        if (flashToken == null) {
            flashHold = false
            return@LaunchedEffect
        }
        delay(180.milliseconds)
        flashHold = true
        delay(420.milliseconds)
        flashHold = false
        delay(180.milliseconds)
        flashHold = true
        delay(420.milliseconds)
        flashHold = false
        PreferenceFlash.clear(flashToken)
    }

    BasicComponent(
        modifier = modifier,
        startAction = startAction,
        endActions = endActions,
        bottomAction = bottomAction,
        insideMargin = insideMargin,
        onClick = onClick,
        onClickLabel = onClickLabel,
        role = role,
        holdDownState = holdDownState || flashHold,
        enabled = enabled,
        interactionSource = interactionSource,
    ) {
        if (title != null) {
            NexioText(
                text = title,
                color = titleColor.color(enabled),
                style = NexioTheme.textStyles.headline1.copy(fontWeight = FontWeight.Medium),
            )
        }
        if (summary != null) {
            NexioText(
                text = summary,
                color = summaryColor.color(enabled),
                style = NexioTheme.textStyles.body2,
            )
        }
    }
}

/**
 * 通用设置行的内容版本，布局与 [BasicComponent] 的标题版本一致。
 */
@Composable
fun BasicComponent(
    modifier: Modifier = Modifier,
    startAction: @Composable (() -> Unit)? = null,
    endActions: (@Composable RowScope.() -> Unit)? = null,
    bottomAction: (@Composable () -> Unit)? = null,
    insideMargin: PaddingValues = BasicComponentDefaults.InsideMargin,
    onClick: (() -> Unit)? = null,
    onClickLabel: String? = null,
    role: Role? = null,
    holdDownState: Boolean = false,
    enabled: Boolean = true,
    interactionSource: MutableInteractionSource? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    @Suppress("NAME_SHADOWING")
    val interactionSource = interactionSource ?: remember { MutableInteractionSource() }
    val currentOnClick by rememberUpdatedState(onClick)

    val clickableModifier = remember(enabled, onClick != null, interactionSource, role, onClickLabel) {
        if (enabled && onClick != null) {
            Modifier.clickable(
                interactionSource = interactionSource,
                onClickLabel = onClickLabel,
                role = role,
                onClick = { currentOnClick?.invoke() },
            )
        } else {
            Modifier
        }
    }
    Column(
        modifier = modifier
            .heightIn(min = 56.dp)
            .fillMaxWidth()
            .then(clickableModifier)
            .padding(insideMargin),
        verticalArrangement = Arrangement.Center,
    ) {
        if (startAction == null && endActions == null) {
            Column(
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.Start,
                content = content,
            )
        } else {
            Layout(
                content = {
                    startAction?.let {
                        Column(
                            modifier = Modifier.layoutId("start"),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.Start,
                        ) { it() }
                    }
                    Column(
                        modifier = Modifier.layoutId("center"),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.Start,
                        content = content,
                    )
                    endActions?.let {
                        Column(
                            modifier = Modifier.layoutId("end"),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.End,
                        ) {
                            Row { it() }
                        }
                    }
                },
            ) { measurables, constraints ->
                val spacerPx = 8.dp.roundToPx()
                val maxWidth = constraints.maxWidth
                val maxHeight = constraints.maxHeight
                val looseConstraints = constraints.copy(minWidth = 0, minHeight = 0)

                val startMeasurable = measurables.firstOrNull { it.layoutId == "start" }
                val centerMeasurable = measurables.first { it.layoutId == "center" }
                val endMeasurable = measurables.firstOrNull { it.layoutId == "end" }

                val startPlaceable = startMeasurable?.measure(looseConstraints)
                val startWidth = startPlaceable?.width ?: 0
                val startSpacerWidth = if (startWidth > 0) spacerPx else 0
                val widthAfterStart = (maxWidth - startWidth - startSpacerWidth).coerceAtLeast(0)

                val endIntrinsicWidth = endMeasurable?.maxIntrinsicWidth(maxHeight) ?: 0
                val endHardCap = (widthAfterStart - spacerPx).coerceAtLeast(0) * 6 / 10
                val endPlaceable = endMeasurable?.measure(
                    looseConstraints.copy(maxWidth = endIntrinsicWidth.coerceAtMost(endHardCap)),
                )
                val endActualWidth = endPlaceable?.width ?: 0
                val endSpacerWidth = if (endActualWidth > 0) spacerPx else 0
                val widthForCenter = (widthAfterStart - endActualWidth - endSpacerWidth).coerceAtLeast(0)
                val centerPlaceable = centerMeasurable.measure(
                    looseConstraints.copy(maxWidth = widthForCenter),
                )

                val rowHeight = maxOf(startPlaceable?.height ?: 0, centerPlaceable.height, endPlaceable?.height ?: 0)
                val layoutHeight = rowHeight.coerceIn(
                    constraints.minHeight,
                    if (maxHeight == Constraints.Infinity) rowHeight else maxHeight,
                )

                layout(maxWidth, layoutHeight) {
                    startPlaceable?.placeRelative(0, (rowHeight - startPlaceable.height).coerceAtLeast(0) / 2)
                    centerPlaceable.placeRelative(startWidth + startSpacerWidth, (rowHeight - centerPlaceable.height) / 2)
                    endPlaceable?.placeRelative(maxWidth - endPlaceable.width, (rowHeight - endPlaceable.height).coerceAtLeast(0) / 2)
                }
            }
        }

        if (bottomAction != null) {
            Spacer(Modifier.height(8.dp))
            bottomAction()
        }
    }
}
