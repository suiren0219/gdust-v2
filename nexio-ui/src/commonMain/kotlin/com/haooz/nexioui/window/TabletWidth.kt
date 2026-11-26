package com.haooz.nexioui.window

import androidx.compose.runtime.Composable

/**
 * 当前是否处于平板宽度（屏幕宽度 ≥ 600dp）。
 *
 * 判的是整个窗口，而不是父容器约束 —— 平板分栏里侧栏的可用宽度会偏小，按父容器判会误判。
 */
@Composable
expect fun isTabletWidth(): Boolean
