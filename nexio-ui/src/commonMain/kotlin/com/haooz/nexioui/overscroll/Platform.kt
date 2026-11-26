package com.haooz.nexioui.overscroll

/**
 * 当前平台是否启用弹簧越界回弹。
 *
 * 触屏平台（Android / iOS）接管越界手势；桌面端保留系统原生滚动，不叠加回弹。
 */
internal expect fun isOverScrollPlatformEnabled(): Boolean
