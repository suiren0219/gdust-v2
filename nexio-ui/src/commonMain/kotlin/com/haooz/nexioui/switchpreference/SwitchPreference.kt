// 派生自 compose-miuix-ui（Apache-2.0），已按 NexioUI 的 API 与包结构重写。

package com.haooz.nexioui.switchpreference

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.NonRestartableComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.haooz.nexioui.basiccomponent.BasicComponent
import com.haooz.nexioui.basiccomponent.BasicComponentColors
import com.haooz.nexioui.basiccomponent.BasicComponentDefaults
import com.haooz.nexioui.switch.Switch
import com.haooz.nexioui.switch.SwitchColors
import com.haooz.nexioui.switch.SwitchDefaults

/**
 * 带标题与摘要的开关偏好项。
 *
 * @param checked 开关状态。
 * @param onCheckedChange 状态变化回调。
 * @param switchColors 开关颜色。
 * @param holdDownState 是否处于按压态。
 */
@Composable
@NonRestartableComposable
fun SwitchPreference(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    title: String,
    modifier: Modifier = Modifier,
    titleColor: BasicComponentColors = BasicComponentDefaults.titleColor(),
    summary: String? = null,
    summaryColor: BasicComponentColors = BasicComponentDefaults.summaryColor(),
    startAction: @Composable (() -> Unit)? = null,
    endActions: @Composable RowScope.() -> Unit = {},
    bottomAction: (@Composable () -> Unit)? = null,
    switchColors: SwitchColors = SwitchDefaults.colors(),
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
        startAction = startAction,
        endActions = {
            Row(
                modifier = Modifier
                    .padding(end = 8.dp)
                    .align(Alignment.CenterVertically)
                    .weight(1f, fill = false),
            ) {
                endActions()
            }
            Switch(
                checked = checked,
                onCheckedChange = currentOnCheckedChange,
                enabled = enabled,
                colors = switchColors,
            )
        },
        bottomAction = bottomAction,
        onClick = {
            currentOnCheckedChange.takeIf { enabled }?.invoke(!checked)
        },
        role = Role.Switch,
        holdDownState = holdDownState,
        enabled = enabled,
    )
}
