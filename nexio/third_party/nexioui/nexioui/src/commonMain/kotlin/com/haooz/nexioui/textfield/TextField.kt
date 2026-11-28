package com.haooz.nexioui.textfield

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.haooz.nexioui.theme.NexioTheme

/**
 * 输入框：连续圆角背景 + 浮动标签 + 聚焦主色描边。
 *
 * Android 使用原生 EditText，保留系统选择控件与输入法行为；skiko 平台使用
 * BasicTextField。两端共享同一套外壳与标签动画逻辑。
 *
 * @param value 文本内容
 * @param onValueChange 内容变化回调
 * @param insideMargin 内边距（同时决定标签浮动的位移基准）
 * @param cornerRadius 圆角半径
 * @param label 标签；空串不显示
 * @param useLabelAsPlaceholder 空内容时标签作为占位符展示
 * @param enabled 是否可交互
 * @param readOnly 是否只读
 * @param textStyle 文字样式
 * @param leadingIcon 前置图标
 * @param trailingIcon 后置图标
 * @param singleLine 是否单行
 * @param maxLines 最大行数
 * @param minLines 最小行数
 * @param maxLength 最大字符数，超出部分在输入层截断
 * @param requestFocus 进入即请求焦点；由 true 变为 false 时视为关闭并清除焦点
 */
@Composable
expect fun TextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    insideMargin: DpSize = DpSize(16.dp, 16.dp),
    cornerRadius: Dp = 20.dp,
    label: String = "",
    useLabelAsPlaceholder: Boolean = false,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    textStyle: TextStyle = NexioTheme.textStyles.main,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    singleLine: Boolean = false,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    minLines: Int = 1,
    maxLength: Int = Int.MAX_VALUE,
    requestFocus: Boolean = false,
)

/**
 * [TextField] 的 [TextFieldValue] 重载，用于需要读写光标位置的场景。
 */
@Composable
expect fun TextField(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    modifier: Modifier = Modifier,
    insideMargin: DpSize = DpSize(16.dp, 16.dp),
    cornerRadius: Dp = 20.dp,
    label: String = "",
    useLabelAsPlaceholder: Boolean = false,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    textStyle: TextStyle = NexioTheme.textStyles.main,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    singleLine: Boolean = false,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    minLines: Int = 1,
    requestFocus: Boolean = false,
)
