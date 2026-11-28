package com.haooz.nexioui.topappbar

import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import kotlin.math.roundToInt

/**
 * 让内容顶部让位给折叠顶栏，且**只在布局阶段读取** [SharedScrollBehavior.currentHeightPx]。
 *
 * `currentHeightPx` 折叠期间逐帧变化。若用 `Modifier.padding(top = ...)` 或 LazyColumn 的
 * `contentPadding` 读它，是在组合期订阅 —— 每折叠一帧整个页面重组一次。
 *
 * 用法：顶部内边距固定为「状态栏高度 + [CollapsibleTopAppBarDefaults.CollapsedHeight]」，
 * 展开态多出的部分由本 Modifier 在布局阶段补齐。顶栏未上报高度时按折叠态摆放，测后自纠。
 */
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
