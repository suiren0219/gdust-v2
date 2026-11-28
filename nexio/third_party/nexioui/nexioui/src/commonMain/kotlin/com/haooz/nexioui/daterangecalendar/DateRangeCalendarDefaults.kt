package com.haooz.nexioui.daterangecalendar

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.haooz.nexioui.icon.NexioIcons
import com.haooz.nexioui.icon.extended.ChevronBackward
import com.haooz.nexioui.icon.extended.ChevronForward
import kotlinx.datetime.LocalDate

/** [DateRangeCalendar] 的默认尺寸、颜色与内置图标。 */
object DateRangeCalendarDefaults {
    val CellHeight = 40.dp
    val MarkSize = 34.dp
    val ContentPadding = 12.dp
    val HeaderHeight = 40.dp
    val NavButtonSize = 40.dp
    val NavIconSize = 20.dp

    /** 次选日期标记色。 */
    val SecondaryMarkColor = Color(0xFF34C759)

    /** 区间底色透明度。 */
    const val RangeBandAlpha: Float = 0.12f

    /** 上一个月图标。 */
    val ChevronBackward: ImageVector get() = NexioIcons.ChevronBackward

    /** 下一个月图标。 */
    val ChevronForward: ImageVector get() = NexioIcons.ChevronForward
}

/**
 * 日历文案与日期格式。
 *
 * 默认使用英文 / ISO 格式，调用方可以整体替换以接入本地化。
 */
data class CalendarStrings(
    val weekdayLabels: List<String> = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"),
    val monthTitle: (year: Int, month: Int) -> String = { year, month ->
        "$year-${month.toString().padStart(2, '0')}"
    },
    val previousMonthContentDescription: String = "Previous month",
    val nextMonthContentDescription: String = "Next month",
    val pickDateLabel: String = "Pick date",
    /** 双日期角色之间的连接符。 */
    val dateSeparator: String = "–",
    val formatDate: (LocalDate) -> String = { date ->
        "${date.year}-${date.monthNumber.toString().padStart(2, '0')}-" +
            date.dayOfMonth.toString().padStart(2, '0')
    },
) {
    companion object {
        /** 英文 / ISO 默认文案。 */
        val Default = CalendarStrings()
    }
}
