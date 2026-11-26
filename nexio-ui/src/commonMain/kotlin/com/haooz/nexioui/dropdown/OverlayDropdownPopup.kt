package com.haooz.nexioui.dropdown

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.haooz.nexioui.overlaydialog.OverlayDialog
import com.haooz.nexioui.text.NexioText
import com.haooz.nexioui.theme.NexioTheme
import com.haooz.softglass.backdrop.GlassBackdrop

/**
 * 单个 [DropdownEntry] 的弹层。
 *
 * @param entry 下拉条目组
 * @param show 是否显示
 * @param onDismiss 关闭回调
 * @param onDismissFinished 退场动画完成回调
 * @param maxHeight 最大高度
 * @param dropdownColors 下拉配色
 * @param renderInRootScaffold 是否在根容器中渲染；弹层始终使用窗口层以保证定位不被裁剪
 * @param collapseOnSelection 选中后是否收起
 * @param liquidGlassBackdrop 玻璃采样层
 * @param onFractionProgress 展开进度回调
 * @param collapseSize 收起态尺寸
 * @param collapseExtra 收起态尺寸补偿
 * @param collapseContent 收起态内容
 * @param dragSelectState 跟手滑选状态
 */
@Composable
fun OverlayDropdownPopup(
    entry: DropdownEntry,
    show: Boolean,
    onDismiss: () -> Unit,
    onDismissFinished: () -> Unit,
    maxHeight: Dp?,
    dropdownColors: DropdownColors,
    renderInRootScaffold: Boolean,
    collapseOnSelection: Boolean = true,
    liquidGlassBackdrop: GlassBackdrop? = null,
    onFractionProgress: ((Float) -> Unit)? = null,
    collapseSize: IntSize? = null,
    collapseExtra: DpSize = DpSize.Zero,
    collapseContent: (@Composable () -> Unit)? = null,
    dragSelectState: DropdownPanelDragSelectState? = null,
) {
    val entries = remember(entry) { listOf(entry) }
    OverlayDropdownPopup(
        entries = entries,
        show = show,
        onDismiss = onDismiss,
        onDismissFinished = onDismissFinished,
        maxHeight = maxHeight,
        dropdownColors = dropdownColors,
        renderInRootScaffold = renderInRootScaffold,
        collapseOnSelection = collapseOnSelection,
        liquidGlassBackdrop = liquidGlassBackdrop,
        onFractionProgress = onFractionProgress,
        collapseSize = collapseSize,
        collapseExtra = collapseExtra,
        collapseContent = collapseContent,
        dragSelectState = dragSelectState,
    )
}

/**
 * 多个 [DropdownEntry] 组的弹层。
 */
@Composable
fun OverlayDropdownPopup(
    entries: List<DropdownEntry>,
    show: Boolean,
    onDismiss: () -> Unit,
    onDismissFinished: () -> Unit,
    maxHeight: Dp?,
    dropdownColors: DropdownColors,
    renderInRootScaffold: Boolean,
    collapseOnSelection: Boolean = entries.size <= 1,
    liquidGlassBackdrop: GlassBackdrop? = null,
    onFractionProgress: ((Float) -> Unit)? = null,
    collapseSize: IntSize? = null,
    collapseExtra: DpSize = DpSize.Zero,
    collapseContent: (@Composable () -> Unit)? = null,
    dragSelectState: DropdownPanelDragSelectState? = null,
) {
    val hapticFeedback = LocalHapticFeedback.current
    val currentEntries by rememberUpdatedState(entries)
    val currentCollapseOnSelection by rememberUpdatedState(collapseOnSelection)
    val currentOnDismiss by rememberUpdatedState(onDismiss)
    val currentHapticFeedback by rememberUpdatedState(hapticFeedback)
    val onItemClicked: (Int, Int) -> Unit = remember {
        { entryIdx, itemIdx ->
            currentHapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
            currentEntries.getOrNull(entryIdx)?.let { entry ->
                entry.items.getOrNull(itemIdx)?.onClick?.invoke()
            }
            if (currentCollapseOnSelection) {
                currentOnDismiss()
            }
        }
    }

    DropdownPopupLayout(
        show = show,
        maxHeight = maxHeight,
        liquidGlassBackdrop = liquidGlassBackdrop,
        onDismissRequest = onDismiss,
        onDismissFinished = onDismissFinished,
        onFractionProgress = onFractionProgress,
        collapseSize = collapseSize,
        collapseExtra = collapseExtra,
        collapseContent = collapseContent,
        dragSelectState = dragSelectState,
        renderInRootScaffold = renderInRootScaffold,
    ) {
        DropdownEntriesColumn(
            onFitsOnScreen = { fits -> dragSelectState?.fitsOnScreen = fits },
        ) {
            DropdownEntriesPopupContent(
                entries = entries,
                dropdownColors = dropdownColors,
                onItemClick = onItemClicked,
            )
        }
    }
}

/**
 * [DropdownEntry] 的弹窗对话。
 */
@Composable
fun OverlayDropdownDialog(
    entry: DropdownEntry,
    title: String,
    dialogButtonString: String,
    show: Boolean,
    onDismiss: () -> Unit,
    onDismissFinished: () -> Unit,
    dropdownColors: DropdownColors,
    popupModifier: Modifier = Modifier,
    renderInRootScaffold: Boolean = true,
    collapseOnSelection: Boolean = true,
    liquidGlassBackdrop: GlassBackdrop? = null,
) {
    val entries = remember(entry) { listOf(entry) }
    OverlayDropdownDialog(
        entries = entries,
        title = title,
        dialogButtonString = dialogButtonString,
        show = show,
        onDismiss = onDismiss,
        onDismissFinished = onDismissFinished,
        dropdownColors = dropdownColors,
        popupModifier = popupModifier,
        renderInRootScaffold = renderInRootScaffold,
        collapseOnSelection = collapseOnSelection,
        liquidGlassBackdrop = liquidGlassBackdrop,
    )
}

/**
 * 多个 [DropdownEntry] 组的弹窗对话。
 */
@Composable
fun OverlayDropdownDialog(
    entries: List<DropdownEntry>,
    title: String,
    dialogButtonString: String,
    show: Boolean,
    onDismiss: () -> Unit,
    onDismissFinished: () -> Unit,
    dropdownColors: DropdownColors,
    popupModifier: Modifier = Modifier,
    renderInRootScaffold: Boolean = true,
    collapseOnSelection: Boolean = entries.size <= 1,
    liquidGlassBackdrop: GlassBackdrop? = null,
) {
    val hapticFeedback = LocalHapticFeedback.current
    val currentEntries by rememberUpdatedState(entries)
    val currentCollapseOnSelection by rememberUpdatedState(collapseOnSelection)
    val currentOnDismiss by rememberUpdatedState(onDismiss)
    val currentHapticFeedback by rememberUpdatedState(hapticFeedback)
    val onItemClicked: (Int, Int) -> Unit = remember {
        { entryIdx, itemIdx ->
            currentHapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
            currentEntries.getOrNull(entryIdx)?.let { entry ->
                entry.items.getOrNull(itemIdx)?.onClick?.invoke()
            }
            if (currentCollapseOnSelection) {
                currentOnDismiss()
            }
        }
    }

    OverlayDialog(
        show = show,
        modifier = popupModifier,
        title = title,
        onDismissRequest = onDismiss,
        onDismissFinished = onDismissFinished,
        insideMargin = DpSize(0.dp, 24.dp),
        renderInRootScaffold = renderInRootScaffold,
        liquidGlassBackdrop = liquidGlassBackdrop,
    ) {
        Column {
            LazyColumn(
                modifier = Modifier.heightIn(max = 440.dp),
            ) {
                dropdownEntriesDialogItems(
                    entries = entries,
                    dropdownColors = dropdownColors,
                    onItemClick = onItemClicked,
                )
            }
            TextButton(
                modifier = Modifier
                    .padding(start = 24.dp, top = 12.dp, end = 24.dp)
                    .fillMaxWidth(),
                onClick = onDismiss,
            ) {
                NexioText(
                    text = dialogButtonString,
                    color = NexioTheme.colorScheme.primary,
                )
            }
        }
    }
}
