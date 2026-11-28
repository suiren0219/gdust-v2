package com.menglimi.gdust.v2.ui.screens

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import com.haooz.nexioui.text.NexioText as Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.menglimi.gdust.v2.data.Repository
import com.menglimi.gdust.v2.ui.AiSparkleIcon
import com.menglimi.gdust.v2.ui.AppCard
import com.menglimi.gdust.v2.ui.AppUiTokens
import com.menglimi.gdust.v2.ui.LocalDs
import com.menglimi.gdust.v2.ui.LocalWallpaperState
import com.menglimi.gdust.v2.ui.WallpaperState
import com.menglimi.gdust.v2.ui.pageBackground
import androidx.compose.material3.Icon
import com.haooz.nexioui.arrowpreference.ArrowPreference
import com.haooz.nexioui.basiccomponent.BasicComponent
import com.haooz.nexioui.basiccomponent.BasicComponentDefaults
import com.haooz.nexioui.button.FlatIconButton
import com.haooz.nexioui.button.FlatTextButton
import com.haooz.nexioui.cardexpand.CardExpand
import com.haooz.nexioui.icon.NexioIcons
import com.haooz.nexioui.icon.extended.Alarm
import com.haooz.nexioui.icon.extended.Background
import com.haooz.nexioui.icon.extended.Backup
import com.haooz.nexioui.icon.extended.BankCards
import com.haooz.nexioui.icon.extended.Close
import com.haooz.nexioui.icon.extended.ContactsCircle
import com.haooz.nexioui.icon.extended.ConvertFile
import com.haooz.nexioui.icon.extended.Home
import com.haooz.nexioui.icon.extended.Import
import com.haooz.nexioui.icon.extended.Info
import com.haooz.nexioui.icon.extended.Link
import com.haooz.nexioui.icon.extended.Messages
import com.haooz.nexioui.icon.extended.Months
import com.haooz.nexioui.icon.extended.Notes
import com.haooz.nexioui.icon.extended.Refresh
import com.haooz.nexioui.icon.extended.Search
import com.haooz.nexioui.icon.extended.Theme
import com.haooz.nexioui.icon.extended.Timer
import com.haooz.nexioui.icon.extended.Unlock
import com.haooz.nexioui.icon.extended.Weeks
import com.haooz.nexioui.overscroll.overScrollVertical
import com.haooz.nexioui.scrollhaptic.scrollEndHaptic
import com.haooz.nexioui.slider.Slider
import com.haooz.nexioui.switchpreference.SwitchPreference
import com.menglimi.gdust.v2.ui.AppMaterialStyle
import com.menglimi.gdust.v2.ui.NexioPageScaffold
import com.menglimi.gdust.v2.ui.applyNexioMaterial

/**
 * Adapted from NexioSchedule, revision 28a99a3:
 * ui-shared/.../ui/screens/SettingsScreen.kt (SettingsEntryRow / SettingsEntryIcon),
 * SettingsSearch.kt and ui/activities/PreferenceSettingsScreen.kt.
 * Retains the grouped Miuix layout and icon geometry; actions use GDUST's local repository.
 */
@Composable
fun SettingsScreen(
    repo: Repository,
    syncError: String?,
    onSync: () -> Unit,
    onWebImport: () -> Unit,
    onReminder: () -> Unit,
    onTodoReminder: () -> Unit = {},
    onChxNotifications: () -> Unit,
    onChxEntry: () -> Unit,
    onProfile: () -> Unit,
    onAbout: () -> Unit,
    onExportBackup: ManagedActivityResultLauncher<String, Uri?>,
    onImportBackup: (Uri) -> Unit,
    onLogout: () -> Unit,
    onBack: () -> Unit,
    loggedIn: Boolean = true,
    chxSessionSaved: Boolean = false,
    openSection: MutableState<String?>,
    openGroupState: MutableState<String?> = rememberSaveable { mutableStateOf(null) },
    syncing: Boolean = false,
    onOpenAiService: () -> Unit = {},
    onGrades: () -> Unit = {},
    onChxCourses: () -> Unit = {},
    onSchoolNotices: () -> Unit = {},
    onFiveEducation: () -> Unit = {},
    onReconnectService: ((com.menglimi.gdust.v2.data.SchoolService) -> Unit)? = null,
    offlinePreview: Boolean = false,
    onOpenMcp: () -> Unit = {},
    category: MutableState<String?> = openGroupState,
    onSchedules: () -> Unit = {},
    onShareSchedule: () -> Unit = onOpenMcp,
) {
    val ds = LocalDs.current
    val preview = offlinePreview || com.menglimi.gdust.v2.BuildConfig.PREVIEW_TARGET
    val wallpaperState = LocalWallpaperState.current
    val secondary = ds.sub
    val store = repo.store
    val focusManager = LocalFocusManager.current
    val context = androidx.compose.ui.platform.LocalContext.current
    var query by rememberSaveable { mutableStateOf("") }
    var subPage by category

    var thresholdText by remember { mutableStateOf(store.warnThreshold.toInt().toString()) }
    var dropText by remember { mutableStateOf(store.warnDropRate.toInt().toString()) }
    var thresholdSaved by remember { mutableStateOf(false) }
    var warnThresholdLocal by remember { mutableStateOf(store.warnThreshold) }
    var termStart by rememberSaveable { mutableStateOf(store.termStartDate) }
    var termStartLocal by remember { mutableStateOf(store.termStartDate) }
    // 普通 prefs 不会触发重组，选中态必须用本地镜像并同步写回。
    var startTabLocal by remember { mutableStateOf(store.startTab) }
    var materialStyleLocal by remember { mutableStateOf(AppMaterialStyle.fromKey(store.uiStyle)) }
    var sampleIntervalLocal by remember { mutableStateOf(store.sampleIntervalMinutes) }
    var powerPresetLocal by remember { mutableStateOf(store.powerQueryPreset) }
    var smartWeekendLocal by remember { mutableStateOf(store.smartHideWeekend) }
    var autoSyncLocal by remember { mutableStateOf(store.autoSyncEnabled) }
    // 教务夜间关闭：开关与 HH:mm 时段（prefs 不触发重组，用本地镜像）。
    var maintenanceLocal by remember { mutableStateOf(store.maintenanceReminderEnabled) }
    var maintStartText by remember { mutableStateOf(com.menglimi.gdust.v2.data.MaintenanceWindow.format(store.maintenanceStartMin)) }
    var maintEndText by remember { mutableStateOf(com.menglimi.gdust.v2.data.MaintenanceWindow.format(store.maintenanceEndMin)) }
    var maintSaved by remember { mutableStateOf(false) }
    var maintError by remember { mutableStateOf<String?>(null) }
    var maintWindowLocal by remember { mutableStateOf("${maintStartText}–${maintEndText}") }
    var termWeeksText by remember { mutableStateOf(store.termWeeks.takeIf { it > 0 }?.toString() ?: "") }
    var termWeekText by remember { mutableStateOf(store.termCurrentWeek.takeIf { it > 0 }?.toString() ?: "") }
    var termSaved by remember { mutableStateOf(false) }
    var termError by remember { mutableStateOf<String?>(null) }
    var showWidgetPicker by rememberSaveable { mutableStateOf(false) }
    var showDesktopAppearance by rememberSaveable { mutableStateOf(false) }

    // 导入与恢复后的状态重载统一由宿主处理，不能在本页直接写 Store。
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(onImportBackup)
    }

    fun toggle(section: String) {
        focusManager.clearFocus()
        openSection.value = if (openSection.value == section) null else section
    }

    val accountEntry = SettingsEntry(
        id = "profile", title = "个人信息", summary = "广东科技学院 · 本机数据与偏好",
        keywords = "账户 账号 学校 学号 姓名 登录 验证 隐私", icon = NexioIcons.ContactsCircle,
    )
    val originalGroups = listOf(
        SettingsSection("通用", listOf(
            SettingsEntry(
                id = "startTab", title = "启动页", summary = "下次打开时默认显示", icon = NexioIcons.Home,
                detail = when (startTabLocal) { 0 -> "今日"; 2 -> "电费"; 3 -> "待办"; else -> "课表" },
                onClick = { toggle("startTab") },
                editor = {
                    SegmentedChoice(
                        // 持久化索引与原有四个业务页保持一致。
                        options = listOf(0 to "今日", 1 to "课表", 2 to "电费", 3 to "待办"),
                        selected = startTabLocal,
                        onSelect = { startTabLocal = it; store.startTab = it },
                    )
                },
            ),
            SettingsEntry(
                id = "desktop", title = "桌面名称与图标", summary = "自定义桌面入口的名称和图片", icon = NexioIcons.Home,
                keywords = "桌面 图标 名称 自定义 快捷方式", onClick = { showDesktopAppearance = true },
            ),
            SettingsEntry(
                id = "widgets", title = "桌面小部件", summary = "课表 / 电量 / 待办，各三款尺寸",
                icon = NexioIcons.Months,
                keywords = "小组件 widget 添加 电量 待办 课表",
                onClick = { showWidgetPicker = true },
            ),
            SettingsEntry(
                id = "material", title = "玻璃材质效果", summary = materialStyleLocal.summary,
                icon = NexioIcons.Theme, detail = materialStyleLocal.label,
                keywords = "材质 玻璃 柔光 液态 磨砂 纯色 SoftGlass 性能",
                onClick = { toggle("material") },
                editor = {
                    SegmentedChoice(
                        options = AppMaterialStyle.entries.map { it to it.label.removeSuffix("玻璃").removePrefix("纯色") },
                        selected = materialStyleLocal,
                        onSelect = {
                            materialStyleLocal = it
                            store.uiStyle = it.key
                            applyNexioMaterial(it.key)
                        },
                    )
                    Text(materialStyleLocal.summary, fontSize = 13.sp, color = secondary)
                },
            ),
            SettingsEntry(
                id = "wallpaper", title = "自定义壁纸", summary = "设置首页与课表背景，调节模糊和压暗",
                icon = NexioIcons.Background, keywords = "壁纸 背景 模糊 磨砂 透明度 压暗",
                onClick = { toggle("wallpaper") },
                editor = { WallpaperAppearanceEditor(state = wallpaperState) },
            ),
        )),
        SettingsSection("课表", listOf(
            SettingsEntry(
                id = "term", title = "学期设置", summary = if (store.isIndependentSchedule) "独立课表沿用导入的学期，请切回学校课表再修改"
                    else "开学日期、总周数与当前周", icon = NexioIcons.Months, enabled = !store.isIndependentSchedule,
                keywords = "校历 开学日期 总周数 当前周", detail = termStartLocal.ifBlank { "跟随校历" },
                onClick = { toggle("term") },
                editor = {
                    TermDateField(
                        value = termStart,
                        onValueChange = { termStart = it; termSaved = false; termError = null },
                        fallback = store.loadCalendar()?.startDate.orEmpty(),
                        allowFollowCalendar = true,
                    )
                    NexioField(termWeeksText, { termWeeksText = it; termSaved = false; termError = null }, "学期总周数", KeyboardType.Number)
                    NexioField(termWeekText, { termWeekText = it; termSaved = false; termError = null }, "当前第几周", KeyboardType.Number)
                    NexioActionButton(if (termSaved) "已保存，重新同步生效" else "保存学期设置") {
                        val total = if (termWeeksText.isBlank()) 0 else termWeeksText.toIntOrNull()
                        val current = if (termWeekText.isBlank()) 0 else termWeekText.toIntOrNull()
                        termError = when {
                            !DateWheelSelection.validTermStart(termStart) -> "请选择第一周的周一日期"
                            total == null || (termWeeksText.isNotBlank() && total !in 1..60) -> "学期总周数须为 1–60，留空跟随校历"
                            current == null || (termWeekText.isNotBlank() && current !in 1..60) -> "当前周须为 1–60，留空自动计算"
                            total > 0 && current > total -> "当前周不能超过学期总周数"
                            else -> null
                        }
                        if (termError == null) {
                            store.termStartDate = termStart.trim()
                            termStartLocal = store.termStartDate
                            store.termWeeks = total ?: 0
                            store.termCurrentWeek = current ?: 0
                            termSaved = true
                        }
                    }
                    termError?.let { Text(it, fontSize = 13.sp, color = ds.danger) }
                    Text("日期与周数可跟随校历；课程仍需从教务导入，未同步周次不代表无课。", fontSize = 13.sp, color = secondary)
                },
            ),
            SettingsEntry(
                id = "weekend", title = "智能显示周末", summary = "隐藏没有课程的周六、周日", icon = NexioIcons.Weeks,
                checked = smartWeekendLocal,
                onCheckedChange = { smartWeekendLocal = it; store.smartHideWeekend = it },
            ),
        )),
        SettingsSection("提醒", listOf(
            SettingsEntry(
                id = "reminder", title = "课程提醒", summary = "课前、次日与课中提醒", icon = NexioIcons.Alarm,
                detail = if (store.reminderEnabled) "已开启" else "未开启", onClick = onReminder,
            ),
            SettingsEntry(
                id = "todoReminder", title = "待办提醒", summary = "作业、考试与自定义事项的到期提醒", icon = NexioIcons.Alarm,
                detail = if (store.todoReminderEnabled) "已开启" else "未开启", onClick = onTodoReminder,
            ),
        )),
        SettingsSection("同步", listOf(
            SettingsEntry(
                id = "sync", title = "账号直连同步", summary = "使用当前登录账号读取教务课表", icon = NexioIcons.Refresh,
                detail = when {
                    syncing -> "同步中"
                    syncError != null -> "失败"
                    store.lastSyncAt > 0 -> "已同步"
                    else -> "未同步"
                },
                enabled = !preview && !syncing, onClick = onSync,
            ),
            SettingsEntry(
                id = "webImport", title = "网页登录导入", summary = "在教务网页导入学期课程", icon = NexioIcons.Import,
                keywords = "兼容备用 教务学期导入", detail = if (store.loadWebImportPage().isNotBlank()) "已保存会话" else null,
                enabled = !preview, onClick = onWebImport,
            ),
            SettingsEntry(
                id = "autoSync", title = "自动同步课表", summary = "每 4 小时在本机更新课表", icon = NexioIcons.Refresh,
                checked = autoSyncLocal, enabled = !preview,
                onCheckedChange = {
                    autoSyncLocal = it
                    store.autoSyncEnabled = it
                    if (it) com.menglimi.gdust.v2.work.ScheduleSyncWorker.schedule(context)
                    else com.menglimi.gdust.v2.work.ScheduleSyncWorker.cancel(context)
                },
            ),
            SettingsEntry(
                id = "maintenanceReminder", title = "教务维护提醒", summary = "同步失败时参考设定时段",
                icon = NexioIcons.Timer,
                checked = maintenanceLocal,
                onCheckedChange = { maintenanceLocal = it; store.maintenanceReminderEnabled = it },
            ),
            SettingsEntry(
                id = "maintenance", title = "维护时段", icon = NexioIcons.Timer,
                summary = if (maintenanceLocal) "可跨午夜" else "提醒已关闭，仍可编辑时段",
                detail = maintWindowLocal,
                onClick = { toggle("maintenance") },
                editor = {
                    NexioField(maintStartText, { maintStartText = it; maintSaved = false; maintError = null }, "开始时间（HH:mm）")
                    NexioField(maintEndText, { maintEndText = it; maintSaved = false; maintError = null }, "结束时间（HH:mm）")
                    NexioActionButton(if (maintSaved) "已保存" else "保存时段") {
                        val start = com.menglimi.gdust.v2.data.MaintenanceWindow.parse(maintStartText)
                        val end = com.menglimi.gdust.v2.data.MaintenanceWindow.parse(maintEndText)
                        maintError = when {
                            start == null || end == null -> "请输入有效时间，如 23:00 或 06:30"
                            start == end -> "开始时间与结束时间不能相同"
                            else -> null
                        }
                        if (maintError == null && start != null && end != null) {
                            store.maintenanceStartMin = start
                            store.maintenanceEndMin = end
                            maintStartText = com.menglimi.gdust.v2.data.MaintenanceWindow.format(start)
                            maintEndText = com.menglimi.gdust.v2.data.MaintenanceWindow.format(end)
                            maintWindowLocal = "${maintStartText}–${maintEndText}"
                            maintSaved = true
                        }
                    }
                    maintError?.let { Text(it, fontSize = 13.sp, color = ds.danger) }
                    Text("仅用于解释课表、成绩同步失败，不代表教务实际停服；学习通与电量查询不受影响。", fontSize = 13.sp, color = secondary)
                },
            ),
        )),
        SettingsSection("电量", listOf(
            SettingsEntry(
                id = "interval", title = "电量查询预设", summary = "按宿舍余量调整查询节奏", icon = NexioIcons.Timer,
                detail = powerPresetLocal.title, onClick = { toggle("interval") },
                editor = {
                    val presets = com.menglimi.gdust.v2.data.PowerQueryPreset.entries
                    SegmentedChoice(
                        options = presets.map { it to it.title }, selected = powerPresetLocal,
                        onSelect = { powerPresetLocal = it; store.powerQueryPreset = it },
                    )
                    if (powerPresetLocal == com.menglimi.gdust.v2.data.PowerQueryPreset.CUSTOM) {
                        SegmentedChoice(
                            options = listOf(15, 30, 60, 120).map { it to "${it}分" }, selected = sampleIntervalLocal,
                            onSelect = { sampleIntervalLocal = it; store.sampleIntervalMinutes = it },
                        )
                    }
                    Text(when (powerPresetLocal) {
                        com.menglimi.gdust.v2.data.PowerQueryPreset.MANUAL -> "仅点按查询时更新，停止后台电量任务。"
                        com.menglimi.gdust.v2.data.PowerQueryPreset.BALANCED -> "通常每 2 小时查询；余量偏低或预计不足两天时每小时查询。"
                        com.menglimi.gdust.v2.data.PowerQueryPreset.LOW_POWER -> "通常每小时查询；余量偏低或预计不足两天时每 30 分钟查询。"
                        com.menglimi.gdust.v2.data.PowerQueryPreset.CUSTOM -> "按自定间隔采样。后台仅在日间时段查询，系统省电策略可能延后执行。"
                    }, fontSize = 13.sp, color = secondary)
                },
            ),
            SettingsEntry(
                id = "warn", title = "低电量预警", summary = "剩余电量阈值与掉电速率", icon = NexioIcons.BankCards,
                detail = "${warnThresholdLocal.toInt()} 度", onClick = { toggle("warn") },
                editor = {
                    NexioField(thresholdText, { thresholdText = it; thresholdSaved = false }, "低电量阈值（度）", KeyboardType.Decimal)
                    NexioField(dropText, { dropText = it; thresholdSaved = false }, "掉电速率（度/小时）", KeyboardType.Decimal)
                    NexioActionButton(if (thresholdSaved) "已保存" else "保存阈值") {
                        thresholdText.toFloatOrNull()?.let { store.warnThreshold = it; warnThresholdLocal = it }
                        dropText.toFloatOrNull()?.let { store.warnDropRate = it }
                        thresholdSaved = true
                    }
                    Text("按电量查询预设执行，同类预警间隔 12 小时；手动模式只在查询时检查。", fontSize = 13.sp, color = secondary)
                },
            ),
        )),
        SettingsSection("数据", listOf(
            SettingsEntry(
                id = "cachedCourses", title = "本地缓存课程", icon = NexioIcons.Months,
                detail = "${store.loadBlocks().size} 张卡",
            ),
            SettingsEntry(
                id = "calendar", title = "校历", icon = NexioIcons.Weeks,
                detail = store.loadCalendar()?.let { "第 ${it.week}/${it.allWeek} 周" } ?: "未同步",
            ),
            SettingsEntry(
                id = "export", title = "导出备份", summary = "课表、待办、偏好与水电绑定", icon = NexioIcons.Backup,
                keywords = "JSON 不含登录凭证 密码",
                onClick = { onExportBackup.launch("gdust-backup-${java.time.LocalDate.now()}.json") },
            ),
            SettingsEntry(
                id = "import", title = "导入备份", summary = "从 JSON 恢复，将覆盖现有数据与偏好", icon = NexioIcons.ConvertFile,
                onClick = { importLauncher.launch(arrayOf("application/json", "text/*")) },
            ),
        )),
        SettingsSection("服务", listOf(
            SettingsEntry(
                id = "grades", title = "学期成绩", summary = "按学期查询成绩、学分与绩点", icon = NexioIcons.Notes,
                keywords = "教务 成绩 绩点 GPA", onClick = onGrades,
            ),
            SettingsEntry(
                id = "schoolNotices", title = "学校通知", summary = "门户通知、教务处公告与官网新闻", icon = NexioIcons.Messages,
                keywords = "通知 公告 教务 新闻 考试", onClick = onSchoolNotices,
            ),
            SettingsEntry(
                id = "chx", title = "学习通", summary = "课程、作业与通知待办", icon = NexioIcons.Link,
                keywords = "超星 课程 任务 作业 通知 登录 联动", onClick = onChxEntry,
            ),
            SettingsEntry(
                id = "fiveEducation", title = "五育综合数据平台", summary = "打开校内网页查看活动与学期记录", icon = NexioIcons.Link,
                detail = "网页", keywords = "活动 五育 综合评价", onClick = onFiveEducation,
            ),
        )),
        SettingsSection("AI", listOf(
            SettingsEntry(
                id = "summary", title = "生成公告总结", summary = "查看公告并按需生成或读取本机摘要",
                keywords = "AI 缓存 总结", icon = NexioIcons.Notes, enabled = !preview, onClick = onSchoolNotices,
            ),
            SettingsEntry(
                id = "llm", title = "AI 服务", summary = "模型连接、预设与测试", keywords = "AI 模型 地址 密钥 公告 总结", icon = AiSparkleIcon,
                detail = if (preview) "离线预览" else if (store.hasLlm) "已配置" else "未配置",
                enabled = !preview, onClick = onOpenAiService,
            ),
            SettingsEntry(
                id = "mcp", title = "AI 与 MCP", summary = "本机只读服务 · 可选个人课表云", icon = AiSparkleIcon,
                keywords = "AI MCP 本地服务 云端 同步课表", enabled = !preview, onClick = onOpenMcp,
            ),
        )),
        SettingsSection("关于", listOf(
            SettingsEntry(
                id = "about", title = "关于与更新", summary = "版本、项目主页与数据来源", keywords = "GitHub Star 开源 支持", icon = NexioIcons.Info,
                onClick = onAbout,
            ),
        )),
    )
    val allEntries = (originalGroups.flatMap { it.entries } + accountEntry.copy(onClick = onProfile) + listOf(
        SettingsEntry("chxNotifications", "学习通通知待办", NexioIcons.Messages, summary = "授权本机通知读取", onClick = onChxNotifications),
        SettingsEntry("schedules", "课表切换", NexioIcons.Months, summary = "学校课表与独立云端课表", enabled = !preview, onClick = onSchedules),
        SettingsEntry("shareSchedule", "分享当前课表", NexioIcons.Link, summary = "导出独立课表供其他设备导入", enabled = !preview, onClick = onShareSchedule),
    )).map { entry ->
        if (preview) entry.copy(enabled = false, onClick = {}, onCheckedChange = {}, editor = null) else entry
    }
    val groups = SettingsNavigation.categories.map { c ->
        val categoryEntries = if (c.title == "外观与数据") c.entries + listOf("material", "wallpaper") else c.entries
        SettingsSection(c.title, allEntries.filter { it.id in categoryEntries })
    }
    val search = query.trim()
    val isDirectory = subPage == null && search.isBlank()
    val sourceGroups = if (subPage == null) groups else groups.filter { it.title == subPage }
    val visibleGroups = sourceGroups.mapNotNull { group ->
        group.copy(entries = group.entries.filter { it.matches(search, group.title) })
            .takeIf { it.entries.isNotEmpty() }
    }
    val showServiceStatus = SettingsNavigation.showServiceStatus(subPage, search)
    val showLogout = !preview && subPage == null && loggedIn &&
        (search.isBlank() || "退出登录 清除本机数据 账户".contains(search, ignoreCase = true))
    val scrollState = key(subPage) { rememberScrollState() }
    val backAction = {
        when (SettingsNavigation.back(subPage, query)) {
            SettingsBack.CLEAR_SEARCH -> { query = ""; focusManager.clearFocus() }
            SettingsBack.DIRECTORY -> { subPage = null; openSection.value = null }
            SettingsBack.EXIT -> onBack()
        }
    }
    BackHandler { backAction() }

    Box(Modifier.fillMaxSize()) {
        NexioPageScaffold(
            title = subPage ?: "设置",
            onBack = backAction,
            modifier = Modifier.settingsEdgeBack { backAction() },
        ) { scaffoldPadding, _, _ ->
            Column(
                Modifier
                    .fillMaxSize()
                    .imePadding()
                    .overScrollVertical()
                    .scrollEndHaptic()
                    .verticalScroll(scrollState)
                    .padding(horizontal = nexioPagePadding())
                    .padding(top = scaffoldPadding.calculateTopPadding(), bottom = scaffoldPadding.calculateBottomPadding()),
            ) {
                SettingsSearchField(query = query, onQueryChange = { query = it })
                if (showServiceStatus) {
                    ServiceStatusCard(
                        repo,
                        onReconnect = { service -> onReconnectService?.invoke(service) },
                        actionsEnabled = !preview,
                        offlinePreview = preview,
                    )
                }
                if (isDirectory) {
                    SettingGroup {
                        groups.forEachIndexed { index, group ->
                            if (index > 0) SettingDivider()
                            val destination = group.title
                            key(destination) {
                                SettingsPreference(SettingsEntry(
                                    id = "category-$destination", title = destination,
                                    summary = SettingsNavigation.categories.single { it.title == destination }.summary,
                                    icon = group.entries.first().icon,
                                    onClick = { focusManager.clearFocus(); subPage = destination; query = ""; openSection.value = null },
                                ))
                            }
                        }
                    }
                } else {
                    visibleGroups.forEach { group ->
                        key(group.title) {
                            if (subPage == null) SectionHeader(group.title)
                            SettingGroup {
                                group.entries.forEachIndexed { index, entry ->
                                    if (index > 0) SettingDivider()
                                    key(entry.id) { SettingsGroupEntryWithEditor(entry, openSection, syncError) }
                                }
                            }
                        }
                    }
                }
                if (showLogout) {
                    SettingGroup {
                        SettingsPreference(SettingsEntry(
                            id = "logout", title = "退出登录", summary = "同时清除本机数据", icon = NexioIcons.Unlock,
                            danger = true, onClick = onLogout,
                        ))
                    }
                }
                val showProjectStar = !preview && subPage == null && (search.isBlank() ||
                    "GitHub 项目主页 star 支持 开源".contains(search, ignoreCase = true))
                if (!showServiceStatus && visibleGroups.isEmpty() && !showLogout && !isDirectory && !showProjectStar) {
                    Text("没有找到相关设置", fontSize = 14.sp, color = secondary,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp))
                }
                if (showProjectStar) ProjectStarEntry()
            }
        }
        if (showWidgetPicker) {
            WidgetPreviewScreen(onBack = { showWidgetPicker = false })
        }
        if (showDesktopAppearance) {
            DesktopAppearanceScreen(onBack = { showDesktopAppearance = false })
        }
    }
}

@Composable
private fun SettingsGroupEntryWithEditor(
    entry: SettingsEntry,
    openSection: MutableState<String?>,
    syncError: String?,
) {
    SettingsPreference(entry)
    CardExpand(visible = openSection.value == entry.id && entry.editor != null) {
        SettingEditor { entry.editor?.invoke() }
    }
    CardExpand(visible = entry.id == "sync" && syncError != null) {
        SettingEditor { Text(syncError.orEmpty(), fontSize = 13.sp, color = LocalDs.current.danger) }
    }
}

@Composable
private fun WallpaperAppearanceEditor(state: WallpaperState?) {
    val ds = LocalDs.current
    if (state == null) {
        Text("壁纸设置暂不可用，请重新进入设置页。", fontSize = 13.sp, color = ds.sub)
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                if (state.bitmap != null) "已设置自定义壁纸" else "尚未设置壁纸",
                modifier = Modifier.weight(1f), fontSize = 14.sp, color = ds.text,
            )
            FlatTextButton(
                text = if (state.bitmap != null) "更换" else "选择图片",
                onClick = state.choose,
                minWidth = 84.dp,
                minHeight = 36.dp,
                cornerRadius = 100.dp,
            )
            if (state.bitmap != null) {
                FlatTextButton(
                    text = "清除", onClick = state.clear, minWidth = 60.dp, minHeight = 36.dp,
                    cornerRadius = 100.dp, textColor = ds.danger,
                )
            }
        }
        state.error?.let { Text(it, fontSize = 13.sp, color = ds.danger) }
        if (state.bitmap != null) {
            Text("背景模糊 · ${state.blur.toInt()} dp", fontSize = 14.sp, color = ds.text)
            Slider(
                value = state.blur,
                onValueChange = state.setBlur,
                valueRange = 0f..40f,
                showKeyPoints = true,
                keyPoints = listOf(0f, 8f, 16f, 24f, 32f, 40f),
            )
            Text("背景压暗 · ${(state.dim * 100).toInt()}%", fontSize = 14.sp, color = ds.text)
            Slider(
                value = state.dim,
                onValueChange = state.setDim,
                valueRange = 0f..0.85f,
                showKeyPoints = true,
                keyPoints = listOf(0f, 0.2f, 0.4f, 0.6f, 0.8f),
            )
            Text("壁纸保存在本机应用私有目录，不会上传，也不会写入备份文件。", fontSize = 13.sp, color = ds.sub)
        } else {
            Text("选择一张图片后可设置模糊与压暗程度；无壁纸时应用继续使用深浅主题纯色背景。", fontSize = 13.sp, color = ds.sub)
        }
    }
}

private data class SettingsSection(val title: String, val entries: List<SettingsEntry>)

private data class SettingsEntry(
    val id: String,
    val title: String,
    val icon: ImageVector,
    val summary: String? = null,
    val detail: String? = null,
    val keywords: String = "",
    val danger: Boolean = false,
    val enabled: Boolean = true,
    val checked: Boolean? = null,
    val onCheckedChange: ((Boolean) -> Unit)? = null,
    val onClick: (() -> Unit)? = null,
    val editor: (@Composable () -> Unit)? = null,
) {
    fun matches(query: String, group: String): Boolean = query.isBlank() ||
        listOf(title, summary.orEmpty(), keywords, group).any { it.contains(query, ignoreCase = true) }
}

/** 分组设置行统一使用主题文字与无底衬的线性图标。 */
@Composable
private fun SettingsPreference(entry: SettingsEntry) {
    val ds = LocalDs.current
    val accent = if (entry.danger) ds.danger else ds.accent
    val leading: @Composable () -> Unit = {
        Box(Modifier.padding(end = 12.dp).size(28.dp), contentAlignment = Alignment.Center) {
            Icon(entry.icon, contentDescription = null, modifier = Modifier.size(22.dp), tint = accent)
        }
    }
    val titleColors = BasicComponentDefaults.titleColor(color = if (entry.danger) ds.danger else ds.text)
    val summaryColors = BasicComponentDefaults.summaryColor(color = ds.sub)
    val margins = PaddingValues(horizontal = 16.dp, vertical = 14.dp)
    when {
        entry.checked != null && entry.onCheckedChange != null -> SwitchPreference(
            title = entry.title, summary = entry.summary, titleColor = titleColors, summaryColor = summaryColors,
            checked = entry.checked, onCheckedChange = { if (entry.enabled) entry.onCheckedChange.invoke(it) },
            startAction = leading, insideMargin = margins, enabled = entry.enabled,
        )
        entry.onClick != null -> ArrowPreference(
            title = entry.title, summary = entry.summary, titleColor = titleColors, summaryColor = summaryColors,
            startAction = leading, insideMargin = margins,
            onClick = { if (entry.enabled) entry.onClick.invoke() }, enabled = entry.enabled,
            endActions = { entry.detail?.let { SettingsDetail(it) } },
        )
        else -> BasicComponent(
            title = entry.title, summary = entry.summary, titleColor = titleColors, summaryColor = summaryColors,
            startAction = leading, insideMargin = margins,
            endActions = { entry.detail?.let { SettingsDetail(it) } },
        )
    }
}

@Composable
private fun SettingsDetail(text: String) {
    Text(text, fontSize = 13.sp, color = LocalDs.current.sub, maxLines = 1, overflow = TextOverflow.Ellipsis)
}

/** 在实底搜索卡中筛选本页的实际设置项。 */
@Composable
private fun SettingsSearchField(query: String, onQueryChange: (String) -> Unit) {
    val ds = LocalDs.current
    val focusManager = LocalFocusManager.current
    AppCard(
        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
        insideMargin = PaddingValues(horizontal = AppUiTokens.CardPadding, vertical = 4.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 48.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(NexioIcons.Search, contentDescription = null, modifier = Modifier.size(20.dp), tint = ds.sub)
            Spacer(Modifier.width(12.dp))
            BasicTextField(
                value = query, onValueChange = onQueryChange, modifier = Modifier.weight(1f),
                singleLine = true, textStyle = TextStyle(fontSize = 16.sp, color = ds.text),
                cursorBrush = SolidColor(ds.accent),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
                decorationBox = { input ->
                    Box {
                        if (query.isEmpty()) Text("搜索设置", fontSize = 16.sp, color = ds.sub)
                        input()
                    }
                },
            )
            if (query.isNotEmpty()) {
                FlatIconButton(onClick = { onQueryChange("") }, minWidth = 40.dp, minHeight = 40.dp) {
                    Icon(NexioIcons.Close, contentDescription = "清除搜索", modifier = Modifier.size(18.dp), tint = ds.sub)
                }
            }
        }
    }
}
