// 派生自 compose-miuix-ui（Apache-2.0），已按 NexioUI 的 API 与包结构重写。

package com.haooz.nexioui.searchbar

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDp
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.haooz.nexioui.icon.NexioIcons
import com.haooz.nexioui.icon.basic.Search
import com.haooz.nexioui.icon.basic.SearchCleanup
import com.haooz.nexioui.internal.drawBlurredPathShadow
import com.haooz.nexioui.internal.hasFocusReassignBug
import com.haooz.nexioui.material.LocalMaterialNoneEnabled
import com.haooz.nexioui.material.NexioMaterial
import com.haooz.nexioui.navigationback.NavigationBackHandlerFor
import com.haooz.nexioui.text.NexioText
import com.haooz.nexioui.theme.NexioTheme
import com.haooz.softglass.backdrop.GlassBackdrop
import com.haooz.softglass.backdrop.GlassEffectScope
import com.haooz.softglass.backdrop.drawBackdrop
import com.haooz.softglass.backdrop.effects.blur
import com.haooz.softglass.backdrop.effects.runtimeShaderEffect
import com.haooz.softglass.glass.SOFT_GLASS_LIVE_SHADER
import com.haooz.softglass.glass.ThemeFill
import com.haooz.softglass.glass.applySoftGlass
import com.haooz.softglass.shape.ContinuousCapsule
import com.haooz.softglass.shape.ContinuousRoundedRectangle
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.delay

private const val SOFT_GLASS_SEARCH_KEY = "NexioSoftGlassSearch"
private const val SOFT_GLASS_SEARCH_ACTION_KEY = "NexioSoftGlassSearchAction"

/**
 * 搜索框：输入框 + 展开时右侧的操作按钮 + 下方可展开的结果区。
 *
 * @param inputField 输入框（通常传 [InputField]）。
 * @param expanded 是否展开。
 * @param actionIcon 展开时右侧显示的图标。
 * @param onActionClick 图标点击回调。
 * @param backdrop 玻璃采样源；为空时退回实色。
 * @param backdropAlpha 玻璃层不透明度（折叠过程跟手用）。
 * @param content 展开时显示的内容。
 */
@Composable
fun SearchBar(
    inputField: @Composable () -> Unit,
    onExpandedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    insideMargin: DpSize = SearchBarDefaults.InsideMargin,
    expanded: Boolean = false,
    actionIcon: ImageVector? = null,
    actionIconSize: Dp = 23.dp,
    onActionClick: (() -> Unit)? = null,
    backdrop: GlassBackdrop? = null,
    backdropAlpha: Float = 0f,
    content: @Composable ColumnScope.() -> Unit = {},
) {
    val currentOnExpandedChange by rememberUpdatedState(onExpandedChange)
    val glassBlur = NexioMaterial.glassBlurDp()
    val materialNone = LocalMaterialNoneEnabled.current
    val glassEffectsOn = NexioMaterial.glassEffectsEnabled()
    val isDark = NexioTheme.isDark

    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = insideMargin.height, horizontal = insideMargin.width),
            ) {
                inputField()
            }
            AnimatedVisibility(
                visible = expanded,
                enter = expandHorizontally() + slideInHorizontally(initialOffsetX = { it }),
                exit = shrinkHorizontally() + slideOutHorizontally(targetOffsetX = { it }),
            ) {
                val scale by transition.animateFloat(
                    transitionSpec = { tween(durationMillis = 300) },
                    label = "scale",
                ) { enterExit ->
                    if (enterExit == EnterExitState.Visible) 1f else 0.6f
                }
                val blur by transition.animateDp(
                    transitionSpec = { tween(durationMillis = 300) },
                    label = "blur",
                ) { enterExit ->
                    if (enterExit == EnterExitState.Visible) 0.dp else 7.dp
                }

                if (actionIcon != null && onActionClick != null) {
                    Box(
                        modifier = Modifier
                            .padding(end = 12.dp)
                            .size(SearchBarDefaults.InputFieldMinHeight)
                            .blur(blur)
                            .graphicsLayer {
                                scaleX = scale
                                scaleY = scale
                                alpha = scale
                            }
                            .clip(ContinuousCapsule())
                            .clickable { onActionClick() },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (backdrop != null) {
                            val actionGlassStyle = remember(glassBlur, glassEffectsOn, isDark) {
                                NexioMaterial.chromeGlassStyle(
                                    isDark = isDark,
                                    fill = ThemeFill,
                                )
                            }
                            // drawBackdrop 按引用比较 effects：每帧新建会重新录制采样层
                            val actionEffects: GlassEffectScope.() -> Unit =
                                remember(glassBlur, glassEffectsOn, actionGlassStyle) {
                                    {
                                        if (glassEffectsOn) {
                                            blur(glassBlur.dp.toPx())
                                            runtimeShaderEffect(
                                                key = SOFT_GLASS_SEARCH_ACTION_KEY,
                                                shaderString = SOFT_GLASS_LIVE_SHADER,
                                                uniformShaderName = "content",
                                            ) {
                                                applySoftGlass(
                                                    style = actionGlassStyle,
                                                    width = size.width,
                                                    height = size.height,
                                                )
                                            }
                                        }
                                    }
                                }
                            Box(
                                modifier = Modifier
                                    .matchParentSize()
                                    .drawBackdrop(
                                        backdrop = backdrop,
                                        shadow = null,
                                        shape = { ContinuousCapsule() },
                                        effects = actionEffects,
                                        layerBlock = { alpha = backdropAlpha },
                                        onDrawSurface = {},
                                    ),
                            )
                        }
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .background(
                                    color = when {
                                        // 无材质档不画玻璃，底色必须落到实色上
                                        materialNone -> NexioTheme.colorScheme.surfaceContainerHigh
                                        // 玻璃图层 alpha 跟折叠进度走，底色补足剩余部分
                                        backdrop != null && glassEffectsOn -> NexioTheme.colorScheme.surfaceContainerHigh
                                            .copy(alpha = 1f - backdropAlpha.coerceIn(0f, 1f))
                                        backdrop != null -> NexioTheme.colorScheme.surfaceContainerHigh
                                            .copy(alpha = 0.6f)
                                        else -> NexioTheme.colorScheme.surfaceContainerHigh
                                    },
                                    shape = ContinuousCapsule(),
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = actionIcon,
                                contentDescription = null,
                                modifier = Modifier.size(actionIconSize),
                            )
                        }
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = expanded,
            modifier = Modifier
                .fillMaxWidth()
                .background(color = NexioTheme.colorScheme.surface)
                .clip(ContinuousRoundedRectangle(20.dp)),
        ) {
            content()
        }
    }

    NavigationBackHandlerFor(
        enabled = expanded,
        onBackCompleted = { currentOnExpandedChange(false) },
        onBackCancelled = {},
    )
}

/**
 * 搜索输入框。
 *
 * @param label 未聚焦且无内容时的提示文本。
 * @param isLightTheme 当前是否浅色主题；`null` 时读 [NexioTheme.isDark]。
 */
@Composable
fun InputField(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: (String) -> Unit,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    label: String = "",
    enabled: Boolean = true,
    textStyle: TextStyle? = null,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    interactionSource: MutableInteractionSource? = null,
    backdrop: GlassBackdrop? = null,
    backdropAlpha: Float = 1f,
    shadowAlpha: Float = 0f,
    /**
     * 浅色主题判定。传 lambda 而不是 Boolean：装饰层每帧现读，
     * 组合期快照会在主题切换瞬间留一帧旧色。
     */
    isLightTheme: (() -> Boolean)? = null,
) {
    val systemIsLightTheme = !NexioTheme.isDark
    val resolveIsLightTheme: () -> Boolean = isLightTheme ?: { systemIsLightTheme }
    val currentOnQueryChange by rememberUpdatedState(onQueryChange)
    val currentOnSearch by rememberUpdatedState(onSearch)
    val currentOnExpandedChange by rememberUpdatedState(onExpandedChange)
    val internalInteractionSource = interactionSource ?: remember { MutableInteractionSource() }
    val capsuleShape = ContinuousCapsule()
    val glassBlur = NexioMaterial.glassBlurDp()
    val materialNone = LocalMaterialNoneEnabled.current
    val glassEffectsOn = NexioMaterial.glassEffectsEnabled()

    val actualLeadingIcon = leadingIcon ?: {
        Box(
            modifier = Modifier.padding(
                start = SearchBarDefaults.LeadingIconStartPadding,
                end = SearchBarDefaults.LeadingIconEndPadding,
            ),
        ) {
            Icon(
                modifier = Modifier.size(24.dp),
                imageVector = NexioIcons.Basic.Search,
                tint = NexioTheme.colorScheme.onSurfaceContainerHigh,
                contentDescription = "搜索",
            )
        }
    }

    val actualTrailingIcon = trailingIcon ?: {
        AnimatedVisibility(
            visible = query.isNotEmpty(),
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            Box(
                modifier = Modifier.padding(
                    start = SearchBarDefaults.TrailingIconStartPadding,
                    end = SearchBarDefaults.TrailingIconEndPadding,
                ),
                contentAlignment = Alignment.CenterStart,
            ) {
                Icon(
                    modifier = Modifier
                        .clip(capsuleShape)
                        .clickable { currentOnQueryChange("") },
                    imageVector = NexioIcons.Basic.SearchCleanup,
                    tint = NexioTheme.colorScheme.onSurfaceContainerHighest,
                    contentDescription = "清除",
                )
            }
        }
    }

    val focused = internalInteractionSource.collectIsFocusedAsState().value
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val textAlpha = remember { Animatable(1f) }

    val textColor = LocalContentColor.current
    val inputTextStyle = NexioTheme.textStyles.main
        .copy(fontWeight = FontWeight.Medium)
        .merge(textStyle)
        .copy(color = textColor)

    val cursorBrush = SolidColor(NexioTheme.colorScheme.primary)
    val labelText by remember(query, expanded, label) {
        derivedStateOf { if (!(query.isNotEmpty() || expanded)) label else "" }
    }

    // API 26–27：收起时禁用输入框，避开焦点错误重分配
    val workaroundEnabled = !hasFocusReassignBug || expanded
    val expandOnTapModifier = if (workaroundEnabled || !enabled) {
        Modifier
    } else {
        Modifier.pointerInput(Unit) { detectTapGestures { currentOnExpandedChange(true) } }
    }

    BasicTextField(
        value = query,
        onValueChange = currentOnQueryChange,
        modifier = modifier
            .then(expandOnTapModifier)
            .focusRequester(focusRequester)
            .onFocusChanged { if (it.isFocused) currentOnExpandedChange(true) }
            .semantics {
                onClick {
                    focusRequester.requestFocus()
                    true
                }
            },
        enabled = enabled && workaroundEnabled,
        singleLine = true,
        textStyle = inputTextStyle,
        cursorBrush = cursorBrush,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { currentOnSearch(query) }),
        interactionSource = internalInteractionSource,
        decorationBox = { innerTextField ->
            val isLightTheme = resolveIsLightTheme()
            val containerColor = if (isLightTheme) {
                Color(0xFFFAFAFA).copy(if (materialNone) 1f else 0.76f)
            } else {
                Color(0xFF242424).copy(if (materialNone) 1f else 0.84f)
            }
            val shadowColor = if (isLightTheme) Color(0x12000000) else Color(0x20000000)
            val containerGlassStyle = remember(glassBlur, glassEffectsOn, isLightTheme) {
                NexioMaterial.chromeGlassStyle(
                    isDark = !isLightTheme,
                    fill = ThemeFill,
                )
            }
            // drawBackdrop 按引用比较 effects：每帧新建会重新录制采样层
            val containerEffects: GlassEffectScope.() -> Unit =
                remember(glassBlur, glassEffectsOn, containerGlassStyle) {
                    {
                        if (glassEffectsOn) {
                            blur(glassBlur.dp.toPx())
                            runtimeShaderEffect(
                                key = SOFT_GLASS_SEARCH_KEY,
                                shaderString = SOFT_GLASS_LIVE_SHADER,
                                uniformShaderName = "content",
                            ) {
                                applySoftGlass(
                                    style = containerGlassStyle,
                                    width = size.width,
                                    height = size.height,
                                )
                            }
                        }
                    }
                }

            Box(
                modifier = Modifier.then(
                    if (backdrop != null) {
                        Modifier.drawBehind {
                            if (shadowAlpha > 0.01f) {
                                val maxBlurRadius = 10f * density
                                val blurRadius = maxBlurRadius * shadowAlpha
                                val composePath = Path().apply {
                                    val outline = capsuleShape.createOutline(size, layoutDirection, this@drawBehind)
                                    when (outline) {
                                        is Outline.Rounded -> addRoundRect(outline.roundRect)
                                        is Outline.Generic -> addPath(outline.path)
                                        is Outline.Rectangle -> addRect(outline.rect)
                                    }
                                }
                                drawBlurredPathShadow(
                                    path = composePath,
                                    color = shadowColor,
                                    blurRadius = blurRadius.coerceAtLeast(0.1f),
                                )
                            }
                        }
                    } else {
                        Modifier
                    },
                ),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (backdrop != null) {
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .drawBackdrop(
                                backdrop = backdrop,
                                shadow = null,
                                shape = { capsuleShape },
                                effects = containerEffects,
                                layerBlock = { alpha = backdropAlpha },
                                onDrawSurface = {},
                            ),
                    )
                }
                Row(
                    modifier = Modifier
                        .background(
                            color = when {
                                materialNone -> containerColor
                                glassEffectsOn -> NexioTheme.colorScheme.surfaceContainerHigh
                                    .copy(alpha = 1f - backdropAlpha.coerceIn(0f, 1f))
                                backdrop != null -> lerp(
                                    NexioTheme.colorScheme.surfaceContainerHigh,
                                    containerColor,
                                    backdropAlpha,
                                )
                                else -> NexioTheme.colorScheme.surfaceContainerHigh
                            },
                            shape = capsuleShape,
                        )
                        .fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    actualLeadingIcon()
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = SearchBarDefaults.InputFieldMinHeight),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        val mergedLabelStyle = remember(textStyle) {
                            TextStyle(
                                fontSize = SearchBarDefaults.InputFieldFontSize,
                                fontWeight = FontWeight.Medium,
                            ).merge(textStyle)
                        }
                        NexioText(
                            text = labelText,
                            style = mergedLabelStyle,
                            color = NexioTheme.colorScheme.onSurfaceContainerHigh,
                        )
                        Box(modifier = Modifier.graphicsLayer { alpha = textAlpha.value }) {
                            innerTextField()
                        }
                    }
                    actualTrailingIcon()
                }
            }
        },
    )

    LaunchedEffect(expanded) {
        if (expanded) {
            focusRequester.requestFocus()
        } else if (focused) {
            delay(100.milliseconds)
            if (query.isNotEmpty()) {
                textAlpha.animateTo(0f)
                currentOnQueryChange("")
                textAlpha.snapTo(1f)
            }
            focusManager.clearFocus()
        }
    }
}

/** 搜索框默认尺寸。 */
object SearchBarDefaults {
    val InsideMargin = DpSize(10.dp, 0.dp)
    val InputFieldMinHeight = 45.dp
    val InputFieldFontSize = 16.sp
    val LeadingIconStartPadding = 12.dp
    val LeadingIconEndPadding = 8.dp
    val TrailingIconStartPadding = 8.dp
    val TrailingIconEndPadding = 16.dp
}
