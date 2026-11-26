package com.haooz.nexioui.dropdown

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.NonRestartableComposable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.BlendModeColorFilter
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.haooz.nexioui.icon.NexioIcons
import com.haooz.nexioui.icon.basic.ArrowRight
import com.haooz.nexioui.icon.basic.ArrowUpDown
import com.haooz.nexioui.icon.basic.Check
import com.haooz.nexioui.text.NexioText
import com.haooz.nexioui.theme.NexioTheme
import com.haooz.softglass.shape.ContinuousRoundedRectangle

/** 触发区右侧的上下箭头。 */
@Composable
@NonRestartableComposable
fun RowScope.DropdownArrowEndAction(
    actionColor: Color,
    modifier: Modifier = Modifier,
) {
    val colorFilter = remember(actionColor) { ColorFilter.tint(actionColor) }
    Image(
        modifier = modifier
            .size(width = DropdownDefaults.ArrowSize.width, height = DropdownDefaults.ArrowSize.height)
            .align(Alignment.CenterVertically),
        imageVector = NexioIcons.Basic.ArrowUpDown,
        colorFilter = colorFilter,
        contentDescription = null,
    )
}

/**
 * 单个下拉条目。
 *
 * 条目自带圆角裁剪与选中底色；弹窗面板存在拖选手势时，点击交给面板统一处理。
 */
@Composable
fun DropdownImpl(
    item: DropdownItem,
    optionSize: Int,
    isSelected: Boolean,
    index: Int,
    dropdownColors: DropdownColors = DropdownDefaults.dropdownColors(),
    enabled: Boolean = item.enabled,
    dialogMode: Boolean = false,
    hasSubmenu: Boolean = false,
    isFirst: Boolean = index == 0,
    isLast: Boolean = index == optionSize - 1,
    onSelectedIndexChange: (Int) -> Unit,
) {
    val backgroundColor = if (isSelected) {
        dropdownColors.selectedContainerColor
    } else {
        dropdownColors.containerColor
    }
    val backgroundColorState = rememberUpdatedState(backgroundColor)

    val checkColor = when {
        !isSelected -> Color.Transparent
        !enabled -> NexioTheme.colorScheme.disabledOnSurface
        else -> dropdownColors.selectedIndicatorColor
    }

    val titleColor = when {
        !enabled -> NexioTheme.colorScheme.disabledOnSurface
        isSelected -> dropdownColors.selectedContentColor
        else -> dropdownColors.contentColor
    }

    val summaryColor = when {
        !enabled -> NexioTheme.colorScheme.disabledOnSurface
        isSelected -> dropdownColors.selectedSummaryColor
        else -> dropdownColors.summaryColor
    }

    val containerModifier = remember(dialogMode) {
        if (dialogMode) {
            Modifier
                .heightIn(min = DropdownDefaults.MinHeight)
                .widthIn(min = DropdownDefaults.MinWidth)
                .fillMaxWidth()
        } else {
            Modifier.fillMaxWidth()
        }
    }
    val innerRowModifier = remember(dialogMode) {
        if (dialogMode) Modifier else Modifier.widthIn(max = DropdownDefaults.MaxItemTextWidth)
    }

    val currentOnSelectedIndexChange by rememberUpdatedState(onSelectedIndexChange)
    // 弹窗面板会下发拖选状态；对话场景为 null，行为不变
    val dragSelectState = LocalDropdownPanelDragSelect.current
    val role = if (hasSubmenu) Role.Button else Role.RadioButton
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier
            .padding(
                start = 8.dp,
                end = 8.dp,
                top = if (isFirst) 8.dp else 0.dp,
                bottom = if (isLast) 8.dp else 0.dp,
            )
            .clip(ContinuousRoundedRectangle(17.dp))
            .drawBehind { drawRect(backgroundColorState.value) }
            .then(
                if (dragSelectState != null) {
                    // 面板手势层统一负责点按与滑动，条目上不再挂 clickable
                    Modifier
                        .dropdownPanelEntry(enabled = enabled) {
                            currentOnSelectedIndexChange(index)
                        }
                        .semantics {
                            this.role = role
                            if (enabled) {
                                onClick {
                                    currentOnSelectedIndexChange(index)
                                    true
                                }
                            }
                        }
                } else {
                    Modifier.clickable(
                        enabled = enabled,
                        role = role,
                        onClick = { currentOnSelectedIndexChange(index) },
                    )
                }
            )
            .then(containerModifier)
            .padding(horizontal = 14.dp, vertical = 10.5.dp),
    ) {
        Row(
            modifier = innerRowModifier,
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start,
        ) {
            item.icon?.let { it(IconCellModifier) }
            Column {
                NexioText(
                    text = item.text,
                    fontSize = 15.6.sp,
                    fontWeight = FontWeight.Medium,
                    color = titleColor,
                )
                item.summary?.let {
                    NexioText(
                        text = it,
                        fontSize = NexioTheme.textStyles.body2.fontSize,
                        color = summaryColor,
                    )
                }
            }
        }

        if (hasSubmenu) {
            val chevronColor = when {
                !enabled -> NexioTheme.colorScheme.disabledOnSurface
                isSelected -> dropdownColors.selectedContentColor
                else -> dropdownColors.summaryColor
            }
            val chevronColorFilter = remember(chevronColor) {
                BlendModeColorFilter(chevronColor, BlendMode.SrcIn)
            }
            Image(
                modifier = ChevronIconBaseModifier,
                imageVector = NexioIcons.Basic.ArrowRight,
                colorFilter = chevronColorFilter,
                contentDescription = null,
            )
        } else {
            val checkColorFilter = remember(checkColor) {
                BlendModeColorFilter(checkColor, BlendMode.SrcIn)
            }
            Image(
                modifier = CheckIconBaseModifier,
                imageVector = NexioIcons.Basic.Check,
                colorFilter = checkColorFilter,
                contentDescription = null,
            )
        }
    }
}

/** 纯文本条目。 */
@Composable
@NonRestartableComposable
fun DropdownImpl(
    text: String,
    optionSize: Int,
    isSelected: Boolean,
    index: Int,
    dropdownColors: DropdownColors = DropdownDefaults.dropdownColors(),
    enabled: Boolean = true,
    dialogMode: Boolean = false,
    onSelectedIndexChange: (Int) -> Unit,
) {
    val item = remember(text, enabled) { DropdownItem(text = text, enabled = enabled) }
    DropdownImpl(
        item = item,
        optionSize = optionSize,
        isSelected = isSelected,
        index = index,
        dropdownColors = dropdownColors,
        enabled = enabled,
        dialogMode = dialogMode,
        onSelectedIndexChange = onSelectedIndexChange,
    )
}

/** 下拉配色。 */
@Immutable
data class DropdownColors(
    val contentColor: Color,
    val summaryColor: Color,
    val containerColor: Color,
    val selectedContentColor: Color,
    val selectedSummaryColor: Color,
    val selectedContainerColor: Color,
    val selectedIndicatorColor: Color,
)

/** 一组下拉条目。 */
@Stable
data class DropdownEntry(
    val items: List<DropdownItem>,
    val enabled: Boolean = true,
)

/** 下拉条目。 */
@Stable
data class DropdownItem(
    val text: String,
    val enabled: Boolean = true,
    val selected: Boolean = false,
    val onClick: (() -> Unit)? = null,
    val icon: @Composable ((Modifier) -> Unit)? = null,
    val summary: String? = null,
    val children: List<DropdownItem>? = null,
) {
    constructor(
        icon: @Composable ((Modifier) -> Unit)? = null,
        title: String? = null,
        summary: String? = null,
    ) : this(
        text = title.orEmpty(),
        icon = icon,
        summary = summary,
    )
}

/** 下拉默认尺寸与配色。 */
object DropdownDefaults {
    val MinHeight: Dp = 56.dp
    val MinWidth: Dp = 200.dp
    val CheckIconSize: Dp = 20.dp
    val ArrowSize: DpSize = DpSize(width = 10.dp, height = 16.dp)
    val ChevronSize: DpSize = DpSize(width = 10.dp, height = 16.dp)
    val IconMinSize: Dp = 26.dp
    val MaxItemTextWidth: Dp = 216.dp
    val InsideHorizontalPadding: Dp = 20.dp
    val DialogHorizontalPadding: Dp = 28.dp
    val FirstLastVerticalPadding: Dp = 20.dp
    val MiddleVerticalPadding: Dp = 12.dp
    val IconEndPadding: Dp = 12.dp
    val CheckIconStartPadding: Dp = 12.dp

    @Composable
    fun dropdownColors(
        contentColor: Color = NexioTheme.colorScheme.onSurfaceContainer,
        summaryColor: Color = NexioTheme.colorScheme.onSurfaceVariantSummary,
        containerColor: Color = NexioTheme.colorScheme.surfaceContainer,
        selectedContentColor: Color = NexioTheme.colorScheme.primary,
        selectedSummaryColor: Color = NexioTheme.colorScheme.primary,
        selectedContainerColor: Color = NexioTheme.colorScheme.surfaceContainer,
        selectedIndicatorColor: Color = NexioTheme.colorScheme.primary,
    ): DropdownColors = rememberDropdownColorsImpl(
        contentColor = contentColor,
        summaryColor = summaryColor,
        containerColor = containerColor,
        selectedContentColor = selectedContentColor,
        selectedSummaryColor = selectedSummaryColor,
        selectedContainerColor = selectedContainerColor,
        selectedIndicatorColor = selectedIndicatorColor,
    )

    @Composable
    fun dialogDropdownColors(
        contentColor: Color = NexioTheme.colorScheme.onSurfaceContainer,
        summaryColor: Color = NexioTheme.colorScheme.onSurfaceVariantSummary,
        containerColor: Color = Color.Transparent,
        selectedContentColor: Color = NexioTheme.colorScheme.primary,
        selectedSummaryColor: Color = NexioTheme.colorScheme.primary,
        selectedContainerColor: Color = NexioTheme.colorScheme.secondaryVariant,
        selectedIndicatorColor: Color = NexioTheme.colorScheme.primary,
    ): DropdownColors = rememberDropdownColorsImpl(
        contentColor = contentColor,
        summaryColor = summaryColor,
        containerColor = containerColor,
        selectedContentColor = selectedContentColor,
        selectedSummaryColor = selectedSummaryColor,
        selectedContainerColor = selectedContainerColor,
        selectedIndicatorColor = selectedIndicatorColor,
    )
}

@Composable
private fun rememberDropdownColorsImpl(
    contentColor: Color,
    summaryColor: Color,
    containerColor: Color,
    selectedContentColor: Color,
    selectedSummaryColor: Color,
    selectedContainerColor: Color,
    selectedIndicatorColor: Color,
): DropdownColors = remember(
    contentColor,
    summaryColor,
    containerColor,
    selectedContentColor,
    selectedSummaryColor,
    selectedContainerColor,
    selectedIndicatorColor,
) {
    DropdownColors(
        contentColor = contentColor,
        summaryColor = summaryColor,
        containerColor = containerColor,
        selectedContentColor = selectedContentColor,
        selectedSummaryColor = selectedSummaryColor,
        selectedContainerColor = selectedContainerColor,
        selectedIndicatorColor = selectedIndicatorColor,
    )
}

private val CheckIconBaseModifier = Modifier
    .padding(start = DropdownDefaults.CheckIconStartPadding)
    .size(DropdownDefaults.CheckIconSize)

private val ChevronIconBaseModifier = Modifier
    .padding(start = DropdownDefaults.CheckIconStartPadding)
    .size(width = DropdownDefaults.ChevronSize.width, height = DropdownDefaults.ChevronSize.height)

private val IconCellModifier = Modifier
    .sizeIn(minWidth = DropdownDefaults.IconMinSize, minHeight = DropdownDefaults.IconMinSize)
    .padding(end = DropdownDefaults.IconEndPadding)
