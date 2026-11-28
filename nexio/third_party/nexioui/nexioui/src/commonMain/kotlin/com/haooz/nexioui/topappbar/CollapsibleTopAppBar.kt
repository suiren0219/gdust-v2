package com.haooz.nexioui.topappbar

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateTo
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.fastFirst
import androidx.compose.ui.util.lerp
import androidx.compose.ui.zIndex
import com.haooz.nexioui.anim.folmeSpring
import com.haooz.nexioui.overscroll.LocalOverScrollState
import com.haooz.nexioui.theme.NexioTheme
import com.haooz.nexioui.topbarmaterial.TopBarMaterialState
import com.haooz.nexioui.topbarmaterial.TopBarMaterialThreshold
import com.haooz.nexioui.topbarmaterial.isTopBarMaterialVisible
import com.haooz.nexioui.topbarmaterial.rememberTopBarMaterialState
import com.haooz.nexioui.window.isTabletWidth
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

/**
 * 可折叠的大标题顶栏。平板宽度下默认只显示小标题。
 *
 * @param materialState 页面级共享的材质状态。传同一个实例给本组件与渐变遮罩，材质才会同进同出；
 *   传 null 时内部自算一份。
 * @param startAction 左侧自定义内容，接收 backdropAlpha / shadowAlpha 用于玻璃按钮动画。
 * @param endAction 右侧自定义内容，接收 backdropAlpha / shadowAlpha 用于玻璃按钮动画。
 */
@Composable
fun CollapsibleTopAppBar(
    title: String,
    modifier: Modifier = Modifier,
    largeTitle: String = title,
    showLargeTitle: Boolean? = null,
    showSmallTitle: Boolean? = null,
    showShadow: Boolean? = null,
    materialState: TopBarMaterialState? = null,
    /** 标题色；默认随主题。WebView 页可按页面顶色传黑白。 */
    titleColor: Color = NexioTheme.colorScheme.onSurface,
    /** 标题左对齐（默认居中）。平板端需要避让左侧侧栏并左对齐时传 true。 */
    titleStartAligned: Boolean = false,
    /** 标题附加修饰符；平板端可传避让侧栏的内边距。 */
    titleModifier: Modifier = Modifier,
    scrollBehavior: SharedScrollBehavior? = null,
    contentPadding: (Dp) -> Unit = {},
    startAction: @Composable ((backdropAlpha: Float, shadowAlpha: Float) -> Unit)? = null,
    endAction: @Composable ((backdropAlpha: Float, shadowAlpha: Float) -> Unit)? = null,
    onAlphaChanged: (backdropAlpha: Float, shadowAlpha: Float) -> Unit = { _, _ -> },
) {
    val state = scrollBehavior?.state

    val isTablet = isTabletWidth()
    val effectiveShowLargeTitle = showLargeTitle ?: !isTablet

    val overScrollState = LocalOverScrollState.current

    val scrolledOffset = remember(scrollBehavior) {
        { scrollBehavior?.state?.heightOffset ?: 0f }
    }
    val largeTitleAlpha = remember(scrollBehavior) {
        {
            val frac = scrollBehavior?.state?.collapsedFraction ?: 0f
            1f - (frac * 2.5f).coerceIn(0f, 1f)
        }
    }
    val largeTitleBlur: () -> Dp = remember(scrollBehavior) {
        {
            val frac = scrollBehavior?.state?.collapsedFraction ?: 0f
            Dp((frac * 4f).coerceIn(0f, 1f) * CollapsibleTopAppBarDefaults.MaxLargeTitleBlur.value)
        }
    }
    val updateHeightOffsetLimit = remember(scrollBehavior) {
        { height: Int ->
            scrollBehavior?.state?.let { s ->
                val limit = -height.toFloat()
                if (s.heightOffsetLimit != limit) s.heightOffsetLimit = limit
            }
            Unit
        }
    }

    // 没有大标题时设置 heightOffsetLimit = -1 表示 bar 已完全折叠，不消费滚动。
    // 设为 -1 而非 0，确保 onPreScroll 中 heightOffset(0) > heightOffsetLimit(-1) 为 true，
    // 向上滚动时能正确重置 postCollapseScrollOffset。
    LaunchedEffect(effectiveShowLargeTitle, scrollBehavior) {
        if (!effectiveShowLargeTitle) {
            scrollBehavior?.state?.heightOffsetLimit = -1f
        }
    }

    val smallTitleVisible by remember(state, effectiveShowLargeTitle, showSmallTitle) {
        derivedStateOf {
            showSmallTitle
                // 没有大标题时，小标题始终显示
                ?: if (!effectiveShowLargeTitle) true
                // 默认：折叠到一定程度时显示
                else (state?.collapsedFraction ?: 0f) >= 0.45f
        }
    }
    val density = LocalDensity.current
    // 最近一次上报给 scrollBehavior / contentPadding 的顶栏高度，避免同值重复写状态
    val lastReportedHeight = remember { intArrayOf(-1) }
    val scrollShadowThresholdPx = with(density) { TopBarMaterialThreshold.toPx() }
    // overScrollState 只作身份键：offset 在 derivedStateOf 内部读，本身就会被订阅。
    // 若把它也写进 remember 键，回弹期间每一帧都要重建整个 derivedStateOf。
    val showButtonShadow = remember(scrollBehavior, showShadow, effectiveShowLargeTitle, overScrollState) {
        derivedStateOf {
            if (showShadow != null) return@derivedStateOf showShadow
            isTopBarMaterialVisible(
                contentOffset = scrollBehavior?.state?.contentOffset ?: 0f,
                thresholdPx = scrollShadowThresholdPx,
                overScrollOffset = overScrollState.offset,
            )
        }
    }
    val material = materialState ?: rememberTopBarMaterialState(showButtonShadow.value)
    val backdropAlpha = material.alpha
    val shadowAlpha = material.alpha
    LaunchedEffect(showButtonShadow.value) {
        state?.showButtonShadow = showButtonShadow.value
    }
    val smallTitleAlpha = remember { Animatable(if (smallTitleVisible) 1f else 0f) }
    val smallTitleTranslationY = remember { Animatable(if (smallTitleVisible) 0f else 20f) }
    val smallTitleBlur =
        remember { Animatable(if (smallTitleVisible) 0f else CollapsibleTopAppBarDefaults.MaxSmallTitleBlur.value) }

    LaunchedEffect(smallTitleVisible) {
        if (smallTitleVisible) {
            val showSpec = folmeSpring<Float>(damping = 1.0f, response = 0.3f)
            launch { smallTitleAlpha.animateTo(1f, showSpec) }
            launch { smallTitleTranslationY.animateTo(0f, showSpec) }
            launch { smallTitleBlur.animateTo(0f, showSpec) }
        } else {
            val hideSpec = folmeSpring<Float>(damping = 1.0f, response = 0.15f)
            launch { smallTitleAlpha.animateTo(0f, hideSpec) }
            launch { smallTitleTranslationY.animateTo(20f, hideSpec) }
            launch {
                smallTitleBlur.animateTo(
                    CollapsibleTopAppBarDefaults.MaxSmallTitleBlur.value,
                    hideSpec,
                )
            }
        }
    }

    // 调用方首帧只能拿到兜底初值（表现为「更多」先亮一帧材质才正常）
    SideEffect {
        onAlphaChanged(backdropAlpha(), shadowAlpha())
    }

    Box {

        Layout(
            {
                if (startAction != null) {
                    Box(
                        Modifier
                            .layoutId("startAction")
                            .zIndex(2f),
                    ) {
                        startAction(backdropAlpha(), shadowAlpha())
                    }
                }
                Box(
                    Modifier
                        .layoutId("title")
                        .then(titleModifier)
                        .padding(horizontal = CollapsibleTopAppBarDefaults.TitlePadding)
                        .graphicsLayer {
                            alpha = smallTitleAlpha.value
                            translationY = smallTitleTranslationY.value
                        }
                        .blur(Dp(smallTitleBlur.value)),
                ) {
                    Text(
                        text = title,
                        color = titleColor,
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Medium,
                        overflow = TextOverflow.Ellipsis,
                        softWrap = false,
                    )
                }
                if (endAction != null) {
                    Box(
                        Modifier
                            .layoutId("endAction")
                            .zIndex(2f),
                    ) {
                        endAction(backdropAlpha(), shadowAlpha())
                    }
                }
                if (effectiveShowLargeTitle) {
                    Box(
                        Modifier
                            .layoutId("largeTitle")
                            .padding(top = CollapsibleTopAppBarDefaults.CollapsedHeight)
                            .padding(horizontal = CollapsibleTopAppBarDefaults.TitlePadding)
                            .graphicsLayer { alpha = largeTitleAlpha() }
                            .blur(largeTitleBlur()),
                    ) {
                        Column(
                            modifier = Modifier.onSizeChanged { updateHeightOffsetLimit(it.height) },
                        ) {
                            Text(
                                text = largeTitle,
                                color = NexioTheme.colorScheme.onSurface,
                                fontSize = 30.sp,
                                fontWeight = FontWeight.Normal,
                            )
                        }
                    }
                }
            },
            modifier = modifier
                .windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Top))
                // 顶栏高度必须在测量期写入，不能用 onSizeChanged：它在 place 阶段才回调，
                // 当帧布局已结束，依赖它的内容只能等下一帧更新，表现为切 tab 时顶部距离慢一帧。
                .layout { measurable, constraints ->
                    val placeable = measurable.measure(constraints)
                    // 该 layout 位于 windowInsetsPadding 之内，测得的尺寸不含状态栏高度。
                    val height = placeable.height
                    if (height != lastReportedHeight[0]) {
                        lastReportedHeight[0] = height
                        scrollBehavior?.currentHeightPx = height.toFloat()
                        contentPadding(with(density) { height.toDp() })
                    }
                    layout(placeable.width, placeable.height) {
                        placeable.place(0, 0)
                    }
                },
        ) { measurables, constraints ->
            val backButtonPlaceable = measurables
                .firstOrNull { it.layoutId == "backButton" }
                ?.measure(constraints.copy(minWidth = 0, minHeight = 0))

            val startActionPlaceable = measurables
                .firstOrNull { it.layoutId == "startAction" }
                ?.measure(constraints.copy(minWidth = 0, minHeight = 0))

            val endActionPlaceable = measurables
                .firstOrNull { it.layoutId == "endAction" }
                ?.measure(constraints.copy(minWidth = 0, minHeight = 0))

            val maxTitleWidth = constraints.maxWidth
            val titleMaxWidth = if (maxTitleWidth == Constraints.Infinity) {
                maxTitleWidth
            } else {
                (maxTitleWidth * CollapsibleTopAppBarDefaults.TitleWidthFraction).roundToInt()
            }

            val titlePlaceable = measurables
                .fastFirst { it.layoutId == "title" }
                .measure(constraints.copy(minWidth = 0, maxWidth = titleMaxWidth, minHeight = 0))

            val largeTitlePlaceable = if (effectiveShowLargeTitle) {
                measurables
                    .firstOrNull { it.layoutId == "largeTitle" }
                    ?.measure(
                        constraints.copy(
                            minWidth = 0,
                            minHeight = 0,
                            maxHeight = Constraints.Infinity,
                        ),
                    )
            } else null

            val collapsedHeightPx = CollapsibleTopAppBarDefaults.CollapsedHeight.roundToPx()
            val expansion =
                ((largeTitlePlaceable?.height ?: 0) - collapsedHeightPx).coerceAtLeast(0)

            val offset = scrolledOffset()
            val collapseFraction = if (expansion > 0 && !offset.isNaN()) {
                (abs(offset) / expansion.toFloat()).coerceIn(0f, 1f)
            } else {
                0f
            }
            val barHeight = lerp(
                start = collapsedHeightPx.toFloat(),
                stop = (collapsedHeightPx + expansion).toFloat(),
                fraction = 1f - collapseFraction,
            ).roundToInt()

            val verticalCenter = collapsedHeightPx / 2

            layout(constraints.maxWidth, barHeight) {
                backButtonPlaceable?.placeRelative(
                    x = with(density) { 16.dp.roundToPx() },
                    y = verticalCenter - backButtonPlaceable.height / 2,
                )

                startActionPlaceable?.placeRelative(
                    x = with(density) { 16.dp.roundToPx() },
                    y = verticalCenter - startActionPlaceable.height / 2,
                )

                titlePlaceable.placeRelative(
                    x = if (titleStartAligned) {
                        0
                    } else {
                        (constraints.maxWidth - titlePlaceable.width) / 2
                    },
                    y = verticalCenter - titlePlaceable.height / 2,
                )

                endActionPlaceable?.placeRelative(
                    x = constraints.maxWidth - with(density) { 16.dp.roundToPx() } - endActionPlaceable.width,
                    y = verticalCenter - endActionPlaceable.height / 2,
                )

                val largeTitleY = if (offset.isNaN()) 0 else offset.roundToInt()
                largeTitlePlaceable?.placeRelative(x = 0, y = largeTitleY)
            }
        }
    }
}
