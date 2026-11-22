package com.menglimi.gdust.v2.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.menglimi.gdust.v2.ui.LocalDs
import top.yukonga.miuix.kmp.basic.NumberPicker
import top.yukonga.miuix.kmp.basic.NumberPickerDefaults
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.window.WindowDialog
import java.time.DayOfWeek
import java.time.LocalDate
import kotlin.math.roundToInt

/** 学期设置和网页导入共用日期滚轮；只有确认才把选中日期写回表单。 */
@Composable
internal fun TermDateField(
    value: String,
    onValueChange: (String) -> Unit,
    fallback: String = "",
    allowFollowCalendar: Boolean = false,
) {
    val ds = LocalDs.current
    var show by rememberSaveable { mutableStateOf(false) }
    val date = DateWheelSelection.parse(value)
    val text = date?.let { "$it · ${weekday(it)}" }
        ?: if (value.isBlank()) {
            if (allowFollowCalendar) "跟随校历" else "选择开学日期"
        } else "重新选择日期"
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).clip(RoundedCornerShape(14.dp))
            .background(ds.bg).clickable(role = Role.Button, onClickLabel = "选择第一周周一") { show = true }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("第一周周一", color = ds.sub, fontSize = 12.sp)
            Text(text, color = ds.text, fontSize = 16.sp, fontWeight = FontWeight.Medium)
        }
        Text("选择", color = ds.text, fontSize = 14.sp)
    }
    if (show) {
        TermDateDialog(
            initial = date ?: DateWheelSelection.parse(fallback) ?: DateWheelSelection.monday(LocalDate.now()),
            allowFollowCalendar = allowFollowCalendar,
            onDismiss = { show = false },
            onConfirm = { onValueChange(it); show = false },
        )
    }
}

@Composable
private fun TermDateDialog(
    initial: LocalDate,
    allowFollowCalendar: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    val ds = LocalDs.current
    var draft by rememberSaveable(initial.toString()) { mutableStateOf(initial.toString()) }
    val date = LocalDate.parse(draft)
    val thisYear = remember { LocalDate.now().year }
    val years = minOf(thisYear - 10, initial.year, date.year)..maxOf(thisYear + 10, initial.year, date.year)
    val itemHeight = (44f * LocalDensity.current.fontScale.coerceAtLeast(1f)).dp
    val monday = date.dayOfWeek == DayOfWeek.MONDAY
    WindowDialog(
        show = true,
        title = "开学日期",
        summary = "选择第一周的周一，用于计算课表周次。",
        backgroundColor = ds.card,
        titleColor = ds.text,
        summaryColor = ds.sub,
        onDismissRequest = onDismiss,
    ) {
        Column(
            Modifier.verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Box(Modifier.fillMaxWidth().height(itemHeight).clip(RoundedCornerShape(14.dp)).background(ds.bg))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    DateWheelColumn("年", date.year, years, Modifier.weight(1.35f), itemHeight) {
                        draft = DateWheelSelection.changeMonth(date, year = it).toString()
                    }
                    DateWheelColumn("月", date.monthValue, 1..12, Modifier.weight(1f), itemHeight) {
                        draft = DateWheelSelection.changeMonth(date, month = it).toString()
                    }
                    DateWheelColumn("日", date.dayOfMonth, 1..date.lengthOfMonth(), Modifier.weight(1f), itemHeight) {
                        draft = date.withDayOfMonth(it).toString()
                    }
                }
            }
            Text("$date · ${weekday(date)}", color = ds.text, fontSize = 16.sp, fontWeight = FontWeight.Medium)
            Text(if (monday) "已选中周一，可确认开学日期。" else "开学日期须为周一。", color = ds.sub, fontSize = 13.sp)
            TextButton("改为本周周一 ${DateWheelSelection.monday(date)}", enabled = !monday, onClick = {
                draft = DateWheelSelection.monday(date).toString()
            }, modifier = Modifier.fillMaxWidth())
            if (allowFollowCalendar) {
                TextButton("跟随学校校历", onClick = { onConfirm("") }, modifier = Modifier.fillMaxWidth())
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton("取消", onClick = onDismiss, modifier = Modifier.weight(1f))
                Box(Modifier.weight(1f)) {
                    Button("确定", enabled = monday) { onConfirm(date.toString()) }
                }
            }
        }
    }
}

@Composable
private fun DateWheelColumn(
    label: String,
    value: Int,
    range: IntRange,
    modifier: Modifier,
    itemHeight: androidx.compose.ui.unit.Dp,
    onValueChange: (Int) -> Unit,
) {
    val ds = LocalDs.current
    NumberPicker(
        value = value,
        onValueChange = onValueChange,
        range = range,
        label = { "$it$label" },
        visibleItemCount = 3,
        wrapAround = false,
        itemHeight = itemHeight,
        colors = NumberPickerDefaults.colors(selectedTextColor = ds.text, unselectedTextColor = ds.sub),
        textStyle = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.SemiBold),
        modifier = modifier.semantics(mergeDescendants = true) {
            contentDescription = label
            stateDescription = "$value$label"
            progressBarRangeInfo = ProgressBarRangeInfo(value.toFloat(), range.first.toFloat()..range.last.toFloat())
            setProgress { target -> onValueChange(target.roundToInt().coerceIn(range)); true }
            customActions = listOf(
                CustomAccessibilityAction("上一$label") {
                    if (value > range.first) { onValueChange(value - 1); true } else false
                },
                CustomAccessibilityAction("下一$label") {
                    if (value < range.last) { onValueChange(value + 1); true } else false
                },
            )
        },
    )
}

private fun weekday(date: LocalDate): String = "周" + "一二三四五六日"[date.dayOfWeek.value - 1]
