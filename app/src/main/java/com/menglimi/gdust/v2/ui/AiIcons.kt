package com.menglimi.gdust.v2.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/**
 * 自绘 AI 标识：一颗四角星加一颗小星，避免复用聊天气泡图标造成「消息」而非「助手」的误读。
 * 采用单色 path，颜色由 Icon(tint) 统一控制。
 */
val AiSparkleIcon: ImageVector by lazy {
    val nodes = PathParser().parsePathString(
        "M11,2 L13.3,9.2 L20.5,11.5 L13.3,13.8 L11,21 L8.7,13.8 L1.5,11.5 L8.7,9.2 Z " +
            "M19,2.6 L19.9,5.1 L22.4,6 L19.9,6.9 L19,9.4 L18.1,6.9 L15.6,6 L18.1,5.1 Z"
    ).toNodes()
    ImageVector.Builder(
        name = "AiSparkle",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).addPath(nodes, fill = SolidColor(Color.Black)).build()
}
