package com.haooz.nexioui.topappbar

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue

/** 折叠顶栏的滚动状态：记录可折叠高度、当前折叠量以及内容累计滚动量。 */
@Stable
class CollapsibleTopAppBarState(
    initialHeightOffsetLimit: Float = -Float.MAX_VALUE,
    initialHeightOffset: Float = 0f,
    initialContentOffset: Float = 0f,
) {
    var heightOffsetLimit = initialHeightOffsetLimit

    var heightOffset: Float
        get() = _heightOffset.floatValue
        set(newOffset) {
            _heightOffset.floatValue = newOffset.coerceIn(
                minimumValue = heightOffsetLimit,
                maximumValue = 0f,
            )
        }

    var contentOffset by mutableFloatStateOf(initialContentOffset)

    var showButtonShadow = false

    /** 0 = 完全展开，1 = 完全折叠。 */
    val collapsedFraction: Float
        get() = if (heightOffsetLimit != 0f) {
            heightOffset / heightOffsetLimit
        } else {
            0f
        }

    private var _heightOffset = mutableFloatStateOf(initialHeightOffset)

    companion object {
        val Saver: Saver<CollapsibleTopAppBarState, *> = listSaver(
            save = { listOf(it.heightOffsetLimit, it.heightOffset, it.contentOffset) },
            restore = {
                CollapsibleTopAppBarState(
                    initialHeightOffsetLimit = it[0],
                    initialHeightOffset = it[1],
                    initialContentOffset = it[2],
                )
            },
        )
    }
}

@Composable
fun rememberCollapsibleTopAppBarState(
    initialHeightOffsetLimit: Float = -Float.MAX_VALUE,
    initialHeightOffset: Float = 0f,
    initialContentOffset: Float = 0f,
): CollapsibleTopAppBarState = rememberSaveable(saver = CollapsibleTopAppBarState.Saver) {
    CollapsibleTopAppBarState(initialHeightOffsetLimit, initialHeightOffset, initialContentOffset)
}
