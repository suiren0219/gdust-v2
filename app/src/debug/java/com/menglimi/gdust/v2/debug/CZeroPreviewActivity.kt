package com.menglimi.gdust.v2.debug

import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.view.MotionEvent
import android.widget.FrameLayout
import android.widget.RemoteViews
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.datastore.preferences.core.emptyPreferences
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.compose
import com.google.gson.Gson
import com.menglimi.gdust.v2.data.CalendarInfo
import com.menglimi.gdust.v2.data.CourseBlock
import com.menglimi.gdust.v2.data.Repository
import com.menglimi.gdust.v2.data.Store
import com.menglimi.gdust.v2.data.Todo
import com.menglimi.gdust.v2.data.TodoKind
import com.menglimi.gdust.v2.ui.CZeroTheme
import com.menglimi.gdust.v2.ui.LocalDs
import com.menglimi.gdust.v2.ui.nexio.NexioBottomBar
import com.menglimi.gdust.v2.ui.schedule.ScheduleScreen
import com.menglimi.gdust.v2.ui.screens.AboutScreen
import com.menglimi.gdust.v2.ui.screens.PowerHistoryScreen
import com.menglimi.gdust.v2.ui.screens.PowerScreen
import com.menglimi.gdust.v2.ui.screens.ProfileScreen
import com.menglimi.gdust.v2.ui.screens.SettingsScreen
import com.menglimi.gdust.v2.ui.screens.TodayScreen
import com.menglimi.gdust.v2.ui.screens.TodoScreen
import com.menglimi.gdust.v2.ui.screens.WidgetPreviewScreen
import com.menglimi.gdust.v2.widget.PowerWidget
import com.menglimi.gdust.v2.widget.ScheduleWidget
import com.menglimi.gdust.v2.widget.TodoWidget
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import java.util.Locale

/** 仅 debug 的本机视觉入口；不借用 App.repo，不启动登录、同步或后台任务。 */
class CZeroPreviewActivity : ComponentActivity() {
    private lateinit var previewContext: Context
    private lateinit var repo: Repository
    private var request by mutableStateOf(PreviewRequest())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        previewContext = PreviewContext(this)
        seedPreview(previewContext, reset = savedInstanceState == null)
        repo = Repository(previewContext)
        request = PreviewRequest.from(intent)
        setContent {
            CompositionLocalProvider(LocalContext provides previewContext) {
                CZeroTheme {
                    key(request) { PreviewPages(repo, request) }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        // adb 热启动也重新装载 fixture；旋转/深浅色重建则保留本次交互。
        seedPreview(previewContext, reset = true)
        repo = Repository(previewContext)
        request = PreviewRequest.from(intent).copy(revision = request.revision + 1)
    }
}

/** Store、Repository 及 WidgetDataSource 的 applicationContext 路径都留在隔离区。 */
private class PreviewContext(base: Context) : ContextWrapper(base) {
    override fun getApplicationContext(): Context = this

    override fun getSharedPreferences(name: String, mode: Int): SharedPreferences =
        super.getSharedPreferences("czero_preview_$name", mode)
}

private val mainPages = listOf("today", "schedule", "power", "todo")
private val previewPages = mainPages + listOf("history", "widgets", "settings", "profile", "about", "widget_render")

private data class PreviewRequest(
    val screen: String = "today",
    val kind: String = "schedule",
    val shape: String = "wide",
    val revision: Int = 0,
) {
    companion object {
        fun from(intent: Intent) = PreviewRequest(
            screen = intent.getStringExtra("screen")?.lowercase(Locale.ROOT)
                ?.takeIf { it in previewPages } ?: "today",
            kind = intent.getStringExtra("kind")?.lowercase(Locale.ROOT)
                ?.takeIf { it in listOf("schedule", "power", "todo") } ?: "schedule",
            shape = intent.getStringExtra("shape")?.lowercase(Locale.ROOT)
                ?.takeIf { it in listOf("wide", "medium", "tall") } ?: "wide",
        )
    }
}

@Composable
private fun PreviewPages(repo: Repository, request: PreviewRequest) {
    val context = LocalContext.current
    val store = repo.store
    var screen by rememberSaveable { mutableStateOf(request.screen) }
    var tab by rememberSaveable { mutableStateOf(mainPages.indexOf(request.screen).coerceAtLeast(0)) }
    val openSection = rememberSaveable { mutableStateOf<String?>(null) }
    val calendar = remember(repo) { store.loadCalendar() }
    val blocks = remember(repo) { store.loadBlocks() }
    val offlineAction: () -> Unit = {
        Toast.makeText(context, "此入口仅验收本机页面", Toast.LENGTH_SHORT).show()
    }
    val back: () -> Unit = {
        screen = when (screen) {
            "history" -> "power"
            "settings" -> mainPages[tab]
            else -> "settings"
        }
        mainPages.indexOf(screen).takeIf { it >= 0 }?.let { tab = it }
    }
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        if (uri != null) {
            val result = runCatching {
                checkNotNull(context.contentResolver.openOutputStream(uri)) { "无法打开文件" }
                    .bufferedWriter().use { it.write(store.exportBackup()) }
            }
            Toast.makeText(context, if (result.isSuccess) "已导出本机预览数据" else "导出失败", Toast.LENGTH_SHORT).show()
        }
    }

    BackHandler(enabled = screen !in mainPages, onBack = back)
    Box(Modifier.fillMaxSize().background(LocalDs.current.bg)) {
        key(screen) {
            when (screen) {
                "today" -> TodayScreen(
                    calendar = calendar,
                    blocks = blocks,
                    onOpenNotices = offlineAction,
                    onOpenSchedule = { tab = 1; screen = "schedule" },
                    onImport = offlineAction,
                    hasImportedSchedule = true,
                )
                "schedule" -> ScheduleScreen(
                    repo = repo,
                    calendar = calendar,
                    blocks = blocks,
                    syncError = null,
                    onPrevWeek = {},
                    onNextWeek = {},
                    onSwitchTerm = { offlineAction() },
                    onImport = offlineAction,
                )
                "power" -> PowerScreen(repo)
                "todo" -> TodoScreen(repo)
                "history" -> PowerHistoryScreen(repo, onBack = back)
                "widgets" -> WidgetPreviewScreen(onBack = back)
                "profile" -> ProfileScreen(repo, onBack = back)
                "about" -> AboutScreen(repo, onBack = back)
                "widget_render" -> RealWidgetPreview(request.kind, request.shape)
                "settings" -> SettingsScreen(
                    repo = repo,
                    syncError = null,
                    onSync = offlineAction,
                    onWebImport = offlineAction,
                    onReminder = offlineAction,
                    onTodoReminder = offlineAction,
                    onChxNotifications = offlineAction,
                    onChxEntry = offlineAction,
                    onProfile = { screen = "profile" },
                    onAbout = { screen = "about" },
                    onExportBackup = exportLauncher,
                    // 不把外部备份中的水电绑定或会话带进视觉 fixture。
                    onImportBackup = { offlineAction() },
                    onLogout = offlineAction,
                    onBack = back,
                    loggedIn = false,
                    chxSessionSaved = false,
                    openSection = openSection,
                    onGrades = offlineAction,
                    onChxCourses = offlineAction,
                    onSchoolNotices = offlineAction,
                    onFiveEducation = offlineAction,
                )
            }
        }
        if (screen in mainPages) {
            NexioBottomBar(
                selectedTab = tab,
                onTabSelected = { tab = it; screen = mainPages[it] },
                onSettings = { screen = "settings" },
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
}

/** 直接走生产 Glance 布局，默认假 widget ID + 内存 state，不申请 launcher 绑定。 */
@Composable
private fun RealWidgetPreview(kind: String, shape: String) {
    val context = LocalContext.current
    val size = remember(shape) {
        when (shape) {
            "medium" -> DpSize(180.dp, 180.dp)
            "tall" -> DpSize(96.dp, 192.dp)
            else -> DpSize(340.dp, 170.dp)
        }
    }
    var remoteViews by remember(context, kind, size) { mutableStateOf<RemoteViews?>(null) }
    LaunchedEffect(context, kind, size) {
        val widget: GlanceAppWidget = when (kind) {
            "power" -> PowerWidget()
            "todo" -> TodoWidget()
            else -> ScheduleWidget()
        }
        remoteViews = widget.compose(context = context, size = size, state = emptyPreferences())
    }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        remoteViews?.let { views ->
            AndroidView(
                modifier = Modifier.size(size.width, size.height),
                factory = { isolated ->
                    // RemoteViews 中的生产 PendingIntent 不能发给原应用或 widget receiver。
                    object : FrameLayout(isolated) {
                        override fun onInterceptTouchEvent(event: MotionEvent): Boolean = true
                        override fun onTouchEvent(event: MotionEvent): Boolean = true
                    }.apply {
                        addView(views.apply(isolated, this), FrameLayout.LayoutParams(
                            FrameLayout.LayoutParams.MATCH_PARENT,
                            FrameLayout.LayoutParams.MATCH_PARENT,
                        ))
                    }
                },
            )
        }
    }
}

/** 每次显式启动重置 fixture；日期跨周仍正确，绝不读写真实缓存或写任何安全凭据。 */
private fun seedPreview(context: Context, reset: Boolean) {
    val today = LocalDate.now()
    val cache = context.getSharedPreferences("gdust_cache", Context.MODE_PRIVATE)
    if (!reset && cache.getString("czero_seed_date", null) == today.toString()) return
    val monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    val termStart = monday.minusWeeks(7)
    val calendar = CalendarInfo(
        week = 8,
        allWeek = 20,
        startDate = termStart.toString(),
        year = today.year.toString(),
        semester = if (today.monthValue >= 8) "3" else "12",
    )
    val shortNames = listOf("高等数学", "大学英语", "数据结构", "体育", "线性代数")
    val dates = ((0L..4L).map(monday::plusDays) + today + today.plusDays(1)).distinct().sorted()
    val blocks = dates.flatMap { date ->
        val week = (ChronoUnit.DAYS.between(termStart, date) / 7).toInt() + 1
        listOf(
            CourseBlock(week, date.dayOfWeek.value, 1, 2, shortNames[(date.dayOfWeek.value - 1) % shortNames.size],
                "博学楼 A203", "陈老师", date.toString()),
            CourseBlock(week, date.dayOfWeek.value, 5, 7, "面向对象程序设计与软件工程综合实践（项目实验）",
                "信息工程实验中心 B502", "林老师", date.toString()),
            CourseBlock(week, date.dayOfWeek.value, 9, 10, "自习", "图书馆 302", "", date.toString()),
        )
    }
    val zone = ZoneId.systemDefault()
    fun due(days: Long, hour: Int) = today.plusDays(days).atTime(hour, 0).atZone(zone).toInstant().toEpochMilli()
    val todos = listOf(
        Todo(101, "完成第三章习题并整理错题", false, TodoKind.HOMEWORK, "高等数学", due(-1, 20)),
        Todo(102, "提交小组项目设计报告与原型，检查长标题换行和截止时间展示", false,
            TodoKind.HOMEWORK, "面向对象程序设计与软件工程综合实践", due(0, 23)),
        Todo(103, "英语听力测验", false, TodoKind.EXAM, "大学英语", due(0, 19)),
        Todo(104, "复习树与图，完成算法复杂度分析", false, TodoKind.HOMEWORK, "数据结构", due(1, 18)),
        Todo(105, "归还图书", false),
        Todo(106, "提交实验一报告", true, TodoKind.HOMEWORK, "程序设计", due(-2, 18)),
        Todo(107, "整理本周笔记", true, dueAt = due(0, 12)),
    )
    // 30 个本地自然日的逐小时采样，包含充值上跳；最后一个点固定为 46.8 度。
    val now = System.currentTimeMillis()
    val firstSample = today.minusDays(29).atStartOfDay(zone).toInstant().toEpochMilli()
    val times = generateSequence(firstSample) { it + 3_600_000L }.takeWhile { it < now }.toList() + now
    val rechargeAt = today.minusDays(9).atTime(12, 0).atZone(zone).toInstant().toEpochMilli()
    val samples = times.map { time ->
        val elapsedHours = (now - time) / 3_600_000.0
        val odd = 46.8 + elapsedHours * 0.13 - if (time < rechargeAt) 40.0 else 0.0
        Store.Sample(time, kotlin.math.round(odd * 10.0) / 10.0)
    }
    // 清理的对象已经过 PreviewContext 加前缀；安全存储从不 seed/clear。
    cache.edit().clear()
        .putString("czero_seed_date", today.toString())
        .putString("samples", Gson().toJson(samples))
        .putBoolean("auto_sync", false)
        .putBoolean("reminder_enabled", false)
        .putBoolean("todo_reminder_enabled", false)
        .putFloat("warn_th", 20f)
        .putInt("widget_power_range", 7)
        .apply()
    Store(context).apply {
        saveCalendar(calendar)
        saveBlocks(blocks, source = "portal_week")
        updateTodos { todos }
    }
    context.getSharedPreferences("gdust_power", Context.MODE_PRIVATE).edit().clear()
        .putFloat("prev_odd", 46.8f)
        .putString("name", "松山湖校区 · 6 栋 502")
        .putLong("last_sample", now)
        .apply()
}
