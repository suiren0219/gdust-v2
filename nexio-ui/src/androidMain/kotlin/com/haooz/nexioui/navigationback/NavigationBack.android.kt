package com.haooz.nexioui.navigationback

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.navigationevent.NavigationEvent
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.NavigationEventTransitionState
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState

/**
 * Android 实现：包住 androidx.navigationevent。
 *
 * handler 与进度读取必须共用同一个 `navigationEventState`；各 remember 一份时手势会被 handler
 * 收到而进度 state 不动，跟手动画静默失效（编译无报错）。
 */
@Composable
actual fun rememberNavigationBack(): NavigationBackState {
    val navigationEventState = rememberNavigationEventState(currentInfo = NavigationEventInfo.None)
    // 手势首帧 touchX / touchY：跟手位移的基准（NaN = 无手势）。
    val gestureStartY = remember { floatArrayOf(Float.NaN) }
    val gestureStartX = remember { floatArrayOf(Float.NaN) }

    return remember(navigationEventState) {
        NavigationBackState(
            isTransitioningBack = {
                val t = navigationEventState.transitionState
                t is NavigationEventTransitionState.InProgress &&
                    t.direction == NavigationEventTransitionState.TRANSITIONING_BACK
            },
            backProgress = {
                val t = navigationEventState.transitionState
                if (t is NavigationEventTransitionState.InProgress &&
                    t.direction == NavigationEventTransitionState.TRANSITIONING_BACK
                ) {
                    t.latestEvent.progress
                } else {
                    null
                }
            },
            backSwipeEdge = {
                val t = navigationEventState.transitionState
                if (t is NavigationEventTransitionState.InProgress &&
                    t.direction == NavigationEventTransitionState.TRANSITIONING_BACK
                ) {
                    t.latestEvent.swipeEdge
                } else {
                    NavigationEvent.EDGE_NONE
                }
            },
            // 垂直跟随：touchY − 首帧 touchY。touchY == 0 是非触摸事件（返回键），返回 null。
            backSwipeOffsetY = {
                val t = navigationEventState.transitionState
                if (t is NavigationEventTransitionState.InProgress &&
                    t.direction == NavigationEventTransitionState.TRANSITIONING_BACK
                ) {
                    val y = t.latestEvent.touchY
                    if (y > 0f) {
                        if (gestureStartY[0].isNaN()) gestureStartY[0] = y
                        y - gestureStartY[0]
                    } else {
                        null
                    }
                } else {
                    gestureStartY[0] = Float.NaN
                    null
                }
            },
            // 水平跟手：touchX − 首帧 touchX，语义与垂直对称。
            backSwipeOffsetX = {
                val t = navigationEventState.transitionState
                if (t is NavigationEventTransitionState.InProgress &&
                    t.direction == NavigationEventTransitionState.TRANSITIONING_BACK
                ) {
                    val x = t.latestEvent.touchX
                    if (x > 0f) {
                        if (gestureStartX[0].isNaN()) gestureStartX[0] = x
                        x - gestureStartX[0]
                    } else {
                        null
                    }
                } else {
                    gestureStartX[0] = Float.NaN
                    null
                }
            },
            onHandler = {
                NavigationBackHandler(
                    state = navigationEventState,
                    isBackEnabled = enabled,
                    onBackCompleted = onBackCompleted,
                    onBackCancelled = onBackCancelled,
                )
            },
        )
    }
}
