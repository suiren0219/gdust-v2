// Schedule-only adaptation of NexioSchedule revision 28a99a3:
// ui-shared/src/commonMain/kotlin/com/haooz/chedule/ui/activities/classschedule/ScheduleTopBar.kt
// ui-shared/src/commonMain/kotlin/com/haooz/chedule/ui/activities/main/CourseScheduleChrome.kt
// ui-shared/src/commonMain/kotlin/com/haooz/chedule/ui/activities/classschedule/MainScheduleScreen.kt
// Uses the vendored Miuix window components; week selection and GDUST detail/preferences are local adaptations.
package com.menglimi.gdust.v2.ui.schedule

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.graphics.TileMode
import com.haooz.softglass.backdrop.drawBackdrop
import com.haooz.softglass.backdrop.effects.blur
import com.kyant.backdrop.edgelight.edgeLight
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.menglimi.gdust.v2.ui.LocalDs
import com.menglimi.gdust.v2.ui.appSurface
import com.menglimi.gdust.v2.ui.pageTextBacking
import com.menglimi.gdust.v2.ui.secondaryTextColor
import com.menglimi.gdust.v2.ui.LocalWallpaperActive
import com.menglimi.gdust.v2.ui.nexio.LocalPageLayerBackdrop
import com.kyant.backdrop.edgelight.rememberCardEdgeLight
import java.time.LocalDate
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Theme
import top.yukonga.miuix.kmp.icon.basic.ArrowUpDown
import top.yukonga.miuix.kmp.icon.basic.Check
import top.yukonga.miuix.kmp.icon.basic.FastForward
import top.yukonga.miuix.kmp.icon.extended.Background
import top.yukonga.miuix.kmp.icon.extended.Backup
import top.yukonga.miuix.kmp.icon.extended.ConvertFile
import top.yukonga.miuix.kmp.icon.extended.More
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.PressFeedbackType
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
    onGlassEffect: () -> Unit,
) {
    val ds = LocalDs.current
    val secondary = secondaryTextColor()
    var showMenu by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().padding(top = 2.dp, bottom = 6.dp)) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 66.dp).padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            HeaderGlassButton(onClick = onSwitchTerm) {
                Icon(MiuixIcons.Normal.ConvertFile, contentDescription = "切换学期", modifier = Modifier.size(25.dp), tint = ds.text)
            }
            Column(
                Modifier.weight(1f).padding(horizontal = 8.dp).pageTextBacking(14.dp).clip(RoundedCornerShape(14.dp))
                    .clickable(role = Role.Button, onClickLabel = "选择周次", onClick = onChooseWeek)
                    .padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("第 $week 周", fontSize = 26.sp, lineHeight = 31.sp, fontWeight = FontWeight.Bold, color = ds.text, maxLines = 1)
                    Icon(MiuixIcons.Basic.ArrowUpDown, contentDescription = "选择周次", modifier = Modifier.size(14.dp), tint = secondary)
                }
                val weekEnd = weekStart?.plusDays(6)
                Text(
                    if (weekStart != null && weekEnd != null) {
                        "${weekStart.monthValue}月${weekStart.dayOfMonth}日 – ${weekEnd.monthValue}月${weekEnd.dayOfMonth}日"
                    } else "点击选择周次",
                    fontSize = 11.sp,
                    lineHeight = 16.sp,
                    color = secondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Box {
                HeaderGlassButton(onClick = { showMenu = true }) {
                    Icon(MiuixIcons.More, contentDescription = "课表菜单", modifier = Modifier.size(24.dp), tint = ds.text)
                }
                if (showMenu) {
                    ScheduleGlassMenu(
                        onChooseWeek = { showMenu = false; onChooseWeek() },
                        onDisplaySettings = { showMenu = false; onDisplaySettings() },
                        onGlassEffect = { showMenu = false; onGlassEffect() },
                        onSwitchTerm = { showMenu = false; onSwitchTerm() },
                        onImport = { showMenu = false; onImport() },
                        onDismiss = { showMenu = false },
                    )
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            if (currentWeek in 1..totalWeeks && week != currentWeek) {
                TextButton(
                    text = "回本周",
                    modifier = Modifier.pageTextBacking(18.dp),
                    onClick = onCurrentWeek,
                    minHeight = 36.dp,
                    insideMargin = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                    colors = ButtonDefaults.textButtonColors(color = ds.accent, textColor = ds.accent, alpha = 0.1f),
                    textStyle = TextStyle(fontSize = 12.sp),
                )
            } else {
                Box(Modifier.height(36.dp).pageTextBacking(18.dp).padding(horizontal = 12.dp), contentAlignment = Alignment.Center) {
                    Text(
                        when {
                            currentWeek < 1 -> "学期尚未开始"
                            currentWeek > totalWeeks -> "学期已结束"
                            else -> "本周"
                        },
                        fontSize = 12.sp,
                        color = if (week == currentWeek) ds.accent else secondary,
                    )
                }
            }
        }
    }
}

/** 顶栏与页面卡片共用材质；24dp 半径对应 48dp 圆钮。 */
@Composable
private fun HeaderGlassButton(onClick: () -> Unit, content: @Composable androidx.compose.foundation.layout.BoxScope.() -> Unit) {
    Box(
        Modifier.size(48.dp).appSurface(24.dp)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
        content = content,
    )
}

/** 右上角菜单：原地玻璃下拉（替代 WindowListPopup，修复弹层锚点错位）。 */
@Composable
private fun ScheduleGlassMenu(
    onChooseWeek: () -> Unit,
    onDisplaySettings: () -> Unit,
    onGlassEffect: () -> Unit,
    onSwitchTerm: () -> Unit,
    onImport: () -> Unit,
    onDismiss: () -> Unit,
) {
    val ds = LocalDs.current
    val density = androidx.compose.ui.platform.LocalDensity.current
    val shape = RoundedCornerShape(18.dp)
    androidx.compose.ui.window.Popup(
        alignment = Alignment.TopEnd,
        offset = androidx.compose.ui.unit.IntOffset(0, with(density) { 54.dp.roundToPx() }),
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.PopupProperties(focusable = true),
    ) {
        Column(
            Modifier
                .width(196.dp)
                // Popup 属于另一个窗口，不采样页面坐标，保留清晰的实底菜单。
                .background(ds.card, shape)
                .clip(shape)
                .padding(vertical = 6.dp),
        ) {
            ScheduleMenuItem("选择周次", MiuixIcons.Basic.FastForward, onChooseWeek)
            ScheduleMenuItem("课表外观", MiuixIcons.Background, onDisplaySettings)
            ScheduleMenuItem("课表效果", MiuixIcons.Theme, onGlassEffect)
            ScheduleMenuItem("切换学期", MiuixIcons.Normal.ConvertFile, onSwitchTerm)
            ScheduleMenuItem("导入课表", MiuixIcons.Backup, onImport)
        }
    }
}

@Composable
private fun ScheduleMenuItem(label: String, icon: ImageVector, onClick: () -> Unit) {
    val ds = LocalDs.current
    Row(
        Modifier.fillMaxWidth().heightIn(min = 50.dp).clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 12.dp),
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
                    Card(
                        modifier = Modifier.height(66.dp).semantics { selected = picked },
                        cornerRadius = 14.dp,
                        colors = CardDefaults.defaultColors(
                            color = if (picked) ds.accent else ds.text.copy(alpha = 0.045f),
                            contentColor = if (picked) MiuixTheme.colorScheme.onPrimary else ds.text,
                        ),
                        pressFeedbackType = PressFeedbackType.Sink,
                        showIndication = true,
                        onClick = { onPick(week) },
                    ) {
                        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("第 $week 周", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                            Text(
                                when {
                                    week == currentWeek -> "本周"
                                    count > 0 -> "$count 门课"
                                    fullTermImported -> "无课"
                                    else -> "未获取"
                                },
                                fontSize = 10.sp,
                                color = if (picked) MiuixTheme.colorScheme.onPrimary.copy(alpha = 0.8f)
                                    else if (week == currentWeek) ds.accent else ds.sub,
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
        backgroundColor = ds.card,
        onDismissRequest = onDismiss,
        onDismissFinished = onDismissFinished,
    ) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("第 $week 周 · 此时段及其他周次的实际排课", color = ds.sub, fontSize = 12.sp)
            lessons.forEach { lesson ->
                val tint = CourseColors.tint(lesson.name)
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    cornerRadius = 16.dp,
                    insideMargin = PaddingValues(16.dp),
                    colors = CardDefaults.defaultColors(color = tint.copy(alpha = 0.1f), contentColor = ds.text),
                ) {
                    Text(
                        lesson.name, fontSize = 17.sp, fontWeight = FontWeight.Bold,
                        color = CourseColors.readableText(tint, tint.copy(alpha = 0.1f).compositeOver(ds.card)),
                    )
                    Text(
                        formatScheduleWeeks(lesson.weeks, allWeeks), fontSize = 12.sp, lineHeight = 17.sp,
                        color = if (week in lesson.weeks) ds.accent else ds.sub,
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
    textMono: Boolean,
    smartWeekend: Boolean,
    onScale: (Float) -> Unit,
    onTeacher: (Boolean) -> Unit,
    onClassroom: (Boolean) -> Unit,
    onRowHeight: (Int) -> Unit,
    onCorner: (Int) -> Unit,
    onAlignCenter: (Boolean) -> Unit,
    onTextMono: (Boolean) -> Unit,
    onSmartWeekend: (Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    val ds = LocalDs.current
    WindowBottomSheet(show = show, title = "课表外观", backgroundColor = ds.card, onDismissRequest = onDismiss) {
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
            ScheduleChoice("文字颜色", listOf(false to "彩色", true to "单色"), textMono, onTextMono)
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
        com.menglimi.gdust.v2.ui.screens.SegmentedChoice(options, selected, onSelect)
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
                Card(
                    modifier = Modifier.fillMaxWidth().semantics { selected = picked },
                    cornerRadius = 14.dp,
                    insideMargin = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
                    colors = CardDefaults.defaultColors(color = if (picked) ds.accentSoft else ds.text.copy(alpha = 0.04f)),
                    showIndication = true,
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
