package com.haooz.nexioui.dropdown

import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.IntrinsicMeasureScope
import androidx.compose.ui.layout.IntrinsicMeasurable
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasurePolicy
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import kotlin.math.min

/** 弹窗内容最大宽度。 */
private val PopupMaxWidth = 288.dp

/** 参与宽度测量的最大条目数。 */
private const val MaxItemsForWidth = 8

/** 参与高度测量的最大条目数。 */
private const val MaxItemsForHeight = 8

/**
 * 弹窗内容列：宽度对齐到最宽子项，钳制在 200dp ~ 288dp，内容超高时纵向滚动。
 *
 * @param onFitsOnScreen 内容是否一屏装得下，拖选据此决定是否接管纵向手势。
 */
@Composable
internal fun DropdownEntriesColumn(
    onFitsOnScreen: (Boolean) -> Unit = {},
    content: @Composable () -> Unit,
) {
    val scrollState = rememberScrollState()

    val measurePolicy = remember {
        object : MeasurePolicy {
            override fun MeasureScope.measure(
                measurables: List<Measurable>,
                constraints: Constraints,
            ): MeasureResult {
                val minPx = DropdownDefaults.MinWidth.roundToPx()
                val maxPx = PopupMaxWidth.roundToPx()
                val widthCount = min(MaxItemsForWidth, measurables.size)
                var maxIntrinsic = 0
                for (index in 0 until widthCount) {
                    val width = measurables[index].maxIntrinsicWidth(constraints.maxHeight)
                    if (width > maxIntrinsic) maxIntrinsic = width
                }
                val parentMin = constraints.minWidth
                val parentMax = constraints.maxWidth
                val upper = maxOf(maxPx, parentMin).coerceAtMost(parentMax)
                val lower = maxOf(minPx, parentMin).coerceAtMost(upper)
                val listWidth = maxIntrinsic.coerceIn(lower, upper)

                val childConstraints = constraints.copy(
                    minWidth = listWidth,
                    maxWidth = listWidth,
                    minHeight = 0,
                )

                val placeables = ArrayList<Placeable>(measurables.size)
                var listHeight = 0
                for (index in measurables.indices) {
                    val placeable = measurables[index].measure(childConstraints)
                    placeables.add(placeable)
                    listHeight += placeable.height
                }

                return layout(listWidth, listHeight) {
                    var currentY = 0
                    for (index in placeables.indices) {
                        val placeable = placeables[index]
                        placeable.placeRelative(0, currentY)
                        currentY += placeable.height
                    }
                }
            }

            override fun IntrinsicMeasureScope.minIntrinsicHeight(
                measurables: List<IntrinsicMeasurable>,
                width: Int,
            ): Int {
                val minPx = DropdownDefaults.MinWidth.roundToPx()
                val maxPx = PopupMaxWidth.roundToPx()
                val widthCount = min(MaxItemsForWidth, measurables.size)
                var maxIntrinsic = 0
                for (index in 0 until widthCount) {
                    val intrinsic = measurables[index].maxIntrinsicWidth(Int.MAX_VALUE)
                    if (intrinsic > maxIntrinsic) maxIntrinsic = intrinsic
                }
                val listWidth = maxIntrinsic.coerceIn(minPx, maxPx)

                val heightCount = min(MaxItemsForHeight, measurables.size)
                var height = 0
                for (index in 0 until heightCount) {
                    height += measurables[index].minIntrinsicHeight(listWidth)
                }
                return height
            }
        }
    }

    Layout(
        content = content,
        modifier = Modifier
            .focusGroup()
            .height(IntrinsicSize.Min)
            .verticalScroll(state = scrollState),
        measurePolicy = measurePolicy,
    )

    // maxValue 在「内容被 maxHeight 压缩」时也会变化，所以用 snapshotFlow
    LaunchedEffect(scrollState) {
        snapshotFlow { scrollState.maxValue <= 1 }
            .collect { onFitsOnScreen(it) }
    }
}

/** 渲染弹窗中的 [DropdownEntry] 组。 */
@Composable
internal fun DropdownEntriesPopupContent(
    entries: List<DropdownEntry>,
    dropdownColors: DropdownColors,
    onItemClick: (entryIdx: Int, itemIdx: Int) -> Unit,
) {
    val lastEntryIdx = entries.lastIndex
    entries.forEachIndexed { entryIdx, entry ->
        val lastItemIdx = entry.items.lastIndex
        val isFirstEntry = entryIdx == 0
        val isLastEntry = entryIdx == lastEntryIdx
        entry.items.forEachIndexed { itemIdx, option ->
            key(entryIdx, itemIdx) {
                DropdownImpl(
                    item = option,
                    optionSize = entry.items.size,
                    isSelected = option.selected,
                    index = itemIdx,
                    dropdownColors = dropdownColors,
                    enabled = entry.enabled && option.enabled,
                    isFirst = isFirstEntry && itemIdx == 0,
                    isLast = isLastEntry && itemIdx == lastItemIdx,
                    onSelectedIndexChange = { index -> onItemClick(entryIdx, index) },
                )
            }
        }
        if (entryIdx != lastEntryIdx) {
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                thickness = 1.5.dp,
            )
        }
    }
}

/** 向 [LazyListScope] 添加 [DropdownEntry] 组，用于对话面板。 */
internal fun LazyListScope.dropdownEntriesDialogItems(
    entries: List<DropdownEntry>,
    dropdownColors: DropdownColors,
    onItemClick: (entryIdx: Int, itemIdx: Int) -> Unit,
) {
    val lastEntryIdx = entries.lastIndex
    entries.forEachIndexed { entryIdx, entry ->
        items(entry.items.size, key = { itemIdx -> "$entryIdx-$itemIdx" }) { itemIdx ->
            val item = entry.items[itemIdx]
            DropdownImpl(
                item = item,
                optionSize = entry.items.size,
                isSelected = item.selected,
                index = itemIdx,
                dropdownColors = dropdownColors,
                enabled = entry.enabled && item.enabled,
                dialogMode = true,
                onSelectedIndexChange = { index -> onItemClick(entryIdx, index) },
            )
        }
        if (entryIdx != lastEntryIdx) {
            item(key = "divider-$entryIdx") {
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                    thickness = 1.5.dp,
                )
            }
        }
    }
}
