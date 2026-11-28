package com.haooz.nexioui.navigationback

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState

/**
 * 非 Android 平台的系统返回分发点。
 *
 * 这些平台没有 activity 的 `OnBackPressedDispatcher`，所以留一个栈：弹层 / 底部抽屉 / 连贯动画页
 * 在 `rememberNavigationBack().handler(...)` 里注册，宿主收到系统返回时调 [dispatch]。
 *
 * 后注册的（z 序在上）先被调用 —— 与组合顺序一致，弹窗天然压住它下面的页面。
 *
 * 只在主线程（组合 / UI 线程）使用，不加锁。
 */
object BackDispatcher {

    private val handlers = mutableListOf<() -> Unit>()

    /** 当前是否有能消费返回的层。 */
    val hasHandlers: Boolean get() = handlers.isNotEmpty()

    /** 注册一个返回回调，返回用于注销的 token。 */
    fun push(handler: () -> Unit): Any {
        handlers.add(handler)
        return handler
    }

    fun remove(token: Any) {
        handlers.remove(token)
    }

    /** 分发一次返回；true = 已被消费（宿主不要退出应用）。 */
    fun dispatch(): Boolean {
        val handler = handlers.lastOrNull() ?: return false
        handler()
        return true
    }
}

/**
 * Skia（iOS / Desktop / 鸿蒙 / wasm）实现：CMP 无预测性返回公共 API。
 *
 * 没有手势进度源，所以进度 / 边缘 / 位移一律报空值；但返回键本身仍要能吃掉 ——
 * 注册进 [BackDispatcher]，由宿主转交。
 */
@Composable
actual fun rememberNavigationBack(): NavigationBackState = remember {
    NavigationBackState(
        isTransitioningBack = { false },
        backProgress = { null },
        // 没有手势源：不报边缘（2 = EDGE_NONE）
        backSwipeEdge = { 2 },
        backSwipeOffsetY = { null },
        backSwipeOffsetX = { null },
        onHandler = {
            val registered = enabled
            // 回调每次组合都是新 lambda，不能当 key，否则每帧都会注销重注册、打乱栈序
            val latestCompleted = rememberUpdatedState(onBackCompleted)
            DisposableEffect(registered) {
                val token = if (registered) BackDispatcher.push { latestCompleted.value() } else null
                onDispose { if (token != null) BackDispatcher.remove(token) }
            }
        },
    )
}
