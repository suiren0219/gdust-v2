package com.haooz.nexioui.topappbar

import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import kotlin.math.roundToInt

/** 折叠顶栏的内容让位：只在 layout 期读 [SharedScrollBehavior.currentHeightPx]，避免逐帧重组；固定折叠态内边距，展开态多出的由本 Modifier 补齐。 */
fun Modifier.collapsibleTopInset(scrollBehavior: SharedScrollBehavior?): Modifier =
    layout { measurable, constraints ->
        val collapsedPx = CollapsibleTopAppBarDefaults.CollapsedHeight.roundToPx()
        val reportedPx = scrollBehavior?.currentHeightPx ?: 0f
        val extraPx = if (reportedPx > 0f) {
            (reportedPx.roundToInt() - collapsedPx).coerceAtLeast(0)
        } else {
            0
        }
        // 与 Modifier.padding(top = extraPx) 等价：上下界同时收窄，子项被要求填满时仍能正确填充
        // （只收窄 maxHeight 会让 weight(1f) 之类的填充约束失配），自身总高度保持不变。
        val placeable = measurable.measure(
            constraints.copy(
                minHeight = (constraints.minHeight - extraPx).coerceAtLeast(0),
                maxHeight = (constraints.maxHeight - extraPx).coerceAtLeast(0),
            ),
        )
        layout(placeable.width, placeable.height + extraPx) {
            placeable.place(0, extraPx)
        }
    }
