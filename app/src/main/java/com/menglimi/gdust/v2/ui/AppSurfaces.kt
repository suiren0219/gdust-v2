package com.menglimi.gdust.v2.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.haooz.nexioui.card.Card
import com.haooz.nexioui.card.CardDefaults
import com.haooz.nexioui.card.PressFeedbackType
import com.haooz.nexioui.icon.NexioIcons
import com.haooz.nexioui.icon.extended.Back
import com.haooz.nexioui.iconbutton.IconButton
import com.haooz.nexioui.overscroll.LocalOverScrollState
import com.haooz.nexioui.overscroll.OverScrollState
import com.haooz.nexioui.progressiveblurtopbar.ProgressiveBlurTopBar
import com.haooz.nexioui.topappbar.CollapsibleTopAppBar
import com.haooz.nexioui.topappbar.SharedScrollBehavior
import com.haooz.nexioui.topappbar.rememberSharedScrollBehavior
import com.haooz.nexioui.topbarmaterial.LocalTopBarMaterial
import com.haooz.nexioui.topbarmaterial.rememberTopBarMaterialState
import com.haooz.softglass.backdrop.GlassBackdrop
import com.haooz.softglass.backdrop.backdrops.layerBackdrop
import com.haooz.softglass.shape.ContinuousRoundedRectangle

object AppUiTokens {
    val PagePadding = 16.dp
    val TitleSize = 30.sp
    val TitleWeight = FontWeight.Normal
    val CardCorner = 18.dp
    val ItemCorner = 14.dp
    val CardGap = 12.dp
    val CardPadding = 16.dp
    val PreferenceRowMinHeight = 56.dp
    val PreferenceHorizontalPadding = 16.dp
    val PreferenceVerticalPadding = 14.dp
    val BottomBarClearance = 96.dp
}

@Composable
fun secondaryTextColor(): Color = LocalDs.current.sub

@Composable
fun Modifier.appSurface(cornerRadius: Dp = AppUiTokens.CardCorner): Modifier {
    val shape = remember(cornerRadius) { ContinuousRoundedRectangle(cornerRadius) }
    return clip(shape).background(LocalDs.current.card)
}

/** NexioUI G2 连续圆角卡片：可点击时自动启用原生 Sink 弹簧按压反馈，壁纸开启时自适应半透明。 */
@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = AppUiTokens.CardCorner,
    insideMargin: PaddingValues = PaddingValues(0.dp),
    onClick: (() -> Unit)? = null,
    color: Color = LocalDs.current.card,
    content: @Composable ColumnScope.() -> Unit,
) {
    val ds = LocalDs.current
    val base = if (color == ds.card) ds.card else color.compositeOver(ds.card)
    val resolvedColor = wallpaperSurfaceColor(base)
    CompositionLocalProvider(LocalContentColor provides ds.text) {
        if (onClick != null) {
            Card(
                onClick = onClick,
                modifier = modifier,
                cornerRadius = cornerRadius,
                insideMargin = insideMargin,
                pressFeedbackType = PressFeedbackType.Sink,
                colors = CardDefaults.defaultColors(
                    color = resolvedColor,
                    contentColor = ds.text,
                ),
                content = content,
            )
        } else {
            Card(
                modifier = modifier,
                cornerRadius = cornerRadius,
                insideMargin = insideMargin,
                colors = CardDefaults.defaultColors(
                    color = resolvedColor,
                    contentColor = ds.text,
                ),
                content = content,
            )
        }
    }
}

/** 顶栏圆钮：统一封装 NexioUI 液态玻璃 IconButton，随页面滚动渐变显现玻璃质感。 */
@Composable
fun NexioTopBarGlassIconButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    backdrop: GlassBackdrop = currentPageBackdrop(),
    backdropAlpha: Float = 1f,
    shadowAlpha: Float = 1f,
    enabled: Boolean = true,
    iconTint: Color = Color.Unspecified,
) {
    val hasWallpaper = hasActiveWallpaper()
    IconButton(
        onClick = onClick,
        backdrop = backdrop,
        modifier = if (hasWallpaper) Modifier.background(wallpaperSurfaceColor(), CircleShape) else Modifier,
        icon = icon,
        contentDescription = contentDescription,
        backdropAlpha = backdropAlpha,
        shadowAlpha = shadowAlpha,
        enabled = enabled,
        iconTint = if (hasWallpaper && iconTint == Color.Unspecified) LocalDs.current.text else iconTint,
    )
}

/**
 * 标准 NexioUI 页面骨架：
 * - 内容层与壁纸统一写入页面级 SoftGlass 采样层（[layerBackdrop]）
 * - 顶部悬浮 [ProgressiveBlurTopBar] + [CollapsibleTopAppBar]（大标题滚动平滑折叠 + 真 AGSL 渐进模糊）
 * - 返回键与右侧动作按钮均使用柔光玻璃圆钮，随滚动自动淡入材质与投影
 * - 下发 [PaddingValues] 自动避让状态栏、折叠顶栏高度以及底部导航栏/系统导航条
 */
@Composable
fun NexioPageScaffold(
    title: String,
    modifier: Modifier = Modifier,
    largeTitle: String = title,
    showLargeTitle: Boolean? = null,
    onBack: (() -> Unit)? = null,
    hasBottomBar: Boolean = false,
    startAction: (@Composable (backdrop: GlassBackdrop, backdropAlpha: Float, shadowAlpha: Float) -> Unit)? = null,
    endAction: (@Composable (backdrop: GlassBackdrop, backdropAlpha: Float, shadowAlpha: Float) -> Unit)? = null,
    floatingOverlay: @Composable BoxScope.(backdrop: GlassBackdrop) -> Unit = {},
    content: @Composable BoxScope.(contentPadding: PaddingValues, scrollBehavior: SharedScrollBehavior, backdrop: GlassBackdrop) -> Unit,
) {
    val ds = LocalDs.current
    val scrollBehavior = rememberSharedScrollBehavior()
    val overScrollState = remember { OverScrollState() }
    val pageBackdrop = rememberAppBackdrop(ds.bg)
    var topBarHeight by remember { mutableStateOf(if (showLargeTitle == false) 56.dp else 108.dp) }
    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val bottomPadding = if (hasBottomBar) AppUiTokens.BottomBarClearance + navBottom else navBottom + 24.dp
    val contentPadding = PaddingValues(
        top = statusTop + topBarHeight + 8.dp,
        bottom = bottomPadding,
    )

    CompositionLocalProvider(LocalOverScrollState provides overScrollState) {
        val topBarMaterial = rememberTopBarMaterialState(scrollBehavior)
        CompositionLocalProvider(LocalTopBarMaterial provides topBarMaterial) {
            Box(
                modifier = modifier
                    .fillMaxSize()
                    .nestedScroll(scrollBehavior.nestedScrollConnection),
            ) {
                // 采样层：包裹背景壁纸 + 可滚动正文，供顶栏渐进模糊与顶部玻璃按钮无递归采样
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .layerBackdrop(pageBackdrop),
                ) {
                    AppWallpaperLayer()
                    content(contentPadding, scrollBehavior, pageBackdrop)
                }

                // 顶栏渐进模糊 + 可折叠大标题
                ProgressiveBlurTopBar(
                    backdrop = pageBackdrop,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .then(if (hasActiveWallpaper()) Modifier.background(wallpaperSurfaceColor(ds.bg)) else Modifier),
                    tintColor = ds.bg,
                    blurAlpha = topBarMaterial.alpha(),
                ) {
                    CollapsibleTopAppBar(
                        title = title,
                        largeTitle = largeTitle,
                        showLargeTitle = showLargeTitle,
                        materialState = topBarMaterial,
                        scrollBehavior = scrollBehavior,
                        contentPadding = { measured -> topBarHeight = measured },
                        startAction = when {
                            startAction != null -> { bAlpha, sAlpha ->
                                startAction(pageBackdrop, bAlpha, sAlpha)
                            }
                            onBack != null -> { bAlpha, sAlpha ->
                                NexioTopBarGlassIconButton(
                                    icon = NexioIcons.Back,
                                    contentDescription = "返回",
                                    onClick = onBack,
                                    backdrop = pageBackdrop,
                                    backdropAlpha = bAlpha,
                                    shadowAlpha = sAlpha,
                                )
                            }
                            else -> null
                        },
                        endAction = endAction?.let { action ->
                            { bAlpha, sAlpha -> action(pageBackdrop, bAlpha, sAlpha) }
                        },
                    )
                }

                floatingOverlay(pageBackdrop)
            }
        }
    }
}
