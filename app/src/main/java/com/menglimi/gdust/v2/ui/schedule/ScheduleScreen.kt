// Adapted from NexioSchedule revision 28a99a3:
// ui-shared/src/commonMain/kotlin/com/haooz/chedule/ui/activities/classschedule/{ScheduleTopBar,MainScheduleScreen}.kt
// ui-shared/src/portable/kotlin/com/haooz/chedule/ui/activities/classschedule/{CourseCard,DayColumn,SectionColumn}.kt
// GDUST keeps its actual CourseBlock data, school times, display preferences and date-based status.
// This is a layout/component adaptation, not the portable source's GlassSurface rendering host.
package com.menglimi.gdust.v2.ui.schedule

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.TileMode
import com.haooz.softglass.backdrop.drawBackdrop
import com.haooz.softglass.backdrop.effects.blur
import com.haooz.softglass.backdrop.effects.runtimeShaderEffect
import com.haooz.softglass.glass.SOFT_GLASS_LIVE_SHADER
import com.haooz.softglass.glass.applySoftGlass
import com.kyant.backdrop.edgelight.edgeLight
import com.kyant.backdrop.edgelight.rememberCardEdgeLight
import com.menglimi.gdust.v2.ui.nexio.glass.ScheduleSoftGlassStyle
import com.menglimi.gdust.v2.ui.LocalWallpaperActive
import com.menglimi.gdust.v2.ui.nexio.LocalPageLayerBackdrop
import com.menglimi.gdust.v2.ui.nexio.glassSurfaceColor
import com.menglimi.gdust.v2.data.CalendarInfo
import com.menglimi.gdust.v2.data.CourseBlock
import com.menglimi.gdust.v2.data.Repository
import com.menglimi.gdust.v2.ui.LocalDs
import com.menglimi.gdust.v2.ui.LocalUiStyle
import com.menglimi.gdust.v2.ui.UiStyle
import java.time.LocalDate
import java.time.LocalDateTime
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Backup
import top.yukonga.miuix.kmp.utils.PressFeedbackType

/** Nexio「效果」页六项卡片材质参数（从 Store 直读，回设置页即生效）。 */
internal data class GlassSpec(
    val tint: Float,
    val opacity: Float,
    val blurPx: Int,
    val refraction: Float,
    val edge: Int,
    val highlightEdge: Boolean,
)

internal object ScheduleLayout {
    val RowHeight = 54.dp
    val SectionWidth = 36.dp
    val DateHeight = 40.dp
    val BreakHeight = 24.dp
    val CardGap = 2.dp
    val BottomClearance = 100.dp

    fun sectionY(section: Int, rowHeight: Dp): Dp = rowHeight * (section - 1) +
        (if (section >= 5) BreakHeight else 0.dp) + (if (section >= 9) BreakHeight else 0.dp)
}

@Suppress("UNUSED_PARAMETER") // Legacy week callbacks remain source-compatible; the pager owns week navigation.
@Composable
fun ScheduleScreen(
    repo: Repository,
    calendar: CalendarInfo?,
    blocks: List<CourseBlock>,
    syncError: String?,
    onPrevWeek: () -> Unit,
    onNextWeek: () -> Unit,
    onSwitchTerm: (String) -> Unit = {},
    fullTermImported: Boolean = false,
    onImport: () -> Unit = {},
    onGlassEffect: () -> Unit = {},
) {
    // One snapshot drives date, time, title, section marker, card status and the red line.
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            val tick = LocalDateTime.now()
            now = tick
            delay((60_000L - tick.second * 1_000L - tick.nano / 1_000_000L + 50L).coerceAtLeast(50L))
        }
    }
    val termStart = remember(calendar?.startDate) { ScheduleTimes.parseDate(calendar?.startDate) }
    val validBlocks = remember(blocks) {
        blocks.filter { it.week > 0 && it.day in 1..7 && it.startSection > 0 && it.endSection >= it.startSection }
            .distinct()
    }
    val totalWeeks = maxOf(calendar?.allWeek?.takeIf { it > 0 } ?: 20, validBlocks.maxOfOrNull { it.week } ?: 1)
    val currentWeek = termStart?.let { ScheduleTimes.weekAt(it, now.toLocalDate()) }
        ?: calendar?.week ?: 1
    val initialWeek = currentWeek.coerceIn(1, totalWeeks)
    val context = LocalContext.current
    val stored = remember(context) { context.getSharedPreferences("gdust_cache", Context.MODE_PRIVATE) }
    var textScale by remember(repo) { mutableStateOf(repo.store.courseTextScale) }
    var showTeacher by remember(repo) { mutableStateOf(repo.store.showTeacherOnCard) }
    var showClassroom by remember(repo) { mutableStateOf(repo.store.showClassroomOnCard) }
    // Shared storage still defaults to 56dp/top-start. Use Nexio defaults only for unset keys.
    var rowHeight by remember(repo) {
        mutableStateOf(if (stored.contains("course_row_h")) repo.store.courseRowHeight else ScheduleLayout.RowHeight.value.toInt())
    }
    var corner by remember(repo) { mutableStateOf(repo.store.courseCorner) }
    var alignCenter by remember(repo) {
        mutableStateOf(if (stored.contains("card_align_center")) repo.store.cardAlignCenter else true)
    }
    var textMono by remember(repo) { mutableStateOf(repo.store.cardTextMono) }
    var smartWeekend by remember(repo, repo.store.smartHideWeekend) { mutableStateOf(repo.store.smartHideWeekend) }
    // 玻璃材质参数：每次重组直读 prefs（课表效果页改完回来即生效），不 remember 缓存
    val glass = GlassSpec(
        tint = repo.store.glassTint,
        opacity = repo.store.glassOpacity,
        blurPx = repo.store.glassBlurPx,
        refraction = repo.store.glassRefraction,
        edge = repo.store.glassEdge,
        highlightEdge = repo.store.glassHighlightEdge,
    )
    var showDisplaySettings by remember { mutableStateOf(false) }
    var showTermSwitch by remember { mutableStateOf(false) }

    // Switching terms/start dates resets paging and selection even when the parent keeps this screen alive.
    key(calendar?.year, calendar?.semester, calendar?.startDate) {
        val pager = rememberPagerState(initialPage = initialWeek - 1, pageCount = { totalWeeks })
        val scope = rememberCoroutineScope()
        var selected by remember { mutableStateOf<CourseBlock?>(null) }
        var showLesson by remember { mutableStateOf(false) }
        var showWeekPicker by remember { mutableStateOf(false) }
        val displayWeek = pager.currentPage + 1
        var lastCurrentWeek by remember { mutableStateOf(currentWeek) }
        LaunchedEffect(currentWeek, totalWeeks) {
            // Follow midnight/week rollover only when the user was looking at the previous current week.
            if (pager.currentPage + 1 == lastCurrentWeek && currentWeek in 1..totalWeeks) {
                pager.scrollToPage(currentWeek - 1)
            } else if (pager.currentPage >= totalWeeks) {
                pager.scrollToPage(totalWeeks - 1)
            }
            lastCurrentWeek = currentWeek
        }
        val jumpToWeek: (Int) -> Unit = { week ->
            scope.launch { pager.animateScrollToPage((week - 1).coerceIn(0, totalWeeks - 1)) }
        }
        val weekCounts = remember(validBlocks) { validBlocks.groupingBy { it.week }.eachCount() }
        val blocksByWeek = remember(validBlocks) { validBlocks.groupBy { it.week } }
        val ds = LocalDs.current

        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            ScheduleHeader(
                week = displayWeek,
                currentWeek = currentWeek,
                totalWeeks = totalWeeks,
                weekStart = ScheduleTimes.dateAt(termStart, displayWeek, 1),
                onChooseWeek = { showWeekPicker = true },
                onCurrentWeek = { jumpToWeek(currentWeek) },
                onSwitchTerm = { showTermSwitch = true },
                onDisplaySettings = { showDisplaySettings = true },
                onImport = onImport,
                onGlassEffect = onGlassEffect,
            )
            if (syncError != null) {
                Text(
                    "同步失败：$syncError",
                    color = ds.danger,
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 4.dp),
                )
            }
            HorizontalPager(
                state = pager,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                verticalAlignment = Alignment.Top,
                key = { it },
            ) { page ->
                val week = page + 1
                val weekBlocks = blocksByWeek[week].orEmpty()
                WeekGrid(
                    week = week,
                    termStart = termStart,
                    blocks = weekBlocks,
                    allBlocks = validBlocks,
                    now = now,
                    textScale = textScale,
                    showTeacher = showTeacher,
                    showClassroom = showClassroom,
                    rowHeight = rowHeight.dp,
                    cornerRadius = corner.dp,
                    alignCenter = alignCenter,
                    textMono = textMono,
                    smartHideWeekend = smartWeekend,
                    glass = glass,
                    emptyTitle = when {
                        validBlocks.isEmpty() && !fullTermImported -> "还没有课表"
                        fullTermImported -> "第 $week 周暂无课程"
                        else -> "第 $week 周尚未导入"
                    },
                    emptyMessage = if (fullTermImported) "教务当前返回的课表中没有本周排课。调课后可重新导入核对。"
                        else "导入教务学期课表，查看实际排课与完整周次。",
                    onImport = onImport,
                    onLessonClick = { selected = it; showLesson = true },
                )
            }
        }
        WeekPickerDialog(
            show = showWeekPicker,
            selectedWeek = displayWeek,
            currentWeek = currentWeek,
            totalWeeks = totalWeeks,
            weekCounts = weekCounts,
            fullTermImported = fullTermImported,
            onPick = { showWeekPicker = false; jumpToWeek(it) },
            onDismiss = { showWeekPicker = false },
        )
        val selectedBlock = selected
        val lessons = remember(selectedBlock, validBlocks) {
            if (selectedBlock == null) emptyList() else aggregateCellLessons(selectedBlock, validBlocks)
        }
        ScheduleLessonSheet(
            show = showLesson,
            lessons = lessons,
            week = selectedBlock?.week ?: displayWeek,
            allWeeks = totalWeeks,
            onDismiss = { showLesson = false },
            onDismissFinished = { selected = null },
        )
    }
    ScheduleDisplayDialog(
        show = showDisplaySettings,
        textScale = textScale,
        showTeacher = showTeacher,
        showClassroom = showClassroom,
        rowHeight = rowHeight,
        corner = corner,
        alignCenter = alignCenter,
        textMono = textMono,
        smartWeekend = smartWeekend,
        onScale = { textScale = it; repo.store.courseTextScale = it },
        onTeacher = { showTeacher = it; repo.store.showTeacherOnCard = it },
        onClassroom = { showClassroom = it; repo.store.showClassroomOnCard = it },
        onRowHeight = { rowHeight = it; repo.store.courseRowHeight = it },
        onCorner = { corner = it; repo.store.courseCorner = it },
        onAlignCenter = { alignCenter = it; repo.store.cardAlignCenter = it },
        onTextMono = { textMono = it; repo.store.cardTextMono = it },
        onSmartWeekend = { smartWeekend = it; repo.store.smartHideWeekend = it },
        onDismiss = { showDisplaySettings = false },
    )
    ScheduleTermDialog(
        show = showTermSwitch,
        year = calendar?.year.orEmpty(),
        current = calendar?.semester.orEmpty(),
        onPick = { showTermSwitch = false; onSwitchTerm(it) },
        onDismiss = { showTermSwitch = false },
    )
}

@Composable
private fun WeekGrid(
    week: Int,
    termStart: LocalDate?,
    blocks: List<CourseBlock>,
    allBlocks: List<CourseBlock>,
    now: LocalDateTime,
    textScale: Float,
    showTeacher: Boolean,
    showClassroom: Boolean,
    rowHeight: Dp,
    cornerRadius: Dp,
    alignCenter: Boolean,
    textMono: Boolean,
    smartHideWeekend: Boolean,
    glass: GlassSpec,
    emptyTitle: String,
    emptyMessage: String,
    onImport: () -> Unit,
    onLessonClick: (CourseBlock) -> Unit,
) {
    val ds = LocalDs.current
    val today = now.toLocalDate()
    val time = now.toLocalTime()
    val days = remember(blocks, smartHideWeekend) {
        (1..7).filter { !smartHideWeekend || it <= 5 || blocks.any { block -> block.day == it } }
    }
    val dates = remember(termStart, week, days) { days.associateWith { ScheduleTimes.dateAt(termStart, week, it) } }
    // 广科作息只有 10 节；11/12 节按 Nexio 保留刻度，本地没有课就不显示这两行。
    val maxCourseSection = blocks.maxOfOrNull { it.endSection } ?: 0
    val totalSections = maxOf(ScheduleTimes.slots.size, maxCourseSection)
        .coerceAtMost(ScheduleTimes.TOTAL_SECTIONS)
    val gridHeight = remember(rowHeight, totalSections) { ScheduleLayout.sectionY(totalSections, rowHeight) + rowHeight }
    val currentSection = remember(dates, today, time) {
        if (dates.values.any { it == today }) ScheduleTimes.currentSection(time) else null
    }
    val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().height(ScheduleLayout.DateHeight).padding(end = 2.dp)) {
            Box(Modifier.width(ScheduleLayout.SectionWidth).height(ScheduleLayout.DateHeight), contentAlignment = Alignment.Center) {
                Text(dates.values.firstOrNull()?.let { "${it.monthValue}月" }.orEmpty(), fontSize = 10.sp, color = ds.sub)
            }
            days.forEach { day ->
                val date = dates[day]
                val isToday = date == today
                Box(Modifier.weight(1f).height(ScheduleLayout.DateHeight).padding(horizontal = 2.dp), contentAlignment = Alignment.Center) {
                    Column(
                        Modifier.fillMaxSize().background(if (isToday) ds.accentSoft else Color.Transparent, CircleShape),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            ScheduleTimes.dayNames[day - 1],
                            fontSize = 13.sp,
                            lineHeight = 16.sp,
                            fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                            color = if (isToday) ds.accent else ds.text,
                        )
                        if (date != null) {
                            Text("${date.monthValue}/${date.dayOfMonth}", fontSize = 10.sp, lineHeight = 13.sp, color = if (isToday) ds.accent else ds.sub)
                        }
                    }
                }
            }
        }
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())
                .padding(top = 2.dp, bottom = ScheduleLayout.BottomClearance + bottomInset),
        ) {
            if (blocks.isEmpty()) {
                ScheduleEmptyState(emptyTitle, emptyMessage, onImport)
            }
            Box(Modifier.fillMaxWidth().height(gridHeight).padding(end = 2.dp)) {
                val gridColor = ds.text.copy(alpha = if (ds.isDark) 0.055f else 0.035f)
                Box(
                    Modifier.fillMaxSize().padding(start = ScheduleLayout.SectionWidth).drawBehind {
                        val dayWidth = size.width / days.size
                        for (index in 0..days.size) {
                            drawLine(gridColor, Offset(dayWidth * index, 0f), Offset(dayWidth * index, size.height), 0.5.dp.toPx())
                        }
                        for (section in 1..totalSections) {
                            val y = ScheduleLayout.sectionY(section, rowHeight).toPx()
                            drawLine(gridColor, Offset(0f, y), Offset(size.width, y), 0.5.dp.toPx())
                        }
                    },
                )
                Row(Modifier.fillMaxSize()) {
                    ScheduleSectionColumn(totalSections, currentSection, rowHeight, gridHeight)
                    days.forEach { day ->
                        val dayBlocks = remember(blocks, day) { blocks.filter { it.day == day }.sortedBy { it.startSection } }
                        Box(Modifier.weight(1f).height(gridHeight)) {
                            // Keep every real section span. Only exactly matching slots share a card;
                            // partial overlaps get separate lanes rather than extending a representative.
                            val groups = remember(dayBlocks) { groupOverlappingLessons(dayBlocks) }
                            groups.forEach { group ->
                                val lanes = remember(group) { lessonLanes(group) }
                                Row(Modifier.fillMaxSize()) {
                                    lanes.forEach { lane ->
                                        Box(Modifier.weight(1f).height(gridHeight)) {
                                            lane.forEach { slot ->
                                                val block = slot.firstOrNull { ScheduleTimes.isOngoing(it, termStart, now) } ?: slot.first()
                                                val relatedCount = remember(block, allBlocks) { aggregateCellLessons(block, allBlocks).size }
                                                val segments = remember(block.startSection, block.endSection) {
                                                    ScheduleTimes.segments(block.startSection, block.endSection)
                                                }
                                                segments.forEachIndexed { index, segment ->
                                                    LessonCard(
                                                        block = block,
                                                        isPast = slot.all {
                                                            ScheduleTimes.isPast(it.copy(startSection = segment.first, endSection = segment.last), termStart, now)
                                                        },
                                                        isOngoing = slot.any {
                                                            ScheduleTimes.isOngoing(it.copy(startSection = segment.first, endSection = segment.last), termStart, now)
                                                        },
                                                        hasMultipleCourses = index == 0 && relatedCount > 1,
                                                        textScale = textScale,
                                                        showTeacher = showTeacher,
                                                        showClassroom = showClassroom,
                                                        cornerRadius = cornerRadius,
                                                        alignCenter = alignCenter,
                                                        textMono = textMono,
                                                        glass = glass,
                                                        cardHeight = rowHeight * (segment.last - segment.first + 1),
                                                        onClick = { onLessonClick(block) },
                                                        modifier = Modifier.offset(y = ScheduleLayout.sectionY(segment.first, rowHeight)),
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                ScheduleBreakBand("午休", Modifier.offset(y = ScheduleLayout.sectionY(5, rowHeight) - ScheduleLayout.BreakHeight))
                ScheduleBreakBand("晚休", Modifier.offset(y = ScheduleLayout.sectionY(9, rowHeight) - ScheduleLayout.BreakHeight))
            }
        }
    }
}

@Composable
private fun ScheduleSectionColumn(totalSections: Int, currentSection: Int?, rowHeight: Dp, gridHeight: Dp) {
    val ds = LocalDs.current
    Box(Modifier.width(ScheduleLayout.SectionWidth).height(gridHeight)) {
        for (section in 1..totalSections) {
            val current = section == currentSection
            Column(
                Modifier.fillMaxWidth().offset(y = ScheduleLayout.sectionY(section, rowHeight)).height(rowHeight),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    "$section", fontSize = 15.sp, lineHeight = 18.sp,
                    fontWeight = if (current) FontWeight.Medium else FontWeight.Normal,
                    color = if (current) ds.accent else ds.text,
                )
                if (ScheduleTimes.start(section).isNotEmpty()) {
                    val color = if (current) ds.accent else ds.sub
                    Text(ScheduleTimes.start(section), fontSize = 10.sp, lineHeight = 12.sp, color = color)
                    Text(ScheduleTimes.end(section), fontSize = 10.sp, lineHeight = 12.sp, color = color)
                }
            }
        }
    }
}

@Composable
private fun ScheduleBreakBand(text: String, modifier: Modifier) {
    val ds = LocalDs.current
    Box(
        modifier.fillMaxWidth().height(ScheduleLayout.BreakHeight)
            .padding(start = ScheduleLayout.SectionWidth + 2.dp, end = 2.dp, top = 2.dp, bottom = 2.dp)
            .background((if (ds.isDark) Color.White else Color.Black).copy(alpha = if (ds.isDark) 0.06f else 0.04f), RoundedCornerShape(10.dp)),
        contentAlignment = Alignment.Center,
    ) { Text(text, fontSize = 10.sp, color = ds.sub) }
}

@Composable
private fun ScheduleEmptyState(title: String, message: String, onImport: () -> Unit) {
    val ds = LocalDs.current
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 28.dp, vertical = 22.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(MiuixIcons.Backup, contentDescription = null, modifier = Modifier.size(32.dp), tint = ds.sub)
        Text(title, fontSize = 18.sp, fontWeight = FontWeight.Medium, color = ds.text)
        Text(message, fontSize = 12.sp, lineHeight = 17.sp, color = ds.sub, textAlign = TextAlign.Center)
        Button(onClick = onImport, colors = ButtonDefaults.buttonColorsPrimary(), insideMargin = PaddingValues(horizontal = 22.dp, vertical = 10.dp)) {
            Text("导入课表", fontSize = 14.sp)
        }
    }
}

@Composable
private fun LessonCard(
    block: CourseBlock,
    isPast: Boolean,
    isOngoing: Boolean,
    hasMultipleCourses: Boolean,
    textScale: Float,
    showTeacher: Boolean,
    showClassroom: Boolean,
    cornerRadius: Dp,
    alignCenter: Boolean,
    textMono: Boolean,
    glass: GlassSpec,
    cardHeight: Dp,
    onClick: () -> Unit,
    modifier: Modifier,
) {
    val ds = LocalDs.current
    val tint = CourseColors.tint(block.name)
    val inactive = Color(0xFF9E9E9E)
    val pageGlass = LocalPageLayerBackdrop.current
    val onWallpaper = LocalWallpaperActive.current && pageGlass != null
    // 28a99a3 卡片材质：壁纸开启走玻璃路径（着色 + 底色 + 模糊透出壁纸，参数见「课表效果」页），
    // 无壁纸沿用其无材质路径（0.12 着色 + 0.92 页底垫色）
    val tintAlpha = glass.tint * (if (isPast) 0.7f else 1f)
    val baseTint = if (isPast) inactive else tint
    // P3：drawBackdrop、描边光、border、Card 必须用同一个形状；
    // 之前描边光用 ContinuousRoundedRectangle（超椭圆）而卡片是 squircle，四角多出一段不贴合的圆弧白边
    val cardShape = RoundedCornerShape(cornerRadius)
    // P2：两档材质共用 NexioCompat.cardGlassParams() 一个真源（与底栏同档）
    // SOFT=磨砂（更实、无折射），LIQUID=通透（折射 + 高光）
    val liquid = LocalUiStyle.current.value == UiStyle.LIQUID
    val params = com.menglimi.gdust.v2.ui.nexio.utils.AppMaterialSettings.cardGlassParams(
        glass.tint, glass.opacity, glass.blurPx, glass.refraction,
    )
    val effectiveTint = params.tint
    val effectiveOpacity = params.opacity
    val effectiveBlur = params.blurPx
    val effectiveRefraction = params.refraction
    val effectiveGlass = glassSurfaceColor(baseTint, ds.isDark, tintAlpha = effectiveTint, surfaceAlpha = effectiveOpacity)
    val glassColor = glassSurfaceColor(baseTint, ds.isDark, tintAlpha = effectiveTint, surfaceAlpha = effectiveOpacity)
    val cardColor = baseTint.copy(alpha = tintAlpha).compositeOver(ds.bg.copy(alpha = 0.92f))
    val preferredText = if (isPast) ds.sub.copy(alpha = 0.7f)
        else CourseColors.text(block.name, ds.isDark, onWallpaper = onWallpaper, monochrome = textMono)
    val foreground = CourseColors.readableText(
        preferredText,
        if (onWallpaper) effectiveGlass else cardColor.compositeOver(ds.bg),
    )
    val scale = textScale.coerceIn(0.7f, 1.4f)
    val nameSize = (12.7f * scale).sp
    val nameLine = (14.2f * scale).sp
    val infoSize = (11f * scale).sp
    val body = remember(block.name, block.room, block.teacher, showClassroom, showTeacher, nameSize, infoSize) {
        buildAnnotatedString {
            pushStyle(SpanStyle(fontWeight = FontWeight.Bold, fontSize = nameSize))
            append(block.name)
            pop()
            if (showClassroom && block.room.isNotBlank()) {
                append('\n')
                pushStyle(SpanStyle(fontSize = infoSize))
                append("@${block.room}")
                pop()
            }
            if (showTeacher && block.teacher.isNotBlank()) {
                append('\n')
                pushStyle(SpanStyle(fontSize = infoSize))
                append(block.teacher)
                pop()
            }
        }
    }
    val density = LocalDensity.current
    val maxLines = with(density) { ((cardHeight - 16.dp).value / nameLine.toDp().value).toInt().coerceAtLeast(1) }
    Card(
        modifier = modifier.fillMaxWidth().height(cardHeight).padding(ScheduleLayout.CardGap)
            .then(
                if (onWallpaper) Modifier.drawBackdrop(
                    backdrop = pageGlass!!,
                    shape = { cardShape },
                    effects = {
                        blur(effectiveBlur.dp.toPx(), TileMode.Mirror)
                        if (effectiveRefraction > 0f) {
                            runtimeShaderEffect(
                                key = "NexioSoftGlassLiveCourseCard",
                                shaderString = SOFT_GLASS_LIVE_SHADER,
                                uniformShaderName = "content",
                            ) {
                                applySoftGlass(
                                    style = ScheduleSoftGlassStyle.copy(
                                        refractionScale = effectiveRefraction,
                                        // 方形卡片上的 shader 弧光会画出不贴合的大圆弧（P3）；
                                        // 折射保留，弧光只在效果页显式开启「高光描边」时启用
                                        edgeHighlight = false,
                                    ),
                                    width = size.width,
                                    height = size.height,
                                )
                            }
                        }
                    },
                    onDrawSurface = { drawRect(effectiveGlass) },
                )
                    .then(
                        when (glass.edge) {
                            0 -> if (glass.highlightEdge) Modifier.border(1.dp, ds.text.copy(alpha = 0.32f), cardShape) else Modifier
                            2 -> Modifier.edgeLight(
                                shape = cardShape,
                                edgeLight = rememberCardEdgeLight(effectiveGlass).copy(width = 0.18.dp, blurRadius = 0.8.dp, intensity = if (glass.highlightEdge) 0.8f else 0.4f),
                            )
                            3 -> Modifier.edgeLight(
                                shape = cardShape,
                                edgeLight = rememberCardEdgeLight(effectiveGlass).copy(width = 0.9.dp, blurRadius = 2.4.dp, intensity = if (glass.highlightEdge) 1.2f else 0.9f),
                            )
                            else -> Modifier.edgeLight(
                                shape = cardShape,
                                edgeLight = rememberCardEdgeLight(effectiveGlass).copy(intensity = if (glass.highlightEdge) 0.8f else 0.5f),
                            )
                        }
                    ) else Modifier
            )
            .then(if (isOngoing) Modifier.border(1.5.dp, ds.accent, cardShape) else Modifier),
        cornerRadius = cornerRadius,
        insideMargin = PaddingValues(0.dp),
        colors = CardDefaults.defaultColors(
            color = if (onWallpaper) Color.Transparent else cardColor,
            contentColor = foreground,
        ),
        pressFeedbackType = PressFeedbackType.Sink,
        showIndication = true,
        onClick = onClick,
    ) {
        Box(Modifier.fillMaxSize().clipToBounds()) {
            Text(
                body,
                modifier = Modifier.fillMaxWidth()
                    .align(if (alignCenter) Alignment.Center else Alignment.TopStart)
                    .padding(horizontal = 4.dp, vertical = 6.dp)
                    .clearAndSetSemantics {
                        contentDescription = buildString {
                            append(block.name)
                            if (block.room.isNotBlank()) append("，${block.room}")
                            if (block.teacher.isNotBlank()) append("，${block.teacher}")
                            append("，第 ${block.startSection} 至 ${block.endSection} 节")
                            if (isOngoing) append("，进行中") else if (isPast) append("，已结束")
                            if (hasMultipleCourses) append("，包含其他周次或重叠课程，点击查看")
                        }
                    },
                color = foreground,
                lineHeight = nameLine,
                textAlign = if (alignCenter) TextAlign.Center else TextAlign.Start,
                maxLines = maxLines,
                overflow = TextOverflow.Ellipsis,
            )
            if (hasMultipleCourses) {
                Box(Modifier.align(Alignment.BottomEnd).padding(5.dp).size(6.dp).background(foreground, CircleShape))
            }
        }
    }
}
