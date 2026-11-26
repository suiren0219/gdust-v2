package com.haooz.nexioui.overlaydialog

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier

/**
 * 弹层宿主里的一条弹窗。
 *
 * 内容用可变状态持有：`DialogLayout` 每次重组只刷新内容，注册 / 注销交给宿主列表负责，
 * 这样退场动画播完、调用方移除 `DialogLayout` 时宿主能干净地摘掉这一条。
 */
internal class NexioDialogEntry {
    var content: @Composable () -> Unit by mutableStateOf({})
}

/** 当前 [com.haooz.nexioui.scaffold.Scaffold] 自己的弹层列表（`renderInRootScaffold = false` 用）。 */
internal val LocalNexioDialogStates = staticCompositionLocalOf<SnapshotStateList<NexioDialogEntry>?> { null }

/** 最外层 Scaffold 的弹层列表；嵌套 Scaffold 会原样继承（`renderInRootScaffold = true` 用）。 */
internal val LocalNexioRootDialogStates = staticCompositionLocalOf<SnapshotStateList<NexioDialogEntry>?> { null }

/**
 * 弹层宿主：由 [com.haooz.nexioui.scaffold.Scaffold] 的 `popupHost` 槽调用。
 *
 * 必须放在 Scaffold 内、且由 Scaffold 在底栏之后绘制 —— 底部弹窗 / 对话框要盖住底栏，
 * 靠的就是这一层，而不是页面内容里的 `zIndex`。
 */
@Composable
fun NexioPopupHost() {
    val states = LocalNexioDialogStates.current ?: return
    if (states.isEmpty()) return
    Box(modifier = Modifier.fillMaxSize()) {
        states.forEach { entry ->
            key(entry) { entry.content() }
        }
    }
}
