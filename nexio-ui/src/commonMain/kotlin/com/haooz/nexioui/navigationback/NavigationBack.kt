// 派生自 compose-miuix-ui（Apache-2.0），已按 NexioUI 的 API 与包结构重写。

package com.haooz.nexioui.navigationback

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf

/** 全局预测性返回动画开关（应用根部提供；弹窗等组件据此决定是否跟手）。 */
val LocalNexioPredictiveBackEnabled = staticCompositionLocalOf { true }

/** [NavigationBackState.handler] 的入参；用类打包是为了能以具名参数调用。 */
class BackHandlerScope(
    val enabled: Boolean,
    val onBackCompleted: () -> Unit,
    val onBackCancelled: () -> Unit,
)

/**
 * 返回转场状态。
 *
 * [handler] 与本对象里的进度读取必须来自同一次 [rememberNavigationBack]：
 * 拆成两个独立实例时，handler 收到的手势进度不会出现在另一个 state 上，跟手动画永远不动。
 */
class NavigationBackState(
    val isTransitioningBack: () -> Boolean,
    /** 手势进度 0f→1f；非手势期间为 null。 */
    val backProgress: () -> Float?,
    /** 手势起始边缘：0 = 左，1 = 右，2 = 非边缘（返回键 / 无手势）。 */
    val backSwipeEdge: () -> Int,
    /** 手势开始至今的垂直触摸位移（px）；非手势 / 返回键 / 无数据时为 null。 */
    val backSwipeOffsetY: () -> Float?,
    /** 手势开始至今的水平触摸位移（px）；语义与 [backSwipeOffsetY] 对称。 */
    val backSwipeOffsetX: () -> Float?,
    private val onHandler: @Composable BackHandlerScope.() -> Unit,
) {
    /**
     * 注册系统返回处理器（与本 state 共用同一份底层状态）。
     *
     * @param enabled 是否启用。
     * @param onBackCompleted 完整返回（键按下 / 手势完成）。
     * @param onBackCancelled 返回手势中途取消（老平台不会触发）。
     */
    @Composable
    fun handler(
        enabled: Boolean,
        onBackCompleted: () -> Unit,
        onBackCancelled: () -> Unit,
    ) {
        onHandler(BackHandlerScope(enabled, onBackCompleted, onBackCancelled))
    }
}

/**
 * 取得返回转场状态与返回处理器（同一份状态）。
 *
 * ```kotlin
 * val back = rememberNavigationBack()
 * back.handler(enabled = show, onBackCompleted = { … }, onBackCancelled = { … })
 * val progress = back.backProgress()
 * ```
 */
@Composable
expect fun rememberNavigationBack(): NavigationBackState

/**
 * 只处理返回键、不读进度的便捷入口。
 *
 * 若调用点还要读手势进度驱动动画，必须改用 [rememberNavigationBack] 后调用 `state.handler(...)`，
 * 否则会各自持有一份独立状态，跟手动画不会动。
 */
@Composable
fun NavigationBackHandlerFor(
    enabled: Boolean,
    onBackCompleted: () -> Unit,
    onBackCancelled: () -> Unit,
    state: NavigationBackState = rememberNavigationBack(),
) {
    state.handler(enabled, onBackCompleted, onBackCancelled)
}
