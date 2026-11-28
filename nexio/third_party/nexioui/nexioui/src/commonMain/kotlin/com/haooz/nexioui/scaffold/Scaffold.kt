// 派生自 compose-miuix-ui（Apache-2.0），已按 NexioUI 的 API 与包结构重写。

package com.haooz.nexioui.scaffold

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.exclude
import androidx.compose.foundation.layout.onConsumedWindowInsetsChanged
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.haooz.nexioui.overlaydialog.LocalNexioDialogStates
import com.haooz.nexioui.overlaydialog.LocalNexioRootDialogStates
import com.haooz.nexioui.overlaydialog.NexioDialogEntry
import com.haooz.nexioui.overlaydialog.NexioPopupHost
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.offset
import com.haooz.nexioui.surface.Surface
import com.haooz.nexioui.theme.NexioTheme

/**
 * 页面脚手架：顶栏 / 底栏 / 悬浮按钮 / 悬浮工具栏 / Snackbar / 弹层槽 + 内容内边距。
 *
 * 只在 [topBar] / [bottomBar] 缺席时把系统栏内边距给到 [content]；两者存在时由它们自己处理。
 * 已被父级 padding 或 `consumeWindowInsets` 消费的 insets 会从 [contentWindowInsets] 中排除。
 *
 * @param popupHost 弹层宿主槽（下拉菜单、对话框等），默认空。
 * @param content 内容；收到的 [PaddingValues] 应通过 `Modifier.padding` + `consumeWindowInsets`
 *   应用到内容根节点以正确避让上下栏。使用 `Modifier.verticalScroll` 时该修饰符应加在滚动子项上，
 *   而不是滚动容器本身。
 */
@Composable
fun Scaffold(
    modifier: Modifier = Modifier,
    topBar: @Composable () -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    floatingActionButton: @Composable () -> Unit = {},
    floatingActionButtonPosition: FabPosition = FabPosition.End,
    floatingToolbar: @Composable () -> Unit = {},
    floatingToolbarPosition: ToolbarPosition = ToolbarPosition.BottomCenter,
    snackbarHost: @Composable () -> Unit = {},
    popupHost: @Composable () -> Unit = { NexioPopupHost() },
    containerColor: Color = NexioTheme.colorScheme.surface,
    contentWindowInsets: WindowInsets = WindowInsets.systemBars.union(WindowInsets.displayCutout),
    content: @Composable (PaddingValues) -> Unit,
) {
    val safeInsets = remember(contentWindowInsets) { MutableWindowInsets(contentWindowInsets) }
    // 弹层宿主列表：对话框 / 底部弹窗注册到这里，由 popupHost 槽在底栏之后绘制。
    // 嵌套 Scaffold 沿用最外层那份，`renderInRootScaffold = true` 的弹窗才能盖住外层底栏。
    val dialogStates = remember { mutableStateListOf<NexioDialogEntry>() }
    val rootDialogStates = LocalNexioRootDialogStates.current ?: dialogStates
    CompositionLocalProvider(
        LocalNexioDialogStates provides dialogStates,
        LocalNexioRootDialogStates provides rootDialogStates,
    ) {
        Surface(
            modifier = modifier.onConsumedWindowInsetsChanged { consumedWindowInsets ->
                safeInsets.insets = contentWindowInsets.exclude(consumedWindowInsets)
            },
            color = containerColor,
        ) {
            ScaffoldLayout(
                topBar = topBar,
                bottomBar = bottomBar,
                content = content,
                snackbar = snackbarHost,
                floatingActionButton = floatingActionButton,
                floatingActionButtonPosition = floatingActionButtonPosition,
                floatingToolbar = floatingToolbar,
                floatingToolbarPosition = floatingToolbarPosition,
                popup = popupHost,
                contentWindowInsets = safeInsets,
            )
        }
    }
}

@Composable
private fun ScaffoldLayout(
    topBar: @Composable () -> Unit,
    snackbar: @Composable () -> Unit,
    bottomBar: @Composable () -> Unit,
    floatingActionButton: @Composable () -> Unit,
    floatingActionButtonPosition: FabPosition,
    floatingToolbar: @Composable () -> Unit,
    floatingToolbarPosition: ToolbarPosition,
    popup: @Composable () -> Unit,
    contentWindowInsets: WindowInsets,
    content: @Composable (PaddingValues) -> Unit,
) {
    // 内容内边距的承载值：在测量期、body subcompose 之前更新。
    // 单个 remember 的 PaddingValues 避免内边距变化时重组整个 body。
    val contentPadding = remember {
        object : PaddingValues {
            var paddingHolder by mutableStateOf(PaddingValues(0.dp))

            override fun calculateLeftPadding(layoutDirection: LayoutDirection): Dp =
                paddingHolder.calculateLeftPadding(layoutDirection)

            override fun calculateTopPadding(): Dp = paddingHolder.calculateTopPadding()

            override fun calculateRightPadding(layoutDirection: LayoutDirection): Dp =
                paddingHolder.calculateRightPadding(layoutDirection)

            override fun calculateBottomPadding(): Dp = paddingHolder.calculateBottomPadding()
        }
    }
    val popupContent: @Composable () -> Unit = remember(popup) { { Box { popup() } } }
    val topBarContent: @Composable () -> Unit = remember(topBar) { { Box { topBar() } } }
    val snackbarContent: @Composable () -> Unit = remember(snackbar) { { Box { snackbar() } } }
    val floatingActionButtonContent: @Composable () -> Unit =
        remember(floatingActionButton) { { Box { floatingActionButton() } } }
    val floatingToolbarContent: @Composable () -> Unit = remember(floatingToolbar) { { Box { floatingToolbar() } } }
    val bodyContent: @Composable () -> Unit = remember(content, contentPadding) { { Box { content(contentPadding) } } }
    val bottomBarContent: @Composable () -> Unit = remember(bottomBar) { { Box { bottomBar() } } }
    SubcomposeLayout { constraints ->
        val layoutWidth = constraints.maxWidth
        val layoutHeight = constraints.maxHeight

        val looseConstraints = constraints.copy(minWidth = 0, minHeight = 0)

        val topInset = contentWindowInsets.getTop(this@SubcomposeLayout)
        val leftInset = contentWindowInsets.getLeft(this@SubcomposeLayout, layoutDirection)
        val rightInset = contentWindowInsets.getRight(this@SubcomposeLayout, layoutDirection)
        val bottomInset = contentWindowInsets.getBottom(this@SubcomposeLayout)

        // 弹层先测后放，位于最高层。
        val popupPlaceables =
            subcompose(ScaffoldLayoutContent.Popup, popupContent)
                .first()
                .measure(looseConstraints)

        val topBarPlaceable =
            subcompose(ScaffoldLayoutContent.TopBar, topBarContent)
                .first()
                .measure(looseConstraints)

        val snackbarPlaceable =
            subcompose(ScaffoldLayoutContent.Snackbar, snackbarContent)
                .first()
                .measure(looseConstraints.offset(-leftInset - rightInset, -bottomInset))

        val fabPlaceable =
            subcompose(ScaffoldLayoutContent.Fab, floatingActionButtonContent)
                .first()
                .measure(looseConstraints.offset(-leftInset - rightInset, -bottomInset))
        val isFabEmpty = fabPlaceable.width == 0 && fabPlaceable.height == 0
        val fabPlacement = if (!isFabEmpty) {
            val fabWidth = fabPlaceable.width
            val fabHeight = fabPlaceable.height
            // FAB 距布局左边缘的距离，已按 RTL 调整
            val fabLeftOffset =
                when (floatingActionButtonPosition) {
                    FabPosition.Start -> {
                        if (layoutDirection == LayoutDirection.Ltr) {
                            FabSpacing.roundToPx() + leftInset
                        } else {
                            layoutWidth - FabSpacing.roundToPx() - fabWidth - rightInset
                        }
                    }

                    FabPosition.End,
                    FabPosition.EndOverlay,
                    -> {
                        if (layoutDirection == LayoutDirection.Ltr) {
                            layoutWidth - FabSpacing.roundToPx() - fabWidth - rightInset
                        } else {
                            FabSpacing.roundToPx() + leftInset
                        }
                    }

                    else -> (layoutWidth - fabWidth + leftInset - rightInset) / 2
                }

            FabPlacement(left = fabLeftOffset, width = fabWidth, height = fabHeight)
        } else {
            null
        }

        val bottomBarPlaceable =
            subcompose(ScaffoldLayoutContent.BottomBar, bottomBarContent)
                .first()
                .measure(looseConstraints)
        val isBottomBarEmpty = bottomBarPlaceable.width == 0 && bottomBarPlaceable.height == 0
        val fabOffsetFromBottom = fabPlacement?.let {
            if (isBottomBarEmpty || floatingActionButtonPosition == FabPosition.EndOverlay) {
                it.height + FabSpacing.roundToPx() + contentWindowInsets.getBottom(this@SubcomposeLayout)
            } else {
                bottomBarPlaceable.height + it.height + FabSpacing.roundToPx()
            }
        }

        val snackbarHeight = snackbarPlaceable.height
        val snackbarOffsetFromBottom =
            if (snackbarHeight != 0) {
                snackbarHeight +
                    (
                        fabOffsetFromBottom
                            ?: bottomBarPlaceable.height.takeIf { !isBottomBarEmpty }
                            ?: contentWindowInsets.getBottom(this@SubcomposeLayout)
                        )
            } else {
                0
            }

        val floatingToolbarPlaceable =
            subcompose(ScaffoldLayoutContent.FloatingToolbar, floatingToolbarContent)
                .first()
                .measure(looseConstraints.offset(-leftInset - rightInset, -bottomInset))

        val isFloatingToolbarEmpty = floatingToolbarPlaceable.width == 0 && floatingToolbarPlaceable.height == 0

        // 内容内边距必须在 body subcompose 之前更新。
        val insets = contentWindowInsets.asPaddingValues(this)
        contentPadding.paddingHolder =
            PaddingValues(
                top =
                if (topBarPlaceable.width == 0 && topBarPlaceable.height == 0) {
                    insets.calculateTopPadding()
                } else {
                    topBarPlaceable.height.toDp()
                },
                bottom =
                if (isBottomBarEmpty) {
                    insets.calculateBottomPadding()
                } else {
                    bottomBarPlaceable.height.toDp()
                },
                start = insets.calculateStartPadding(layoutDirection),
                end = insets.calculateEndPadding(layoutDirection),
            )

        val bodyContentPlaceable =
            subcompose(ScaffoldLayoutContent.MainContent, bodyContent)
                .first()
                .measure(looseConstraints)

        layout(layoutWidth, layoutHeight) {
            // 摆放顺序即绘制顺序，与各层的默认高度一致
            bodyContentPlaceable.place(0, 0)
            topBarPlaceable.place(0, 0)
            snackbarPlaceable.place(
                (
                    layoutWidth - snackbarPlaceable.width +
                        contentWindowInsets.getLeft(this@SubcomposeLayout, layoutDirection) -
                        contentWindowInsets.getRight(this@SubcomposeLayout, layoutDirection)
                    ) / 2,
                layoutHeight - snackbarOffsetFromBottom,
            )
            bottomBarPlaceable.place(0, layoutHeight - bottomBarPlaceable.height)
            if (!isFloatingToolbarEmpty) {
                val floatingToolbarWidth = floatingToolbarPlaceable.width
                val floatingToolbarHeight = floatingToolbarPlaceable.height

                val alignment = floatingToolbarPosition.toAlignment()

                val availableWidth = layoutWidth - leftInset - rightInset
                val availableHeight = layoutHeight - topBarPlaceable.height - topInset - bottomInset

                val position = alignment.align(
                    IntSize(floatingToolbarWidth, floatingToolbarHeight),
                    IntSize(availableWidth, availableHeight),
                    layoutDirection,
                )

                val x = leftInset + position.x
                val y = topBarPlaceable.height + topInset + position.y - FloatingToolbarSpacing.roundToPx()

                floatingToolbarPlaceable.place(x, y)
            }
            fabPlacement?.let { placement ->
                fabPlaceable.place(placement.left, layoutHeight - fabOffsetFromBottom!!)
            }
            popupPlaceables.place(0, 0)
        }
    }
}

private enum class ScaffoldLayoutContent {
    TopBar,
    BottomBar,
    Snackbar,
    FloatingToolbar,
    Fab,
    Popup,
    MainContent,
}

@Immutable
private class FabPlacement(val left: Int, val width: Int, val height: Int)

// FAB 相对底栏 / 脚手架底部的间距
private val FabSpacing = 12.dp

// 悬浮工具栏相对脚手架底部的间距
private val FloatingToolbarSpacing = 4.dp
