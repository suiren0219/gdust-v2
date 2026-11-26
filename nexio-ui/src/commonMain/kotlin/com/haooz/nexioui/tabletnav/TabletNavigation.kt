package com.haooz.nexioui.tabletnav

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.ViewConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.haooz.nexioui.icon.NexioIcons
import com.haooz.nexioui.icon.extended.Sidebar
import com.haooz.nexioui.interaction.InteractiveHighlight
import com.haooz.nexioui.material.NexioGlassStyle
import com.haooz.nexioui.material.NexioMaterial
import com.haooz.nexioui.capsuleslider.CapsuleFlingTracker
import com.haooz.nexioui.capsuleslider.CapsuleSelector
import com.haooz.nexioui.capsuleslider.CapsuleSlider
import com.haooz.nexioui.capsuleslider.capsuleSliderPill
import com.haooz.nexioui.dropdown.DropdownMaskMode
import com.haooz.nexioui.dropdown.DropdownPanelDragSelectState
import com.haooz.nexioui.dropdown.dropdownPanelDragSelect
import com.haooz.nexioui.dropdown.dropdownPanelSlidingMask
import com.haooz.nexioui.dropdown.dropdownPanelEntry
import com.haooz.nexioui.text.NexioText
import com.haooz.nexioui.theme.NexioTheme
import com.haooz.softglass.backdrop.GlassBackdrop
import com.haooz.softglass.backdrop.GlassEffectScope
import com.haooz.softglass.backdrop.drawBackdrop
import com.haooz.softglass.backdrop.effects.blur
import com.haooz.softglass.backdrop.effects.runtimeShaderEffect
import com.haooz.softglass.glass.BarFill
import com.haooz.softglass.glass.BrightLargeTone
import com.haooz.softglass.glass.SOFT_GLASS_LIVE_SHADER
import com.haooz.softglass.glass.applySoftGlass
import com.haooz.softglass.shape.ContinuousCapsule
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.roundToInt
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/** 面板柔光 shader 的缓存键：宽度逐帧变化，uniform 在 draw 期按当前尺寸重灌。 */
private const val LIVE_TABLET_NAV_GLASS_KEY = "NexioSoftGlassLiveTabletNav"

private val TabletNavExpandSpec = spring<Float>(
    dampingRatio = Spring.DampingRatioNoBouncy,
    stiffness = Spring.StiffnessMediumLow,
)

/**
 * 分组项图标上移专用弹簧——比遮罩软一档，只作用于图标
 */
private val TabletNavIconLiftSpring = spring<Float>(
    dampingRatio = 0.85f,
    stiffness = 240f,
    visibilityThreshold = 0.01f,
)

/** 侧栏条目。 */
@Immutable
data class TabletNavItem(
    val icon: ImageVector,
    val label: String,
    val contentDescription: String? = label,
    /** 收成顶栏胶囊时是否占一格。 */
    val inTopBar: Boolean = false,
    /** 非空表示「从这一项开始一个新分组」，同时作为分组标题。 */
    val sectionTitle: String? = null,
)

/**
 * 平板侧栏 + 顶栏形变导航：同一块面板连续收成顶部胶囊，进度只在 layout / draw 期读，组合期不碰高频值。
 *
 * @param items 导航条目。
 * @param selectedIndex 当前选中项，始终是 [items] 中的原始下标。
 * @param onSelect 条目点击回调，返回 [items] 原始下标。
 * @param backdrop 底层玻璃采样；null 时使用实色底。
 * @param state 形态状态。
 * @param modifier 外层修饰符。
 * @param screenCornerRadiusPx 屏幕圆角（px）；0 表示取不到，按默认基准收角。
 * @param navigationLabel 展开 / 折叠按钮的无障碍描述。
 * @param backToNow 底部插槽；null 时不显示。
 */
@Composable
fun TabletNavigation(
    items: List<TabletNavItem>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    backdrop: GlassBackdrop?,
    state: TabletNavState,
    modifier: Modifier = Modifier,
    screenCornerRadiusPx: Float = 0f,
    navigationLabel: String = "",
    backToNow: (@Composable () -> Unit)? = null,
) {
    val isDark = NexioTheme.isDark
    val density = LocalDensity.current
    val animationScope = rememberCoroutineScope()

    val textColor = NexioTheme.colorScheme.onSurfaceContainer.copy(alpha = 0.8f)
    val containerColor = if (isDark) {
        Color(0xFF242424).copy(alpha = NexioMaterial.solidSurfaceAlpha(0.8f))
    } else {
        Color.White.copy(alpha = NexioMaterial.solidSurfaceAlpha(0.8f))
    }
    val dividerColor =
        if (isDark) Color.White.copy(alpha = 0.12f) else Color.Black.copy(alpha = 0.08f)
    val sectionTitleColor = NexioTheme.colorScheme.onSurfaceVariantSummary.copy(alpha = 0.6f)

    val interactiveHighlight = remember(animationScope, state, isDark) {
        InteractiveHighlight(
            animationScope = animationScope,
            isDark = { isDark },
        )
    }

    // 顶栏只收 inTopBar 的项；slot 映射保持 items 顺序，事件回传原始下标。
    val topBarIndices = remember(items) {
        items.indices.filter { items[it].inTopBar }
    }
    val topBarSlotOf = remember(items) {
        val map = HashMap<Int, Int>(topBarIndices.size)
        topBarIndices.forEachIndexed { slot, index -> map[index] = slot }
        map
    }
    val topBarCount = topBarIndices.size

    // 分组：sectionTitle 所在的项就是分组首项
    val groupStarts = remember(items) {
        items.mapIndexedNotNull { index, item -> item.sectionTitle?.let { index to it } }.toMap()
    }
    val groupHeaderHeights = remember(items) { mutableStateMapOf<Int, Float>() }

    val sideCorner = rememberTabletNavCorner(screenCornerRadiusPx)
    val sideCornerPx = with(density) { sideCorner.toPx() }
    val morphShape = remember(state, sideCornerPx) { TabletNavMorphShape(state, sideCornerPx) }
    val sideShapeBlock: () -> Shape = remember(morphShape) { { morphShape } }

    val sideGlassStyle = NexioMaterial.chromeGlassStyle(
        base = NexioGlassStyle,
        isDark = isDark,
        fill = BarFill,
        tone = BrightLargeTone,
    )
    val sideGlassBlurDp = NexioMaterial.glassPanelBlurDp()
    val sideGlassOn = NexioMaterial.glassEffectsEnabled()
    val sideGlassEffects: GlassEffectScope.() -> Unit =
        remember(sideGlassBlurDp, sideGlassOn, sideGlassStyle, sideCornerPx) {
            {
                if (sideGlassOn) {
                    blur(sideGlassBlurDp.dp.toPx())
                    runtimeShaderEffect(
                        key = LIVE_TABLET_NAV_GLASS_KEY,
                        shaderString = SOFT_GLASS_LIVE_SHADER,
                        uniformShaderName = "content",
                    ) {
                        applySoftGlass(
                            style = sideGlassStyle,
                            width = size.width,
                            height = size.height,
                            radiusPx = sideCornerPx +
                                (min(size.width, size.height) / 2f - sideCornerPx) *
                                tabletNavSettleProgress(state),
                        )
                    }
                }
            }
        }
    // 实色底按引用比较，必须稳定
    val sideSurface: DrawScope.() -> Unit = remember(containerColor) {
        { drawRect(containerColor) }
    }

    val statusBarPadding = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val topPadding = if (statusBarPadding > 0.dp) statusBarPadding else 36.dp

    val capsuleWidthPx = with(density) {
        (TabletNavDefaults.TopBarPad * 2 + TabletNavDefaults.TopBarButtonWidth +
            TabletNavDefaults.TopBarTabWidth * topBarCount).toPx()
    }
    val capsuleHeightPx = with(density) { TabletNavDefaults.TopBarHeight.toPx() }

    val morphMotion = remember(animationScope, items.size) {
        TabletNavMorphMotion(
            animationScope,
            if (state.topBarMode) 1f else 0f,
            items.size,
            onFrame = { state.morphProgress.floatValue = it },
        )
    }
    LaunchedEffect(state.topBarMode) {
        val target = if (state.topBarMode) 1f else 0f
        if (morphMotion.lastTarget != target) morphMotion.to(target)
    }
    TabletNavExpandAnimator(state)

    // 落地回弹：从顶栏长度长回全高时，底缘往下顶一下再收回
    var landingJob by remember { mutableStateOf<Job?>(null) }
    val playLanding: () -> Unit = remember(animationScope, state) {
        {
            landingJob?.cancel()
            landingJob = animationScope.launch {
                delay(TabletNavDefaults.LandingDelayMillis)
                state.landBounce.floatValue = 0f
                animate(
                    initialValue = 0f,
                    targetValue = 1f,
                    animationSpec = tween(200, easing = LinearOutSlowInEasing),
                ) { v, _ -> state.landBounce.floatValue = v }
                animate(
                    initialValue = 1f,
                    targetValue = 0f,
                    animationSpec = spring(0.85f, Spring.StiffnessMediumLow),
                ) { v, _ -> state.landBounce.floatValue = v }
            }
        }
    }

    val topBarMode = state.topBarMode
    val panelStartInset = TabletNavDefaults.SideInset

    // 整个侧栏形态（展开 + 折叠图标轨）共用一套滑选与遮罩，形态切换时遮罩不重建；
    // 顶栏胶囊是独立形态，另走 TabletNavTopBarGestures。
    val dragSelectEnabled = !topBarMode
    val dragSelectState = remember {
        DropdownPanelDragSelectState(maskMode = DropdownMaskMode.Sliding)
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val isPortrait = maxHeight > maxWidth
        // 竖屏默认折叠（只在首次进入时设置一次）
        LaunchedEffect(Unit) {
            if (isPortrait) {
                state.expanded = false
                state.expandProgress.floatValue = 0f
            }
        }

        val hostHeightPx = with(density) { maxHeight.toPx() }
        val hostWidthPx = with(density) { maxWidth.toPx() }
        val panelStartInsetPx = with(density) { panelStartInset.toPx() }
        val topPaddingPx = with(density) { topPadding.toPx() }
        val sideTopPx = with(density) { (topPadding + 2.dp).toPx() }
        val sideBottomInsetPx = with(density) { TabletNavDefaults.BottomInset.toPx() }
        val maskPadPx = with(density) { TabletNavDefaults.PanelPadding.toPx() }
        val sideContentHeightPx = hostHeightPx - sideTopPx - sideBottomInsetPx

        val expandedWidthDp = maxOf(
            maxWidth * TabletNavDefaults.ExpandedWidthFraction,
            TabletNavDefaults.ExpandedMinWidth + TabletNavDefaults.SideInset,
        )
        val collapsedPanelPx = with(density) { TabletNavDefaults.RailWidth.toPx() }
        val expandedPanelPx = with(density) {
            (expandedWidthDp - TabletNavDefaults.SideInset).toPx()
        }
        val sideWidthProvider: () -> Float = {
            val p = state.expandProgress.floatValue
            collapsedPanelPx + (expandedPanelPx - collapsedPanelPx) * p
        }
        val sideItemWidthProvider: () -> Float = { sideWidthProvider() - maskPadPx * 2f }

        val topBarPadPx = with(density) { TabletNavDefaults.TopBarPad.toPx() }
        val topBarButtonWidthPx = with(density) { TabletNavDefaults.TopBarButtonWidth.toPx() }
        val topBarTabWidthPx = with(density) { TabletNavDefaults.TopBarTabWidth.toPx() }
        val topBarIconPx = with(density) { TabletNavDefaults.TopBarIconSize.toPx() }
        val sideItemHeightPx = with(density) { TabletNavDefaults.ItemHeight.toPx() }
        val sideItemGapPx = with(density) { TabletNavDefaults.ItemGap.toPx() }
        val buttonFromPx = with(density) { TabletNavDefaults.ItemHeight.toPx() }
        val buttonIconFromPx = with(density) { TabletNavDefaults.IconSize.toPx() }

        fun topBarSlotLeftPx(slot: Int): Float =
            topBarPadPx + topBarButtonWidthPx + slot * topBarTabWidthPx

        // 竖屏展开压暗；折叠时不占命中，离屏合成不进玻璃采样
        if (isPortrait) {
            AnimatedVisibility(
                visible = state.expanded && !topBarMode,
                enter = fadeIn(animationSpec = tween(180)),
                exit = fadeOut(animationSpec = tween(180)),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            alpha = 0.12f
                            compositingStrategy = CompositingStrategy.Offscreen
                        }
                        .background(Color.Black)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) {
                            state.expanded = false
                        }
                )
            }
        }

        Box(
            modifier = Modifier
                .tabletNavMorphPanel(
                    state = state,
                    sideInsetPx = panelStartInsetPx,
                    sideTopPx = sideTopPx,
                    capsuleTopPx = topPaddingPx,
                    sideBottomInsetPx = sideBottomInsetPx,
                    sideWidthPx = sideWidthProvider,
                    capsuleWidthPx = capsuleWidthPx,
                    capsuleHeightPx = capsuleHeightPx,
                    morphX = { state.morphProgress.floatValue },
                    morphY = { morphMotion.y },
                )
                .then(
                    if (backdrop != null) {
                        Modifier.drawBackdrop(
                            backdrop = backdrop,
                            shape = sideShapeBlock,
                            effects = sideGlassEffects,
                            // 柔光自带玻璃色；无材质档必须留实色底
                            onDrawSurface = if (sideGlassOn) null else sideSurface,
                        )
                    } else {
                        Modifier
                            .background(containerColor, morphShape)
                            .clip(morphShape)
                    }
                )
                .then(interactiveHighlight.modifier)
                .then(interactiveHighlight.gestureModifier)
        ) {
            if (topBarCount > 0) {
                TabletNavTopBarStrip(
                    state = state,
                    topBarIndices = topBarIndices,
                    selectedIndex = selectedIndex,
                    onSelect = onSelect,
                    stripLeftPx = topBarPadPx + topBarButtonWidthPx,
                    tabWidthPx = topBarTabWidthPx,
                    pressProgress = { interactiveHighlight.pressProgress },
                )
            }

            Column(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .tabletNavFixedSize(sideWidthProvider) { sideContentHeightPx }
                    .padding(TabletNavDefaults.PanelPadding),
                verticalArrangement = Arrangement.spacedBy(TabletNavDefaults.ItemGap),
                horizontalAlignment = Alignment.Start,
            ) {
                // 左上展开 / 折叠：进入顶栏形态后交给最左圆钮，这颗原地淡出
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(TabletNavDefaults.ItemHeight)
                        .graphicsLayer {
                            alpha = 1f - state.morphProgress.floatValue
                        }
                ) {
                    TabletNavSideItem(
                        state = state,
                        icon = NexioIcons.Regular.Sidebar,
                        label = navigationLabel,
                        contentDescription = navigationLabel,
                        selected = false,
                        textColor = textColor,
                        showLabel = false,
                        clickableEnabled = !topBarMode,
                        onClick = { state.expanded = !state.expanded },
                    )
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(
                            if (dragSelectEnabled) {
                                Modifier
                                    .dropdownPanelDragSelect(
                                        state = dragSelectState,
                                        // 展开 / 折叠全程就绪；只在形变到顶栏途中让位
                                        fraction = {
                                            1f - state.morphProgress.floatValue.coerceAtLeast(0f)
                                        },
                                        hapticFeedback = LocalHapticFeedback.current,
                                    )
                                    .dropdownPanelSlidingMask(
                                        dragSelectState,
                                        shape = ContinuousCapsule(),
                                        highlightPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp),
                                    )
                                    .onGloballyPositioned { coords ->
                                        val bounds = coords.boundsInRoot()
                                        dragSelectState.panelTopInRoot = bounds.top
                                        dragSelectState.panelLeftInRoot = bounds.left
                                    }
                            } else Modifier
                        ),
                    verticalArrangement = Arrangement.spacedBy(0.dp),
                ) {
                    items.forEachIndexed { index, item ->
                        val groupStart = groupStarts.keys.filter { it <= index }.maxOrNull()
                        val extraOffsetPx = if (groupStart != null) {
                            (index - groupStart) * sideItemHeightPx
                        } else {
                            0f
                        }
                        val itemOriginY = groupStarts.keys
                            .filter { it <= index }
                            .sumOf { (groupHeaderHeights[it] ?: 0f).toDouble() }
                            .toFloat() + index * sideItemHeightPx
                        val slot = topBarSlotOf[index] ?: topBarCount
                        val morphItem = Modifier.tabletNavMorphItem(
                            state = state,
                            sideWidthPx = sideItemWidthProvider,
                            sideHeightPx = sideItemHeightPx,
                            progress = { tabletNavItemProgress(morphMotion, index) },
                            targetLeftPx = topBarSlotLeftPx(slot) - maskPadPx,
                            targetTopPx = -(maskPadPx + sideItemHeightPx + sideItemGapPx + itemOriginY),
                            targetWidthPx = topBarTabWidthPx,
                            targetHeightPx = capsuleHeightPx,
                        )

                        if (item.sectionTitle != null) {
                            // 分界线 + 分组标题：只做淡入淡出，始终占着自己的高度
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .graphicsLayer {
                                        val expand = state.expandProgress.floatValue
                                        val morph = state.morphProgress.floatValue
                                        alpha = (expand * (1f - morph)).coerceIn(0f, 1f)
                                    }
                                    .onGloballyPositioned {
                                        groupHeaderHeights[index] = it.size.height.toFloat()
                                    },
                                verticalArrangement = Arrangement.spacedBy(0.dp),
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(
                                            top = 14.dp,
                                            bottom = 12.dp,
                                            start = 10.dp,
                                            end = 10.dp,
                                        )
                                        .height(0.8.dp)
                                        .background(dividerColor)
                                )
                                NexioText(
                                    text = item.sectionTitle,
                                    fontSize = TabletNavDefaults.SectionTitleFontSize,
                                    color = sectionTitleColor,
                                    modifier = Modifier.padding(
                                        start = 10.dp,
                                        top = 12.dp,
                                        bottom = 6.dp,
                                    ),
                                )
                            }
                        }

                        Box(modifier = morphItem) {
                            // 分组项折叠时选中态上移补掉分组标题与组内空行。
                            // targetValue 直接带折叠状态：展开=0、折叠=liftPx，切换选中项也在这里
                            // 一起落，整段位移只走 TabletNavIconLiftSpring，不再乘 expandProgress
                            // （那条由 TabletNavExpandSpec 驱动，会把折叠速率带偏）。
                            val liftPx = if (groupStart != null) {
                                (groupHeaderHeights[groupStart] ?: 0f) + extraOffsetPx
                            } else 0f
                            val lift = animateFloatAsState(
                                targetValue = if (
                                    groupStart != null &&
                                    index == selectedIndex &&
                                    !state.expanded
                                ) {
                                    liftPx
                                } else 0f,
                                animationSpec = TabletNavIconLiftSpring,
                                label = "tabletNavItemLift",
                            )
                            Box(
                                modifier = Modifier
                                    .offset {
                                        IntOffset(0, -lift.value.roundToInt())
                                    }
                                    .graphicsLayer {
                                        val p = state.expandProgress.floatValue
                                        val morph = state.morphProgress.floatValue
                                        if (groupStart != null) {
                                            alpha = (if (index == selectedIndex) 1f else p) *
                                                (1f - morph)
                                        } else if (!item.inTopBar) {
                                            // 不进顶栏的项随形变淡出
                                            alpha = 1f - morph
                                        }
                                    }
                            ) {
                                // 折叠轨上 inTopBar 项与选中项均可见，应可点可滑；
                                // 未选中的分组项 alpha=0 不可点
                                val itemEnabled = state.expanded ||
                                    item.inTopBar || index == selectedIndex
                                TabletNavSideItem(
                                    state = state,
                                    icon = item.icon,
                                    label = item.label,
                                    contentDescription = item.contentDescription,
                                    selected = index == selectedIndex,
                                    textColor = textColor,
                                    showLabel = true,
                                    labelFontWeight = FontWeight.Normal,
                                    progress = { tabletNavItemProgress(morphMotion, index) },
                                    clickableEnabled = !topBarMode && itemEnabled,
                                    dragSelectEntry = if (dragSelectEnabled) dragSelectState else null,
                                    onClick = {
                                        if (!topBarMode && itemEnabled) {
                                            onSelect(index)
                                        }
                                    },
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.weight(1f))

                val bottomSlot = backToNow
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    if (bottomSlot != null) {
                        bottomSlot()
                    }
                }
                Spacer(modifier = Modifier.height(20.dp))
            }
        }

        // 收成顶栏 / 回侧栏：钉在当前面板右缘，进顶栏形态后滑到胶囊最左边一颗
        Box(
            modifier = Modifier
                .zIndex(1f)
                .offset {
                    val slide = tabletNavSlideProgress(state)
                    val settle = tabletNavSettleProgress(state)
                    val fromX = panelStartInsetPx + sideWidthProvider() - maskPadPx - buttonFromPx
                    val fromY = sideTopPx + maskPadPx + (sideItemHeightPx - buttonFromPx) / 2f
                    val toX = (hostWidthPx - capsuleWidthPx) / 2f + topBarPadPx
                    val toY = topPaddingPx
                    IntOffset(
                        (fromX + (toX - fromX) * slide).roundToInt(),
                        (fromY + (toY - fromY) * settle).roundToInt(),
                    )
                }
                .tabletNavMorphBoxSize(
                    state = state,
                    widthFromPx = buttonFromPx,
                    heightFromPx = buttonFromPx,
                    widthToPx = topBarButtonWidthPx,
                    heightToPx = capsuleHeightPx,
                )
                .clip(ContinuousCapsule())
                .graphicsLayer {
                    val expand = state.expandProgress.floatValue.coerceIn(0f, 1f)
                    val morph = state.morphProgress.floatValue.coerceIn(0f, 1f)
                    alpha = expand + (1f - expand) * morph
                }
                .then(
                    when {
                        topBarMode -> Modifier.clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) {
                            state.expanded = true
                            state.topBarMode = false
                            playLanding()
                        }
                        state.expanded -> Modifier.clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) {
                            state.topBarMode = true
                        }
                        // 折叠态不挂任何指针节点，事件落到左上那颗展开按钮
                        else -> Modifier
                    }
                ),
            contentAlignment = Alignment.Center,
        ) {
            Box(Modifier.tabletNavMorphSize(state, buttonIconFromPx, topBarIconPx)) {
                Icon(
                    imageVector = TabletNavIconCollapseTopBar,
                    contentDescription = if (topBarMode) null else navigationLabel,
                    tint = textColor,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            alpha = 1f - state.morphProgress.floatValue.coerceIn(0f, 1f)
                        },
                )
                Icon(
                    imageVector = TabletNavIconExpandSidebar,
                    contentDescription = if (topBarMode) navigationLabel else null,
                    tint = textColor,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            alpha = state.morphProgress.floatValue.coerceIn(0f, 1f)
                        },
                )
            }
        }
    }
}

/** 驱动展开进度；只写状态，不在组合期读。 */
@Composable
private fun TabletNavExpandAnimator(state: TabletNavState) {
    LaunchedEffect(state.expanded) {
        val target = if (state.expanded) 1f else 0f
        if (state.expandProgress.floatValue == target) return@LaunchedEffect
        animate(
            initialValue = state.expandProgress.floatValue,
            targetValue = target,
            animationSpec = TabletNavExpandSpec,
        ) { value, _ -> state.expandProgress.floatValue = value }
    }
}

/**
 * 顶栏胶囊的滑块与手势区。
 *
 * 与 [com.haooz.nexioui.segmentedcontrol.SegmentedControl] 同款：
 * 按在滑块上拖动跟手 + 拖尾、点格子切换、快甩补一格，松手吸附。
 */
@Composable
private fun TabletNavTopBarStrip(
    state: TabletNavState,
    topBarIndices: List<Int>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    stripLeftPx: Float,
    tabWidthPx: Float,
    pressProgress: () -> Float,
) {
    val density = LocalDensity.current
    val viewConfiguration = LocalViewConfiguration.current
    val hapticFeedback = LocalHapticFeedback.current
    val animationScope = rememberCoroutineScope()
    val count = topBarIndices.size
    val selectorInsetPx = with(density) { CapsuleSlider.Inset.toPx() }
    val selectorHeightPx = with(density) {
        (TabletNavDefaults.TopBarHeight - CapsuleSlider.VerticalInset).roundToPx()
    }
    val selectorWidthPx = tabWidthPx - selectorInsetPx * 2f
    val stripRightPx = stripLeftPx + count * tabWidthPx
    val trailMaxPx = with(density) { CapsuleSlider.TrailMax.toPx() }
    val flingVelocityThreshold =
        with(density) { CapsuleSlider.FlingVelocityDpPerSecond.toPx() }
    val selectedSlot = (topBarIndices.indexOf(selectedIndex).coerceAtLeast(0))
        .coerceIn(0, (count - 1).coerceAtLeast(0))

    fun slotLeft(slot: Int): Float = stripLeftPx + slot * tabWidthPx + selectorInsetPx

    var currentSlot by remember { mutableIntStateOf(selectedSlot) }
    val latestSelectedSlot by rememberUpdatedState(selectedSlot)
    val latestOnSelect by rememberUpdatedState(onSelect)

    val capsule = remember(animationScope, count, tabWidthPx, stripLeftPx, selectorWidthPx) {
        CapsuleSelector(
            scope = animationScope,
            widthPx = selectorWidthPx,
            startLeftPx = slotLeft(currentSlot),
            leftLimit = { stripLeftPx..(stripRightPx - selectorWidthPx) },
        )
    }
    val settleToSlot: (Int) -> Unit = { slot ->
        val target = slotLeft(slot)
        capsule.settleTo(target, forward = target > capsule.leftPx)
    }
    val slotAtCapsule: () -> Int = {
        ((capsule.centerPx - stripLeftPx) / tabWidthPx - 0.5f).roundToInt()
            .coerceIn(0, (count - 1).coerceAtLeast(0))
    }

    LaunchedEffect(capsule) {
        snapshotFlow { latestSelectedSlot }.collectLatest { slot ->
            if (slot != currentSlot) {
                currentSlot = slot
                settleToSlot(slot)
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize().zIndex(2f)) {
        // 选中滑块：位置与宽度在 layout 期读
        Box(
            modifier = Modifier.capsuleSliderPill(
                selector = capsule,
                heightPx = selectorHeightPx,
                pressProgress = pressProgress,
                // 胶囊快成形了才显形，别在侧栏形态里闪一下
                visibility = {
                    val p = state.morphProgress.floatValue.coerceIn(0f, 1f)
                    ((p - 0.5f) * 2f).coerceIn(0f, 1f)
                },
            ),
        )

        // 手势区只在顶栏形态组合：侧栏形态下它若存在（哪怕不 consume）也会盖住
        // items Column 的点击/滑选命中，导致侧栏触控失效。
        if (state.topBarMode) {
            TabletNavTopBarGestures(
                capsule = capsule,
                topBarIndices = topBarIndices,
                stripLeftPx = stripLeftPx,
                tabWidthPx = tabWidthPx,
                trailMaxPx = trailMaxPx,
                flingVelocityThreshold = flingVelocityThreshold,
                currentSlot = currentSlot,
                setCurrentSlot = { currentSlot = it },
                settleToSlot = settleToSlot,
                slotAtCapsule = slotAtCapsule,
                latestOnSelect = latestOnSelect,
                hapticFeedback = hapticFeedback,
                viewConfiguration = viewConfiguration,
            )
        }
    }
}

/** 顶栏点按 + 拖动 + 快甩，照 SegmentedControl 的完整手势。 */
@Composable
private fun TabletNavTopBarGestures(
    capsule: CapsuleSelector,
    topBarIndices: List<Int>,
    stripLeftPx: Float,
    tabWidthPx: Float,
    trailMaxPx: Float,
    flingVelocityThreshold: Float,
    currentSlot: Int,
    setCurrentSlot: (Int) -> Unit,
    settleToSlot: (Int) -> Unit,
    slotAtCapsule: () -> Int,
    latestOnSelect: (Int) -> Unit,
    hapticFeedback: HapticFeedback,
    viewConfiguration: ViewConfiguration,
) {
    val lastIndex = (topBarIndices.size - 1).coerceAtLeast(0)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .zIndex(2f)
            .pointerInput(capsule, topBarIndices.size, tabWidthPx, hapticFeedback, flingVelocityThreshold) {
                val touchSlop = viewConfiguration.touchSlop
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    // 左侧「返回侧栏」圆钮交给它自己的 clickable
                    if (down.position.x < stripLeftPx) return@awaitEachGesture
                    down.consume()
                    val downX = down.position.x
                    val startSlot = currentSlot.coerceIn(0, lastIndex)
                    val fling = CapsuleFlingTracker(down.uptimeMillis, downX)

                    var dragging = abs(downX - capsule.centerPx) <=
                        tabWidthPx * CapsuleSlider.GrabHalfWidthRatio
                    var pressedSlot = -1
                    var dragAnchorLeft = capsule.leftPx
                    var dragAnchorX = downX
                    var trailVelocity = 0f

                    if (!dragging) {
                        pressedSlot = floor((downX - stripLeftPx) / tabWidthPx)
                            .toInt().coerceIn(0, lastIndex)
                        settleToSlot(pressedSlot)
                    }

                    var lastX = downX
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Main)
                        val change = event.changes.firstOrNull { it.id == down.id }
                        if (change == null || !change.pressed) {
                            val positionalTarget = if (dragging) {
                                slotAtCapsule()
                            } else {
                                pressedSlot.coerceIn(0, lastIndex)
                            }
                            val flingDir = if (dragging) {
                                fling.flingDirection(flingVelocityThreshold, touchSlop)
                            } else 0
                            val target = CapsuleSlider.flingTarget(
                                startIndex = startSlot,
                                positionalTarget = positionalTarget,
                                indexDirection = flingDir,
                                lastIndex = lastIndex,
                            )
                            setCurrentSlot(target)
                            settleToSlot(target)
                            if (target != startSlot) {
                                hapticFeedback.performHapticFeedback(
                                    HapticFeedbackType.TextHandleMove,
                                )
                            }
                            // 点按一律回传，哪怕目标格与胶囊本地 currentSlot 相同：
                            // 非顶栏项（如课程管理）折成顶栏时 indexOf=-1 被夹到 0，
                            // 胶囊显示在「今日」但页面还在原页；此时点今日 target==startSlot，
                            // 若跳过回传就永远切不过去。宿主 onMainTabSelected 已拦重复调用。
                            latestOnSelect(topBarIndices[target])
                            break
                        }

                        val dx = change.position.x - lastX
                        val totalDx = change.position.x - downX
                        fling.onMove(change.uptimeMillis, change.position, downX)
                        if (!dragging && abs(totalDx) > touchSlop) {
                            dragging = true
                            dragAnchorLeft = capsule.leftPx
                            dragAnchorX = change.position.x
                            trailVelocity = 0f
                            lastX = change.position.x
                        }
                        if (dragging) {
                            trailVelocity = trailVelocity * CapsuleSlider.TrailSmoothing +
                                dx * (1f - CapsuleSlider.TrailSmoothing)
                            val trail = (abs(trailVelocity) * CapsuleSlider.TrailGain)
                                .coerceAtMost(trailMaxPx)
                            val dir = when {
                                trailVelocity > 0.5f -> 1
                                trailVelocity < -0.5f -> -1
                                else -> 0
                            }
                            capsule.onDrag(
                                leftPx = dragAnchorLeft + (change.position.x - dragAnchorX),
                                trailPx = trail,
                                dir = dir,
                            )
                        }
                        lastX = change.position.x
                        change.consume()
                    }
                }
            },
    )
}

/**
 * 侧栏条目 = 顶栏胶囊格：同一个节点随 morph 连续换形状。
 * 条目只有点击，没有滑选。
 */
@Composable
private fun TabletNavSideItem(
    state: TabletNavState,
    icon: ImageVector,
    label: String,
    contentDescription: String?,
    selected: Boolean,
    textColor: Color,
    showLabel: Boolean,
    labelFontWeight: FontWeight = FontWeight.Medium,
    /** 本条目自己的 morph 进度；默认不参与错落 */
    progress: () -> Float = { tabletNavSettleProgress(state) },
    clickableEnabled: Boolean = true,
    /** 非 null 时由面板统一接管点按与滑选，本条目不再挂 clickable。 */
    dragSelectEntry: DropdownPanelDragSelectState? = null,
    onClick: () -> Unit,
) {
    val currentOnClick by rememberUpdatedState(onClick)
    val interaction = if (dragSelectEntry != null && clickableEnabled) {
        Modifier.dropdownPanelEntry(
            enabled = true,
            state = dragSelectEntry,
            selected = selected,
            action = { currentOnClick() },
        )
    } else {
        Modifier.clickable(
            enabled = clickableEnabled,
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = { currentOnClick() },
        )
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .then(interaction),
    ) {
        TabletNavItemContent(
            state = state,
            icon = icon,
            label = label,
            contentDescription = contentDescription,
            textColor = textColor,
            showLabel = showLabel,
            labelFontWeight = labelFontWeight,
            progress = progress,
        )
    }
}

/**
 * 条目内部：图标原地淡出、占位收到 0，文字从图标右侧走到格子正中。
 * 全部在 layout 期按进度插值。
 */
@Composable
private fun TabletNavItemContent(
    state: TabletNavState,
    icon: ImageVector,
    label: String,
    contentDescription: String?,
    textColor: Color,
    showLabel: Boolean,
    labelFontWeight: FontWeight,
    progress: () -> Float,
) {
    val density = LocalDensity.current
    val startPadPx = with(density) {
        (TabletNavDefaults.IconCenterX - TabletNavDefaults.PanelPadding -
            TabletNavDefaults.IconSize / 2f).toPx()
    }
    val iconFromPx = with(density) { TabletNavDefaults.IconSize.toPx() }
    val gapFromPx = with(density) { TabletNavDefaults.LabelGap.toPx() }
    val labelFromSp = TabletNavDefaults.LabelFontSize.value
    val labelScale = TabletNavDefaults.TopBarLabelFontSize.value / labelFromSp

    Layout(
        content = {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = textColor,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        // 顶栏格子里不要图标：不缩小，只淡出并让出占位
                        alpha = 1f - progress()
                    },
            )
            if (showLabel) {
                NexioText(
                    text = label,
                    fontSize = TabletNavDefaults.LabelFontSize,
                    fontWeight = labelFontWeight,
                    color = textColor,
                    maxLines = 1,
                    overflow = TextOverflow.Clip,
                    modifier = Modifier.graphicsLayer {
                        // 与 layout 期同一个公式，缩放后左上角对得上摆放点
                        val p = progress()
                        val s = 1f + (labelScale - 1f) * p
                        scaleX = s
                        scaleY = s
                        transformOrigin = TransformOrigin(0f, 0f)
                        // 折叠轨里文字收起；顶栏形态必须可见
                        val expand = state.expandProgress.floatValue
                        alpha = if (expand > p) expand else p
                    },
                )
            }
        },
    ) { measurables, constraints ->
        val p = progress()
        val w = constraints.maxWidth.toFloat()
        val h = constraints.maxHeight.toFloat()
        // 图标不参与缩放：尺寸恒为侧栏那一档，只靠 alpha 淡出
        val iconSize = iconFromPx.roundToInt().coerceAtLeast(1)
        val iconPlaceable = measurables[0].measure(Constraints.fixed(iconSize, iconSize))
        val textPlaceable = measurables.getOrNull(1)?.measure(Constraints())
        val scale = 1f + (labelScale - 1f) * p
        val textW = (textPlaceable?.width ?: 0) * scale
        val textH = (textPlaceable?.height ?: 0) * scale

        // 侧栏：图标在左、文字在右；图标占的宽度随进度收到 0
        val iconSlot = iconSize * (1f - p)
        val gapSlot = gapFromPx * (1f - p)
        val sideIconX = startPadPx
        val sideIconY = (h - iconSize) / 2f
        val textXFree = startPadPx + iconSlot + gapSlot
        val sideTextY = (h - textH) / 2f
        // 顶栏：文字落在格子正中
        val topTextX = (w - textW) / 2f
        val topTextY = (h - textH) / 2f
        val textX = textXFree + (topTextX - textXFree) * p

        layout(w.roundToInt(), h.roundToInt()) {
            iconPlaceable.place(sideIconX.roundToInt(), sideIconY.roundToInt())
            textPlaceable?.place(
                textX.roundToInt(),
                (sideTextY + (topTextY - sideTextY) * p).roundToInt(),
            )
        }
    }
}

/** 整块玻璃遮罩圆角：屏幕圆角 − 边距；取不到时退回基准值。 */
@Composable
private fun rememberTabletNavCorner(screenCornerRadiusPx: Float) =
    with(LocalDensity.current) {
        val screenRadius = screenCornerRadiusPx.toDp()
        val base = if (screenRadius > 0.dp) {
            screenRadius
        } else {
            TabletNavDefaults.FallbackCornerRadius
        }
        (base - TabletNavDefaults.CornerInset).coerceAtLeast(0.dp)
    }
