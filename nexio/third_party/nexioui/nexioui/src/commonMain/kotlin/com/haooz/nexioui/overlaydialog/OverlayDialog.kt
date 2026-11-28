package com.haooz.nexioui.overlaydialog

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import com.haooz.nexioui.navigationback.LocalNexioPredictiveBackEnabled
import com.haooz.softglass.backdrop.GlassBackdrop

/**
 * 对话框内容可继承的玻璃采样层。
 *
 * 跨栏 / 分栏场景下，宿主可以把整页采样层放进这个 Local，
 * 弹窗不显式传 [OverlayDialog] 的 `liquidGlassBackdrop` 时直接复用。
 */
val LocalOverlayDialogBackdrop = staticCompositionLocalOf<GlassBackdrop?> { null }

/**
 * 通用对话框：标题 + 摘要 + 自定义内容。
 *
 * 手机贴底、宽屏居中；点击外部或返回键关闭。
 *
 * @param show 是否显示。
 * @param modifier 面板修饰符。
 * @param title 标题。
 * @param titleColor 标题颜色。
 * @param summary 摘要。
 * @param summaryColor 摘要颜色。
 * @param backgroundColor 面板底色。
 * @param liquidGlassBackdrop 玻璃采样层；非 null 时启用模糊与折射。
 * @param enableWindowDim 是否压暗窗口。
 * @param onDismissRequest 点击外部或返回时的回调。
 * @param onDismissFinished 退出动画完全结束后的回调。
 * @param enablePredictiveBackGesture 是否跟随预测性返回手势；false 时手势不跟手，但返回键仍会关闭。
 * @param outsideMargin 面板外部留白。
 * @param insideMargin 面板内部留白。
 * @param defaultWindowInsetsPadding 是否自动避让安全区。
 * @param renderInRootScaffold 是否铺满整屏；false 时限制在调用方边界内。
 * @param content 面板内容。
 */
@Composable
fun OverlayDialog(
    show: Boolean,
    modifier: Modifier = Modifier,
    title: String? = null,
    titleColor: Color = DialogDefaults.titleColor(),
    summary: String? = null,
    summaryColor: Color = DialogDefaults.summaryColor(),
    backgroundColor: Color = DialogDefaults.backgroundColor(),
    liquidGlassBackdrop: GlassBackdrop? = LocalOverlayDialogBackdrop.current,
    enableWindowDim: Boolean = true,
    onDismissRequest: (() -> Unit)? = null,
    onDismissFinished: (() -> Unit)? = null,
    enablePredictiveBackGesture: Boolean = LocalNexioPredictiveBackEnabled.current,
    outsideMargin: DpSize = DialogDefaults.outsideMargin,
    insideMargin: DpSize = DialogDefaults.insideMargin,
    defaultWindowInsetsPadding: Boolean = true,
    renderInRootScaffold: Boolean = true,
    content: @Composable () -> Unit,
) {
    DialogContentLayout(
        show = show,
        titleColor = titleColor,
        summaryColor = summaryColor,
        backgroundColor = backgroundColor,
        outsideMargin = outsideMargin,
        insideMargin = insideMargin,
        modifier = modifier,
        title = title,
        summary = summary,
        liquidGlassBackdrop = liquidGlassBackdrop,
        enableWindowDim = enableWindowDim,
        onDismissRequest = onDismissRequest,
        onDismissFinished = onDismissFinished,
        enablePredictiveBackGesture = enablePredictiveBackGesture,
        defaultWindowInsetsPadding = defaultWindowInsetsPadding,
        renderInRootScaffold = renderInRootScaffold,
        content = content,
    )
}
