package com.haooz.nexioui.overlaydialog

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.zIndex

/**
 * 弹层承载。
 *
 * 优先把内容挂到 Scaffold 的弹层宿主（`popupHost = { NexioPopupHost() }`）里 —— 宿主在底栏之后
 * 绘制，弹窗才能盖住底栏；[renderInRootScaffold] 为 true 时挂到最外层 Scaffold，false 时挂到
 * 当前 Scaffold。没有宿主（未使用 [com.haooz.nexioui.scaffold.Scaffold]）时退回就地绘制。
 */
@Composable
internal fun DialogLayout(
    visible: Boolean,
    renderInRootScaffold: Boolean,
    content: @Composable () -> Unit,
) {
    val states = if (renderInRootScaffold) {
        LocalNexioRootDialogStates.current ?: LocalNexioDialogStates.current
    } else {
        LocalNexioDialogStates.current
    }

    if (states == null) {
        if (!visible) return
        Box(
            modifier = if (renderInRootScaffold) {
                Modifier.fillMaxSize().zIndex(1f)
            } else {
                Modifier.fillMaxWidth()
            },
        ) {
            content()
        }
        return
    }

    val entry = remember { NexioDialogEntry() }
    SideEffect { entry.content = content }
    if (visible) {
        DisposableEffect(states, entry) {
            states.add(entry)
            onDispose { states.remove(entry) }
        }
    }
}
