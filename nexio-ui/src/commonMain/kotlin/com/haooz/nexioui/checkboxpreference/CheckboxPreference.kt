// 派生自 compose-miuix-ui（Apache-2.0），已按 NexioUI 的 API 与包结构重写。

package com.haooz.nexioui.checkboxpreference

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.NonRestartableComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.dp
import com.haooz.nexioui.basiccomponent.BasicComponent
import com.haooz.nexioui.basiccomponent.BasicComponentColors
import com.haooz.nexioui.basiccomponent.BasicComponentDefaults
import com.haooz.nexioui.checkbox.Checkbox
import com.haooz.nexioui.checkbox.CheckboxColors
import com.haooz.nexioui.checkbox.CheckboxDefaults
import com.haooz.nexioui.internal.StartActionSlot

/** 复选框在偏好项中的位置。 */
enum class CheckboxLocation {
    Start,
    End,
}

/**
 * 带标题与摘要的复选框偏好项。
 *
 * @param checkboxLocation 复选框放在起始侧还是结束侧。
 * @param checkboxColors 复选框颜色。
 * @param holdDownState 是否处于按压态。
 */
@Composable
@NonRestartableComposable
fun CheckboxPreference(
    title: String,
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    titleColor: BasicComponentColors = BasicComponentDefaults.titleColor(),
    summary: String? = null,
    summaryColor: BasicComponentColors = BasicComponentDefaults.summaryColor(),
    checkboxColors: CheckboxColors = CheckboxDefaults.colors(),
    startAction: @Composable (() -> Unit)? = null,
    endActions: @Composable (RowScope.() -> Unit)? = null,
    checkboxLocation: CheckboxLocation = CheckboxLocation.Start,
    bottomAction: (@Composable () -> Unit)? = null,
    insideMargin: PaddingValues = BasicComponentDefaults.InsideMargin,
    holdDownState: Boolean = false,
    enabled: Boolean = true,
) {
    val currentOnCheckedChange by rememberUpdatedState(onCheckedChange)

    BasicComponent(
        modifier = modifier,
        insideMargin = insideMargin,
        title = title,
        titleColor = titleColor,
        summary = summary,
        summaryColor = summaryColor,
        startAction = if (checkboxLocation == CheckboxLocation.Start || startAction != null) {
            {
                Row {
                    if (checkboxLocation == CheckboxLocation.Start) {
                        CheckboxPreferenceStartAction(
                            checked = checked,
                            onCheckedChange = currentOnCheckedChange,
                            enabled = enabled,
                            checkboxColors = checkboxColors,
                        )
                    }

                    startAction?.let {
                        StartActionSlot(
                            modifier = Modifier
                                .align(Alignment.CenterVertically)
                                .weight(1f, fill = false),
                            endSpacing = 5.dp,
                            content = it,
                        )
                    }
                }
            }
        } else {
            null
        },
        endActions = {
            endActions?.let {
                Row(
                    modifier = Modifier
                        .padding(end = 8.dp)
                        .align(Alignment.CenterVertically)
                        .weight(1f, fill = false),
                ) {
                    it()
                }
            }

            if (checkboxLocation == CheckboxLocation.End) {
                CheckboxPreferenceEndAction(
                    checked = checked,
                    onCheckedChange = currentOnCheckedChange,
                    enabled = enabled,
                    checkboxColors = checkboxColors,
                )
            }
        },
        bottomAction = bottomAction,
        onClick = {
            currentOnCheckedChange.takeIf { enabled }?.invoke(!checked)
        },
        role = Role.Checkbox,
        holdDownState = holdDownState,
        enabled = enabled,
    )
}

@Composable
private fun CheckboxPreferenceStartAction(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    enabled: Boolean,
    checkboxColors: CheckboxColors,
) {
    val currentOnCheckedChange by rememberUpdatedState(onCheckedChange)
    val currentChecked by rememberUpdatedState(checked)
    val onClick = remember(onCheckedChange != null) {
        if (onCheckedChange != null) {
            { currentOnCheckedChange?.invoke(!currentChecked) ?: Unit }
        } else {
            null
        }
    }
    Checkbox(
        modifier = Modifier.padding(end = 5.dp),
        state = ToggleableState(checked),
        onClick = onClick,
        enabled = enabled,
        colors = checkboxColors,
    )
}

@Composable
private fun CheckboxPreferenceEndAction(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    enabled: Boolean,
    checkboxColors: CheckboxColors,
) {
    val currentOnCheckedChange by rememberUpdatedState(onCheckedChange)
    val currentChecked by rememberUpdatedState(checked)
    val onClick = remember(onCheckedChange != null) {
        if (onCheckedChange != null) {
            { currentOnCheckedChange?.invoke(!currentChecked) ?: Unit }
        } else {
            null
        }
    }
    Checkbox(
        state = ToggleableState(checked),
        onClick = onClick,
        enabled = enabled,
        colors = checkboxColors,
    )
}
