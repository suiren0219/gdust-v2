// CZeroX schedule controls using Miuix windows and solid AppCard surfaces.
// Week selection, course aggregation and GDUST display preferences stay local to the schedule.
package com.menglimi.gdust.v2.ui.schedule

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.menglimi.gdust.v2.ui.AppCard
import com.menglimi.gdust.v2.ui.AppUiTokens
import com.menglimi.gdust.v2.ui.LocalDs
import java.time.LocalDate
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.ArrowUpDown
import top.yukonga.miuix.kmp.icon.basic.Check
import top.yukonga.miuix.kmp.icon.basic.FastForward
import top.yukonga.miuix.kmp.icon.extended.Background
import top.yukonga.miuix.kmp.icon.extended.Backup
import top.yukonga.miuix.kmp.icon.extended.ConvertFile
import top.yukonga.miuix.kmp.icon.extended.More
import top.yukonga.miuix.kmp.window.WindowBottomSheet
import top.yukonga.miuix.kmp.window.WindowDialog

@Composable
internal fun ScheduleHeader(
    week: Int,
    currentWeek: Int,
    totalWeeks: Int,
    weekStart: LocalDate?,
    onChooseWeek: () -> Unit,
    onCurrentWeek: () -> Unit,
    onSwitchTerm: () -> Unit,
    onDisplaySettings: () -> Unit,
    onImport: () -> Unit,
) {
    val ds = LocalDs.current
    var showMenu by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().padding(horizontal = AppUiTokens.PagePadding).padding(top = 12.dp, bottom = 12.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "课表",
                fontSize = AppUiTokens.TitleSize,
                fontWeight = AppUiTokens.TitleWeight,
                color = ds.text,
                modifier = Modifier.weight(1f),
            )
            HeaderActionButton(onClick = onChooseWeek) {
                Icon(MiuixIcons.Basic.ArrowUpDown, contentDescription = "选择周次", modifier = Modifier.size(24.dp), tint = ds.text)
            }
            Box {
                HeaderActionButton(onClick = { showMenu = true }) {
                    Icon(MiuixIcons.More, contentDescription = "课表菜单", modifier = Modifier.size(26.dp), tint = ds.text)
                }
                if (showMenu) {
                    ScheduleMenu(
                        onChooseWeek = { showMenu = false; onChooseWeek() },
                        onDisplaySettings = { showMenu = false; onDisplaySettings() },
                        onSwitchTerm = { showMenu = false; onSwitchTerm() },
                        onImport = { showMenu = false; onImport() },
                        onDismiss = { showMenu = false },
                    )
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(
                Modifier.weight(1f).scheduleClickable(ds.bg, onChooseWeek).padding(vertical = 6.dp),
            ) {
                Text("第 $week 周", fontSize = 18.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold, color = ds.text)
                val weekEnd = weekStart?.plusDays(6)
                Text(
                    if (weekStart != null && weekEnd != null) {
                        "${weekStart.monthValue}月${weekStart.dayOfMonth}日 – ${weekEnd.monthValue}月${weekEnd.dayOfMonth}日"
                    } else "选择周次查看课程",
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    color = ds.sub,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            if (currentWeek in 1..totalWeeks && week != currentWeek) {
                TextButton(
                    text = "回本周",
                    onClick = onCurrentWeek,
                    minHeight = 44.dp,
                    insideMargin = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                    colors = ButtonDefaults.textButtonColors(color = ds.accent, textColor = ds.text, alpha = 0.12f),
                    textStyle = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Medium),
                )
            } else {
                Text(
                    when {
                        currentWeek < 1 -> "学期尚未开始"
                        currentWeek > totalWeeks -> "学期已结束"
                        else -> "本周"
                    },
                    fontSize = 13.sp,
                    color = ds.sub,
                    modifier = Modifier.padding(start = 12.dp),
                )
            }
        }
    }
}

@Composable
private fun HeaderActionButton(onClick: () -> Unit, content: @Composable androidx.compose.foundation.layout.BoxScope.() -> Unit) {
    val ds = LocalDs.current
    Box(
        Modifier.size(48.dp).scheduleClickable(ds.bg, onClick),
        contentAlignment = Alignment.Center,
        content = content,
    )
}

/** 按下仅增加轻微 tint，页头不增加常驻底衬。 */
@Composable
private fun Modifier.scheduleClickable(background: Color, onClick: () -> Unit): Modifier {
    val ds = LocalDs.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    return clip(RoundedCornerShape(14.dp))
        .background(if (pressed) ds.accent.copy(alpha = 0.07f).compositeOver(background) else background)
        .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick)
}

/** 菜单固定锚定右上角，使用统一的不透明卡片。 */
@Composable
private fun ScheduleMenu(
    onChooseWeek: () -> Unit,
    onDisplaySettings: () -> Unit,
    onSwitchTerm: () -> Unit,
    onImport: () -> Unit,
    onDismiss: () -> Unit,
) {
    val density = androidx.compose.ui.platform.LocalDensity.current
    androidx.compose.ui.window.Popup(
        alignment = Alignment.TopEnd,
        offset = androidx.compose.ui.unit.IntOffset(0, with(density) { 54.dp.roundToPx() }),
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.PopupProperties(focusable = true),
    ) {
        AppCard(
            modifier = Modifier.width(220.dp),
            insideMargin = PaddingValues(vertical = 8.dp),
        ) {
            ScheduleMenuItem("选择周次", MiuixIcons.Basic.FastForward, onChooseWeek)
            ScheduleMenuItem("显示设置", MiuixIcons.Background, onDisplaySettings)
            ScheduleMenuItem("切换学期", MiuixIcons.Normal.ConvertFile, onSwitchTerm)
            ScheduleMenuItem("导入课表", MiuixIcons.Backup, onImport)
        }
    }
}

@Composable
private fun ScheduleMenuItem(label: String, icon: ImageVector, onClick: () -> Unit) {
    val ds = LocalDs.current
    Row(
        Modifier.fillMaxWidth().heightIn(min = 50.dp).scheduleClickable(ds.card, onClick)
            .padding(horizontal = AppUiTokens.CardPadding, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(22.dp), tint = ds.text)
        Text(label, fontSize = 15.sp, color = ds.text)
    }
}

@Composable
internal fun WeekPickerDialog(
    show: Boolean,
    selectedWeek: Int,
    currentWeek: Int,
    totalWeeks: Int,
    weekCounts: Map<Int, Int>,
    fullTermImported: Boolean,
    onPick: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val ds = LocalDs.current
    val gridState = rememberLazyGridState()
    val maxHeight = (LocalConfiguration.current.screenHeightDp * 0.42f).dp.coerceAtMost(360.dp)
    LaunchedEffect(show, totalWeeks) {
        if (show) gridState.scrollToItem((selectedWeek - 1).coerceIn(0, totalWeeks - 1))
    }
    WindowDialog(
        show = show,
        title = "选择周次",
        summary = "共 $totalWeeks 周",
        onDismissRequest = onDismiss,
        backgroundColor = ds.card,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 66.dp),
                state = gridState,
                modifier = Modifier.fillMaxWidth().heightIn(max = maxHeight),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items((1..totalWeeks).toList(), key = { it }) { week ->
                    val picked = week == selectedWeek
                    val count = weekCounts[week] ?: 0
                    AppCard(
                        modifier = Modifier.heightIn(min = 72.dp).semantics { selected = picked },
                        cornerRadius = 14.dp,
                        color = if (picked) ds.accent.copy(alpha = 0.18f).compositeOver(ds.card) else ds.bg,
                        onClick = { onPick(week) },
                    ) {
                        Column(
                            Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 14.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text("第 $week 周", fontSize = 13.sp, fontWeight = if (picked) FontWeight.Bold else FontWeight.Medium, color = ds.text)
                            Text(
                                when {
                                    week == currentWeek -> "本周"
                                    count > 0 -> "$count 门课"
                                    fullTermImported -> "无课"
                                    else -> "未获取"
                                },
                                fontSize = 12.sp,
                                color = ds.sub,
                            )
                        }
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TextButton("取消", onClick = onDismiss, modifier = Modifier.weight(1f))
                Button(
                    onClick = { onPick(currentWeek) },
                    enabled = currentWeek in 1..totalWeeks,
                    colors = ButtonDefaults.buttonColorsPrimary(),
                    modifier = Modifier.weight(1f),
                ) { Text("回本周") }
            }
        }
    }
}

@Composable
internal fun ScheduleLessonSheet(
    show: Boolean,
    lessons: List<CellLesson>,
    week: Int,
    allWeeks: Int,
    onDismiss: () -> Unit,
    onDismissFinished: () -> Unit,
) {
    val ds = LocalDs.current
    WindowBottomSheet(
        show = show,
        title = "课程详情",
        backgroundColor = ds.bg,
        cornerRadius = AppUiTokens.CardCorner,
        onDismissRequest = onDismiss,
        onDismissFinished = onDismissFinished,
    ) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("第 $week 周 · 此时段及其他周次的实际排课", color = ds.sub, fontSize = 13.sp)
            lessons.forEach { lesson ->
                val surface = CourseColors.tint(lesson.name)
                    .copy(alpha = if (ds.isDark) 0.24f else 0.16f).compositeOver(ds.card)
                AppCard(
                    modifier = Modifier.fillMaxWidth(),
                    insideMargin = PaddingValues(AppUiTokens.CardPadding),
                    color = surface,
                ) {
                    Text(lesson.name, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = ds.text)
                    Text(
                        formatScheduleWeeks(lesson.weeks, allWeeks), fontSize = 13.sp, lineHeight = 19.sp,
                        color = ds.sub,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                    val start = ScheduleTimes.start(lesson.startSection)
                    val end = ScheduleTimes.end(lesson.endSection)
                    Text(
                        buildString {
                            append("第 ${lesson.startSection}–${lesson.endSection} 节")
                            if (start.isNotEmpty() && end.isNotEmpty()) append(" · $start–$end")
                        },
                        fontSize = 13.sp, color = ds.text, modifier = Modifier.padding(top = 8.dp),
                    )
                    if (lesson.room.isNotBlank()) Text(lesson.room, fontSize = 13.sp, color = ds.text, modifier = Modifier.padding(top = 3.dp))
                    if (lesson.teacher.isNotBlank()) Text(lesson.teacher, fontSize = 13.sp, color = ds.sub, modifier = Modifier.padding(top = 3.dp))
                }
            }
            Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColorsPrimary()) { Text("知道了") }
        }
    }
}

@Composable
internal fun ScheduleDisplayDialog(
    show: Boolean,
    textScale: Float,
    showTeacher: Boolean,
    showClassroom: Boolean,
    rowHeight: Int,
    corner: Int,
    alignCenter: Boolean,
    smartWeekend: Boolean,
    onScale: (Float) -> Unit,
    onTeacher: (Boolean) -> Unit,
    onClassroom: (Boolean) -> Unit,
    onRowHeight: (Int) -> Unit,
    onCorner: (Int) -> Unit,
    onAlignCenter: (Boolean) -> Unit,
    onSmartWeekend: (Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    val ds = LocalDs.current
    WindowBottomSheet(
        show = show,
        title = "显示设置",
        backgroundColor = ds.card,
        cornerRadius = AppUiTokens.CardCorner,
        onDismissRequest = onDismiss,
    ) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            ScheduleChoice(
                "卡片字号", listOf(0.85f to "小", 1f to "标准", 1.15f to "大", 1.3f to "特大"),
                listOf(0.85f, 1f, 1.15f, 1.3f).minBy { kotlin.math.abs(it - textScale) }, onScale,
            )
            ScheduleChoice(
                "卡片高度 · ${rowHeight}dp", listOf(48 to "紧凑", 54 to "标准", 64 to "宽松"),
                listOf(48, 54, 64).minBy { kotlin.math.abs(it - rowHeight) }, onRowHeight,
            )
            ScheduleChoice(
                "卡片圆角", listOf(6 to "小", 10 to "中", 14 to "大"),
                listOf(6, 10, 14).minBy { kotlin.math.abs(it - corner) }, onCorner,
            )
            ScheduleChoice("内容对齐", listOf(false to "左对齐", true to "居中"), alignCenter, onAlignCenter)
            ScheduleToggle("显示地点", showClassroom, onClassroom)
            ScheduleToggle("显示教师", showTeacher, onTeacher)
            ScheduleToggle("智能隐藏无课周末", smartWeekend, onSmartWeekend)
            Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColorsPrimary()) { Text("完成") }
        }
    }
}

@Composable
private fun <T> ScheduleChoice(label: String, options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit) {
    val ds = LocalDs.current
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, fontSize = 13.sp, color = ds.sub)
        Row(Modifier.fillMaxWidth().selectableGroup(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEach { (value, text) ->
                val picked = value == selected
                AppCard(
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp).semantics {
                        this.selected = picked
                        role = Role.RadioButton
                    },
                    cornerRadius = 14.dp,
                    color = if (picked) ds.accent.copy(alpha = 0.18f).compositeOver(ds.card) else ds.bg,
                    insideMargin = PaddingValues(horizontal = 4.dp, vertical = 12.dp),
                    onClick = { onSelect(value) },
                ) {
                    Text(
                        text,
                        color = ds.text,
                        fontSize = 13.sp,
                        fontWeight = if (picked) FontWeight.SemiBold else FontWeight.Normal,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

@Composable
private fun ScheduleToggle(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    val ds = LocalDs.current
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).toggleable(value = checked, role = Role.Switch, onValueChange = onChange),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, fontSize = 14.sp, color = ds.text, modifier = Modifier.weight(1f).padding(end = 10.dp))
        Switch(checked = checked, onCheckedChange = null)
    }
}

@Composable
internal fun ScheduleTermDialog(show: Boolean, year: String, current: String, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    val ds = LocalDs.current
    WindowDialog(
        show = show,
        title = "切换学期",
        summary = if (year.isNotBlank()) "$year 学年" else "选择学期",
        backgroundColor = ds.card,
        onDismissRequest = onDismiss,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            listOf("3" to "第一学期", "12" to "第二学期").forEach { (code, label) ->
                val picked = code == current
                AppCard(
                    modifier = Modifier.fillMaxWidth().semantics { selected = picked },
                    insideMargin = PaddingValues(AppUiTokens.CardPadding),
                    color = if (picked) ds.accent.copy(alpha = 0.18f).compositeOver(ds.card) else ds.bg,
                    onClick = { if (picked) onDismiss() else onPick(code) },
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(label, fontSize = 15.sp, color = ds.text, modifier = Modifier.weight(1f))
                        if (picked) Icon(MiuixIcons.Basic.Check, contentDescription = "当前学期", tint = ds.accent, modifier = Modifier.size(20.dp))
                    }
                }
            }
            Text("切换后会重新同步该学期课表。同步失败时可从课表菜单重新导入。", fontSize = 12.sp, lineHeight = 17.sp, color = ds.sub)
            TextButton("取消", onClick = onDismiss, modifier = Modifier.fillMaxWidth())
        }
    }
}
