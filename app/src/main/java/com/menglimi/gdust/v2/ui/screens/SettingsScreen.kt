package com.menglimi.gdust.v2.ui.screens

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.menglimi.gdust.v2.data.Repository
import com.menglimi.gdust.v2.ui.AppUiTokens
import com.menglimi.gdust.v2.ui.LocalDs
import com.menglimi.gdust.v2.ui.LocalUiStyle
import com.menglimi.gdust.v2.ui.LocalWallpaperActive
import com.menglimi.gdust.v2.ui.UiStyle
import com.menglimi.gdust.v2.ui.importWallpaper
import com.menglimi.gdust.v2.ui.pageBackground
import com.menglimi.gdust.v2.ui.pageTextBacking
import com.menglimi.gdust.v2.ui.secondaryTextColor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.window.WindowDialog
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.BasicComponentDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Alarm
import top.yukonga.miuix.kmp.icon.extended.Background
import top.yukonga.miuix.kmp.icon.extended.Backup
import top.yukonga.miuix.kmp.icon.extended.BankCards
import top.yukonga.miuix.kmp.icon.extended.ChevronForward
import top.yukonga.miuix.kmp.icon.extended.Close
import top.yukonga.miuix.kmp.icon.extended.ContactsCircle
import top.yukonga.miuix.kmp.icon.extended.ConvertFile
import top.yukonga.miuix.kmp.icon.extended.Home
import top.yukonga.miuix.kmp.icon.extended.Import
import top.yukonga.miuix.kmp.icon.extended.Info
import top.yukonga.miuix.kmp.icon.extended.Link
import top.yukonga.miuix.kmp.icon.extended.Messages
import top.yukonga.miuix.kmp.icon.extended.MindMap
import top.yukonga.miuix.kmp.icon.extended.Months
import top.yukonga.miuix.kmp.icon.extended.Notes
import top.yukonga.miuix.kmp.icon.extended.Refresh
import top.yukonga.miuix.kmp.icon.extended.Search
import top.yukonga.miuix.kmp.icon.extended.Theme
import top.yukonga.miuix.kmp.icon.extended.Timer
import top.yukonga.miuix.kmp.icon.extended.Unlock
import top.yukonga.miuix.kmp.icon.extended.Weeks
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

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
    onWallpaperChanged: () -> Unit,
    onProfile: () -> Unit,
    onLogin: () -> Unit,
    onWidgets: () -> Unit,
    onAbout: () -> Unit,
    onExportBackup: ManagedActivityResultLauncher<String, Uri?>,
    onImportBackup: (Uri) -> Unit,
    onLogout: () -> Unit,
    onBack: () -> Unit,
    loggedIn: Boolean = true,
    chxSessionSaved: Boolean = false,
    monetEnabled: MutableState<Boolean>,
    openSection: MutableState<String?>,
    syncing: Boolean = false,
    onGrades: () -> Unit = {},
    onChxCourses: () -> Unit = {},
    onSchoolNotices: () -> Unit = {},
    onFiveEducation: () -> Unit = {},
) {
    val ds = LocalDs.current
    val secondary = secondaryTextColor()
    val onWallpaper = LocalWallpaperActive.current
    val store = repo.store
    val scope = rememberCoroutineScope()
    val styleState = LocalUiStyle.current
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    var query by rememberSaveable { mutableStateOf("") }

    var base by remember { mutableStateOf(store.llmBase) }
    var apiKey by remember { mutableStateOf(store.llmKey) }
    var model by remember { mutableStateOf(store.llmModel) }
    var llmConfigured by remember { mutableStateOf(store.hasLlm) }
    var saved by remember { mutableStateOf(false) }
    var summary by remember { mutableStateOf<String?>(null) }
    var summarizing by remember { mutableStateOf(false) }
    var summaryError by remember { mutableStateOf("") }
    var thresholdText by remember { mutableStateOf(store.warnThreshold.toString()) }
    var dropText by remember { mutableStateOf(store.warnDropRate.toString()) }
    var thresholdSaved by remember { mutableStateOf(false) }
    var thresholdError by remember { mutableStateOf("") }
    var warnThresholdLocal by remember { mutableStateOf(store.warnThreshold) }
    var termStart by remember { mutableStateOf(store.termStartDate) }
    var termStartLocal by remember { mutableStateOf(store.termStartDate) }
    // 普通 prefs 不会触发重组，选中态必须用本地镜像并同步写回。
    var startTabLocal by remember { mutableStateOf(store.startTab) }
    var sampleIntervalLocal by remember { mutableStateOf(store.sampleIntervalMinutes) }
    var smartWeekendLocal by remember { mutableStateOf(store.smartHideWeekend) }
    var autoSyncLocal by remember { mutableStateOf(store.autoSyncEnabled) }
    // 教务夜间关闭：开关与 HH:mm 时段（prefs 不触发重组，用本地镜像）。
    var maintenanceLocal by remember { mutableStateOf(store.maintenanceReminderEnabled) }
    var maintStartLocal by remember { mutableStateOf(store.maintenanceStartMin) }
    var maintEndLocal by remember { mutableStateOf(store.maintenanceEndMin) }
    var maintStartText by remember { mutableStateOf(com.menglimi.gdust.v2.data.MaintenanceWindow.format(maintStartLocal)) }
    var maintEndText by remember { mutableStateOf(com.menglimi.gdust.v2.data.MaintenanceWindow.format(maintEndLocal)) }
    var maintSaved by remember { mutableStateOf(false) }
    var maintError by remember { mutableStateOf("") }
    var termWeeksText by remember { mutableStateOf(store.termWeeks.takeIf { it > 0 }?.toString() ?: "") }
    var termWeekText by remember { mutableStateOf(store.termCurrentWeek.takeIf { it > 0 }?.toString() ?: "") }
    var termSaved by remember { mutableStateOf(false) }
    var wallpaperPath by remember { mutableStateOf(store.wallpaperPath) }
    var wallpaperBlur by remember { mutableStateOf(store.wallpaperBlur) }
    var wallpaperDim by remember { mutableStateOf(store.wallpaperDim) }
    var showLogoutConfirm by rememberSaveable { mutableStateOf(false) }
    var expandedGroups by rememberSaveable { mutableStateOf(emptyList<String>()) }

    val wallpaperLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            val path = importWallpaper(context, uri)
            if (path.isNotBlank()) {
                store.wallpaperPath = path
                wallpaperPath = path
                onWallpaperChanged()
            }
        }
    }
    // 导入与恢复后的状态重载统一由宿主处理，不能在本页直接写 Store。
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(onImportBackup)
    }

    fun toggle(section: String) {
        focusManager.clearFocus()
        openSection.value = if (openSection.value == section) null else section
    }

    val accountEntry = SettingsEntry(
        id = "profile",
        title = if (loggedIn) store.realName.ifBlank { "个人信息" } else "登录账号",
        summary = when {
            !loggedIn -> "未登录 · 登录后同步教务课表与个人信息"
            store.jobNumber.isNotBlank() -> "广东科技学院 · ${store.jobNumber}"
            else -> "广东科技学院 · 本机数据与偏好"
        },
        keywords = "登录 账户 账号 学校 学号 个人信息 隐私",
        icon = MiuixIcons.ContactsCircle,
        onClick = if (loggedIn) onProfile else onLogin,
    )
    val entries = listOf(
            SettingsEntry(
                id = "style", title = "界面风格", summary = "选择应用的整体外观", icon = MiuixIcons.Theme,
                detail = styleState.value.label, onClick = { toggle("style") },
                editor = {
                    SegmentedChoice(
                        options = UiStyle.entries.map { it to it.label }, selected = styleState.value,
                        onSelect = { styleState.value = it },
                    )
                    Text(styleState.value.desc, fontSize = 13.sp, color = secondary)
                },
            ),
            SettingsEntry(
                id = "startTab", title = "启动页", summary = "下次打开时默认显示", icon = MiuixIcons.Home,
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
                id = "monet", title = "莫奈取色", summary = "跟随系统壁纸配色", icon = MiuixIcons.Theme,
                checked = monetEnabled.value,
                onCheckedChange = { monetEnabled.value = it; store.monetEnabled = it },
            ),
            SettingsEntry(
                id = "wallpaper", title = "课表壁纸", summary = "相册图片、模糊与压暗", icon = MiuixIcons.Background,
                detail = if (wallpaperPath.isBlank()) "未设置" else "已设置",
                onClick = { toggle("wallpaper") },
                editor = {
                    Button(if (wallpaperPath.isBlank()) "选择壁纸" else "更换壁纸") {
                        wallpaperLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    }
                    if (wallpaperPath.isNotBlank()) {
                        Text("模糊 ${wallpaperBlur.toInt()} dp", fontSize = 13.sp, color = secondary)
                        androidx.compose.material3.Slider(
                            value = wallpaperBlur,
                            onValueChange = { wallpaperBlur = it; store.wallpaperBlur = it; onWallpaperChanged() },
                            valueRange = 0f..40f, steps = 19,
                        )
                        Text("压暗 ${(wallpaperDim * 100).toInt()}%", fontSize = 13.sp, color = secondary)
                        androidx.compose.material3.Slider(
                            value = wallpaperDim,
                            onValueChange = { wallpaperDim = it; store.wallpaperDim = it; onWallpaperChanged() },
                            valueRange = 0f..0.85f, steps = 16,
                        )
                        Button("清除壁纸") {
                            store.wallpaperPath = ""
                            wallpaperPath = ""
                            onWallpaperChanged()
                        }
                    }
                },
            ),
            SettingsEntry(
                id = "term", title = "学期设置", summary = "开学日期、总周数与当前周", icon = MiuixIcons.Months,
                keywords = "校历 开学日期 总周数 当前周", detail = termStartLocal.ifBlank { "跟随校历" },
                onClick = { toggle("term") },
                editor = {
                    InputField(termStart, { termStart = it; termSaved = false }, "第一周周一，YYYY-MM-DD")
                    InputField(termWeeksText, { termWeeksText = it; termSaved = false }, "学期总周数", KeyboardType.Number)
                    InputField(termWeekText, { termWeekText = it; termSaved = false }, "当前第几周", KeyboardType.Number)
                    Button(if (termSaved) "已保存，重新同步生效" else "保存学期设置") {
                        store.termStartDate = termStart.trim()
                        termStartLocal = store.termStartDate
                        store.termWeeks = termWeeksText.toIntOrNull() ?: 0
                        store.termCurrentWeek = termWeekText.toIntOrNull() ?: 0
                        termSaved = true
                    }
                    Text("留空则跟随校历。这里只调整日期与周次；学期课程请从教务导入，未取得的周次不能视为无课。", fontSize = 13.sp, color = secondary)
                },
            ),
            SettingsEntry(
                id = "weekend", title = "智能显示周末", summary = "隐藏没有课程的周六、周日", icon = MiuixIcons.Weeks,
                checked = smartWeekendLocal,
                onCheckedChange = { smartWeekendLocal = it; store.smartHideWeekend = it },
            ),
            SettingsEntry(
                id = "reminder", title = "课程提醒", summary = "课前、次日与课中提醒", icon = MiuixIcons.Alarm,
                detail = if (store.reminderEnabled) "已开启" else "未开启", onClick = onReminder,
            ),
            SettingsEntry(
                id = "todoReminder", title = "待办提醒", summary = "作业、考试与自定义事项的到期提醒", icon = MiuixIcons.Alarm,
                detail = if (store.todoReminderEnabled) "已开启" else "未开启", onClick = onTodoReminder,
            ),
            SettingsEntry(
                id = "autoSync", title = "自动同步课表", summary = "保持已登录账号的课表更新", icon = MiuixIcons.Refresh,
                checked = autoSyncLocal,
                onCheckedChange = { autoSyncLocal = it; store.autoSyncEnabled = it },
            ),
            SettingsEntry(
                id = "grades", title = "学期成绩", summary = "按学期查询成绩、学分与绩点", icon = MiuixIcons.Notes,
                keywords = "教务 成绩 绩点 GPA", onClick = onGrades,
            ),
            SettingsEntry(
                id = "chxCourses", title = "学习通课程", summary = "课程列表与任务点进度", icon = MiuixIcons.Months,
                keywords = "课程 任务 学习通 超星", onClick = onChxCourses,
            ),
            SettingsEntry(
                id = "schoolNotices", title = "学校通知", summary = "门户通知、教务处公告与官网新闻", icon = MiuixIcons.Messages,
                keywords = "通知 公告 教务 新闻 考试", onClick = onSchoolNotices,
            ),
            SettingsEntry(
                id = "fiveEducation", title = "五育综合数据平台", summary = "打开校内网页查看活动与学期记录", icon = MiuixIcons.Link,
                detail = "网页", keywords = "活动 五育 综合评价", onClick = onFiveEducation,
            ),
            SettingsEntry(
                id = "chx", title = "学习通联动", summary = "通过学校统一身份认证连接", icon = MiuixIcons.Link,
                detail = if (chxSessionSaved) "已保存，待验证" else "未联动", onClick = onChxEntry,
            ),
            SettingsEntry(
                id = "chxNotifications", title = "学习通通知待办", summary = "从推送识别作业与考试", icon = MiuixIcons.Messages,
                detail = if (store.chxNotificationsEnabled) "已开启" else "未开启", onClick = onChxNotifications,
            ),
            SettingsEntry(
                id = "maintenanceReminder", title = "教务夜间关闭提醒", summary = "学校教务停服时段同步失败时明确提示",
                keywords = "维护 关闭时段", icon = MiuixIcons.Timer,
                checked = maintenanceLocal,
                onCheckedChange = { maintenanceLocal = it; store.maintenanceReminderEnabled = it },
            ),
            SettingsEntry(
                id = "maintenance", title = "关闭时段", summary = "设置教务夜间维护的开始与结束时间",
                keywords = "教务 夜间 关闭提醒 停服 维护", icon = MiuixIcons.Timer,
                detail = "${com.menglimi.gdust.v2.data.MaintenanceWindow.format(maintStartLocal)}–" +
                    com.menglimi.gdust.v2.data.MaintenanceWindow.format(maintEndLocal),
                onClick = { toggle("maintenance") },
                editor = {
                    InputField(maintStartText, { maintStartText = it; maintSaved = false; maintError = "" }, "开始时间（HH:mm，如 23:00）")
                    InputField(maintEndText, { maintEndText = it; maintSaved = false; maintError = "" }, "结束时间（如 06:30，可跨午夜）")
                    Button(if (maintSaved) "已保存" else "保存时段") {
                        val start = com.menglimi.gdust.v2.data.MaintenanceWindow.parse(maintStartText)
                        val end = com.menglimi.gdust.v2.data.MaintenanceWindow.parse(maintEndText)
                        maintSaved = false
                        maintError = when {
                            start == null -> "开始时间格式不正确，请输入 00:00–23:59。"
                            end == null -> "结束时间格式不正确，请输入 00:00–23:59。"
                            start == end -> "开始与结束时间不能相同；跨午夜可设置为 23:00–06:30。"
                            else -> ""
                        }
                        if (start != null && end != null && maintError.isEmpty()) {
                            store.maintenanceStartMin = start
                            store.maintenanceEndMin = end
                            maintStartLocal = store.maintenanceStartMin
                            maintEndLocal = store.maintenanceEndMin
                            maintStartText = com.menglimi.gdust.v2.data.MaintenanceWindow.format(start)
                            maintEndText = com.menglimi.gdust.v2.data.MaintenanceWindow.format(end)
                            maintSaved = true
                        }
                    }
                    if (maintError.isNotEmpty()) SettingsInputError(maintError)
                    Text(
                        "默认 23:00–06:30。该时段内课表/成绩同步失败会提示「教务夜间关闭」而不是网络失败；" +
                            "学习通与电量查询不受影响。若学校调整了停服时间，在这里改。",
                        fontSize = 13.sp, color = secondary,
                    )
                },
            ),
            SettingsEntry(
                id = "warn", title = "低电量预警", summary = "剩余电量阈值与掉电速率", icon = MiuixIcons.BankCards,
                detail = "$warnThresholdLocal 度", onClick = { toggle("warn") },
                editor = {
                    InputField(thresholdText, { thresholdText = it; thresholdSaved = false; thresholdError = "" }, "低电量阈值（度）", KeyboardType.Decimal)
                    InputField(dropText, { dropText = it; thresholdSaved = false; thresholdError = "" }, "掉电速率（度/小时）", KeyboardType.Decimal)
                    Button(if (thresholdSaved) "已保存" else "保存阈值") {
                        val threshold = thresholdText.trim().toFloatOrNull()
                        val drop = dropText.trim().toFloatOrNull()
                        thresholdSaved = false
                        thresholdError = when {
                            threshold == null || !threshold.isFinite() || threshold <= 0f -> "低电量阈值请输入大于 0 的有效数字，支持小数。"
                            drop == null || !drop.isFinite() || drop <= 0f -> "掉电速率请输入大于 0 的有效数字，支持小数。"
                            else -> ""
                        }
                        if (threshold != null && drop != null && thresholdError.isEmpty()) {
                            store.warnThreshold = threshold
                            store.warnDropRate = drop
                            warnThresholdLocal = store.warnThreshold
                            thresholdSaved = true
                        }
                    }
                    if (thresholdError.isNotEmpty()) SettingsInputError(thresholdError)
                    Text("后台每 15 分钟巡检，同类预警间隔 12 小时。", fontSize = 13.sp, color = secondary)
                },
            ),
            SettingsEntry(
                id = "interval", title = "电量采样间隔", summary = "记录宿舍电量走势", icon = MiuixIcons.Timer,
                detail = "$sampleIntervalLocal 分钟", onClick = { toggle("interval") },
                editor = {
                    SegmentedChoice(
                        options = listOf(15, 30, 60, 120).map { it to "${it}分" },
                        selected = sampleIntervalLocal,
                        onSelect = { sampleIntervalLocal = it; store.sampleIntervalMinutes = it },
                    )
                    Text("间隔越短，走势越细；后台仍按 15 分钟巡检。", fontSize = 13.sp, color = secondary)
                },
            ),
            SettingsEntry(
                id = "llm", title = "AI 中转站配置", summary = "OpenAI 兼容服务，密钥仅存本机", icon = MiuixIcons.MindMap,
                detail = if (llmConfigured) "已配置" else "未配置", onClick = { toggle("llm") },
                editor = {
                    InputField(base, { base = it; saved = false }, "API Base")
                    InputField(apiKey, { apiKey = it; saved = false }, "API Key", visual = PasswordVisualTransformation())
                    InputField(model, { model = it; saved = false }, "模型名")
                    Button(
                        text = if (saved) "已保存" else "保存配置",
                        enabled = base.isNotBlank() && apiKey.isNotBlank() && model.isNotBlank(),
                        onClick = {
                            store.llmBase = base.trim()
                            store.llmKey = apiKey.trim()
                            store.llmModel = model.trim()
                            llmConfigured = store.hasLlm
                            saved = true
                        },
                    )
                },
            ),
            SettingsEntry(
                id = "summary", title = "生成公告总结",
                summary = if (llmConfigured) "用已配置的模型提炼学校公告" else "请先配置 AI 中转站",
                keywords = "AI 生成总结", icon = MiuixIcons.Notes,
                detail = if (summary != null) "查看" else null, onClick = { toggle("summary") },
                editor = {
                    summary?.let { Text(it, fontSize = 14.sp, lineHeight = 21.sp, color = ds.text) }
                    Button(
                        text = if (summarizing) "正在总结" else if (summary == null) "生成总结" else "重新总结",
                        enabled = llmConfigured && !summarizing,
                        onClick = {
                            summarizing = true
                            summaryError = ""
                            scope.launch {
                                try {
                                    summary = repo.summarize()
                                } catch (error: CancellationException) {
                                    throw error
                                } catch (error: Exception) {
                                    summaryError = error.message ?: "总结失败"
                                } finally {
                                    summarizing = false
                                }
                            }
                        },
                    )
                    if (summaryError.isNotEmpty()) Text(summaryError, fontSize = 13.sp, color = ds.danger)
                },
            ),
            SettingsEntry(
                id = "sync", title = "账号直连同步", summary = "使用当前登录账号读取教务课表", icon = MiuixIcons.Refresh,
                detail = when {
                    !loggedIn -> "请先登录"
                    syncing -> "同步中"
                    syncError != null -> "失败"
                    store.lastSyncAt > 0 -> "已同步"
                    else -> "未同步"
                },
                enabled = !loggedIn || !syncing, onClick = if (loggedIn) onSync else onLogin,
            ),
            SettingsEntry(
                id = "webImport", title = "网页登录导入", summary = "在教务网页导入学期课程", icon = MiuixIcons.Import,
                keywords = "兼容备用 教务学期导入", detail = if (store.loadWebImportPage().isNotBlank()) "已保存会话" else null,
                onClick = onWebImport,
            ),
            SettingsEntry(
                id = "cachedCourses", title = "本地缓存课程", icon = MiuixIcons.Months,
                detail = "${store.loadBlocks().size} 张卡",
            ),
            SettingsEntry(
                id = "calendar", title = "校历", icon = MiuixIcons.Weeks,
                detail = store.loadCalendar()?.let { "第 ${it.week}/${it.allWeek} 周" } ?: "未同步",
            ),
            SettingsEntry(
                id = "widgets", title = "桌面小部件", summary = "课表 / 电量 / 待办，各三款尺寸",
                icon = MiuixIcons.Months,
                keywords = "小组件 widget 添加 电量 待办 课表",
                onClick = onWidgets,
            ),
            SettingsEntry(
                id = "export", title = "导出备份", summary = "课表、待办、偏好与水电绑定", icon = MiuixIcons.Backup,
                keywords = "JSON 不含登录凭证 密码",
                onClick = { onExportBackup.launch("gdust-backup-${java.time.LocalDate.now()}.json") },
            ),
            SettingsEntry(
                id = "import", title = "导入备份", summary = "从 JSON 恢复，将覆盖现有数据与偏好", icon = MiuixIcons.ConvertFile,
                onClick = { importLauncher.launch(arrayOf("application/json", "text/*")) },
            ),
            SettingsEntry(
                id = "about", title = "关于与更新", summary = "版本、更新说明与数据来源", icon = MiuixIcons.Info,
                onClick = onAbout,
            ),
    )
    // 编辑器与操作只定义一次，按真实条目的 id 组织展示与搜索。
    val entriesById = entries.associateBy { it.id }
    val groups = listOf(
        "账号与课表" to listOf("sync", "webImport", "autoSync", "term", "weekend", "cachedCourses", "calendar"),
        "提醒" to listOf("reminder", "todoReminder", "chxNotifications", "maintenanceReminder", "maintenance"),
        "电量" to listOf("warn", "interval"),
        "外观" to listOf("style", "startTab", "monet", "wallpaper", "widgets"),
        "校园服务" to listOf("grades", "chxCourses", "schoolNotices", "fiveEducation", "chx"),
        "AI" to listOf("llm", "summary"),
        "数据与备份" to listOf("export", "import"),
        "关于" to listOf("about"),
    ).map { (title, ids) -> SettingsSection(title, ids.map(entriesById::getValue)) }
    LaunchedEffect(openSection.value) {
        groups.firstOrNull { group -> group.entries.any { it.id == openSection.value && it.editor != null } }
            ?.let { group ->
                if (group.title !in expandedGroups) expandedGroups = expandedGroups + group.title
                val entry = group.entries.first { it.id == openSection.value }
                if (!entry.matches(query.trim(), group.title)) query = ""
            }
    }
    val search = query.trim()
    val visibleGroups = groups.mapNotNull { group ->
        group.copy(entries = group.entries.filter { it.matches(search, group.title) })
            .takeIf { it.entries.isNotEmpty() }
    }
    val logoutEntry = SettingsEntry(
        id = "logout", title = "退出登录", summary = "同时清除本机数据", icon = MiuixIcons.Unlock,
        keywords = "账户 账号 清除本机数据", danger = true,
        onClick = { focusManager.clearFocus(); showLogoutConfirm = true },
    )
    val showAccount = accountEntry.matches(search, "账户")
    val showLogout = loggedIn && logoutEntry.matches(search, "账户")
    val resultCount = visibleGroups.sumOf { it.entries.size } + (if (showAccount) 1 else 0) + (if (showLogout) 1 else 0)

    BackHandler {
        if (query.isNotEmpty()) { query = ""; focusManager.clearFocus() } else onBack()
    }
    val density = LocalDensity.current
    val backThreshold = with(density) { 96.dp.toPx() }
    val backEdge = with(density) { 32.dp.toPx() }
    var backPx by remember { mutableStateOf(Float.NaN) }

    Column(
        Modifier
            .fillMaxSize()
            .background(pageBackground())
            .pointerInput(onBack, backThreshold, backEdge) {
                detectHorizontalDragGestures(
                    onDragStart = { backPx = if (it.x < backEdge) 0f else Float.NaN },
                    onDragEnd = {
                        if (!backPx.isNaN() && backPx >= backThreshold) onBack()
                        backPx = Float.NaN
                    },
                    onDragCancel = { backPx = Float.NaN },
                ) { change, amount ->
                    if (!backPx.isNaN()) { backPx += amount; change.consume() }
                }
            }
            .statusBarsPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = AppUiTokens.PagePadding),
    ) {
        Spacer(Modifier.height(24.dp))
        PageTitle(title = "设置", onBack = onBack)
        SettingsSearchField(query = query, onQueryChange = { query = it })
        if (search.isNotBlank()) {
            Text(
                "找到 $resultCount 项设置", fontSize = 13.sp, color = secondary,
                modifier = Modifier.padding(bottom = 12.dp).pageTextBacking()
                    .padding(if (onWallpaper) 12.dp else 0.dp)
                    .semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
        if (showAccount) {
            SettingGroup { SettingsPreference(accountEntry) }
        }
        visibleGroups.forEach { group ->
            key(group.title) {
                val expanded = search.isNotBlank() || group.title in expandedGroups ||
                    group.entries.any { it.id == openSection.value && it.editor != null }
                SettingGroup {
                    SettingsGroupHeader(
                        group = group, expanded = expanded, searching = search.isNotBlank(),
                        onClick = {
                            focusManager.clearFocus()
                            if (expanded) {
                                expandedGroups = expandedGroups - group.title
                                if (group.entries.any { it.id == openSection.value }) openSection.value = null
                            } else {
                                expandedGroups = expandedGroups + group.title
                            }
                        },
                    )
                    if (expanded) {
                        group.entries.forEach { entry ->
                            key(entry.id) {
                                SettingsPreference(entry)
                                if (openSection.value == entry.id && entry.editor != null) {
                                    SettingsInlineEditor(entry.editor)
                                }
                                if (entry.id == "sync" && loggedIn && syncError != null) {
                                    SettingEditor { Text(syncError, fontSize = 13.sp, color = ds.danger) }
                                }
                            }
                        }
                    }
                }
            }
        }
        if (showLogout) {
            SettingGroup { SettingsPreference(logoutEntry) }
        }
        if (resultCount == 0) {
            Text("没有找到相关设置", fontSize = 14.sp, color = secondary,
                modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp).pageTextBacking()
                    .padding(if (onWallpaper) 12.dp else 0.dp))
        }
        Spacer(Modifier.navigationBarsPadding().height(110.dp))
    }
    WindowDialog(
        show = showLogoutConfirm && loggedIn,
        title = "退出登录？",
        summary = "退出后将清除本机数据。需要保留课表、待办与偏好时，请先在「数据与备份」中导出备份。",
        titleColor = ds.text,
        summaryColor = secondary,
        backgroundColor = ds.card,
        onDismissRequest = { showLogoutConfirm = false },
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TextButton(
                text = "取消", onClick = { showLogoutConfirm = false },
                modifier = Modifier.weight(1f),
            )
            TextButton(
                text = "确认退出", textColor = ds.danger,
                onClick = { showLogoutConfirm = false; onLogout() },
                modifier = Modifier.weight(1f),
            )
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
        listOf(title, summary.orEmpty(), detail.orEmpty(), keywords, group).any { it.contains(query, ignoreCase = true) }
}

@Composable
private fun SettingsGroupHeader(group: SettingsSection, expanded: Boolean, searching: Boolean, onClick: () -> Unit) {
    val ds = LocalDs.current
    BasicComponent(
        title = group.title,
        titleColor = BasicComponentDefaults.titleColor(color = ds.text),
        modifier = Modifier.semantics { stateDescription = if (expanded) "已展开" else "已折叠" },
        insideMargin = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
        onClick = if (searching) null else onClick,
        onClickLabel = if (expanded) "收起${group.title}" else "展开${group.title}",
        role = Role.Button,
        endActions = {
            SettingsDetail(if (searching) "${group.entries.size} 项匹配" else "${group.entries.size} 项")
            Spacer(Modifier.width(8.dp))
            Icon(
                MiuixIcons.ChevronForward, contentDescription = null,
                modifier = Modifier.size(18.dp).rotate(if (expanded) 90f else 0f),
                tint = secondaryTextColor(),
            )
        },
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SettingsInlineEditor(editor: @Composable () -> Unit) {
    val requester = remember { BringIntoViewRequester() }
    var positioned by remember { mutableStateOf(false) }
    val previewHeight = with(LocalDensity.current) { 144.dp.toPx() }
    LaunchedEffect(positioned) {
        if (positioned) {
            // 只请求编辑区开头，长表单也能在小屏上露出首个输入框。
            requester.bringIntoView(Rect(0f, 0f, 1f, previewHeight))
        }
    }
    Box(
        Modifier.fillMaxWidth().bringIntoViewRequester(requester)
            .onGloballyPositioned { positioned = true },
    ) {
        SettingEditor { editor() }
    }
}

@Composable
private fun SettingsInputError(message: String) {
    Text(
        message, fontSize = 13.sp, color = LocalDs.current.danger,
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
    )
}

/** Nexio SettingsEntryIcon geometry, using Miuix vectors and the app's Monet-aware colors. */
@Composable
private fun SettingsPreference(entry: SettingsEntry) {
    val ds = LocalDs.current
    val accent = if (entry.danger) ds.danger else ds.accent
    val leading: @Composable () -> Unit = {
        Box(Modifier.padding(end = 8.dp)) {
            Box(
                Modifier.size(28.dp).background(accent.copy(alpha = 0.13f), RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(entry.icon, contentDescription = null, modifier = Modifier.size(22.dp), tint = accent)
            }
        }
    }
    val titleColors = BasicComponentDefaults.titleColor(color = if (entry.danger) ds.danger else ds.text)
    val summaryColors = BasicComponentDefaults.summaryColor(color = secondaryTextColor())
    val margins = PaddingValues(start = 16.dp, end = 13.dp, top = 14.dp, bottom = 14.dp)
    when {
        entry.checked != null && entry.onCheckedChange != null -> SwitchPreference(
            title = entry.title, summary = entry.summary, titleColor = titleColors, summaryColor = summaryColors,
            checked = entry.checked, onCheckedChange = entry.onCheckedChange,
            startAction = leading, insideMargin = margins, enabled = entry.enabled,
        )
        entry.onClick != null -> ArrowPreference(
            title = entry.title, summary = entry.summary, titleColor = titleColors, summaryColor = summaryColors,
            startAction = leading, insideMargin = margins, onClick = entry.onClick, enabled = entry.enabled,
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
    Text(text, fontSize = 14.sp, color = secondaryTextColor(), maxLines = 1, overflow = TextOverflow.Ellipsis)
}

/** 59dp block from Nexio SettingsSearch.kt; filters only this page's real entries. */
@Composable
private fun SettingsSearchField(query: String, onQueryChange: (String) -> Unit) {
    val ds = LocalDs.current
    val secondary = secondaryTextColor()
    val searchSurface = MiuixTheme.colorScheme.surface.copy(alpha = 1f)
    val focusManager = LocalFocusManager.current
    Row(
        Modifier.fillMaxWidth().height(59.dp).padding(bottom = 12.dp)
            .background(searchSurface, RoundedCornerShape(16.dp)).padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(MiuixIcons.Search, contentDescription = null, modifier = Modifier.size(20.dp), tint = secondary)
        Spacer(Modifier.width(10.dp))
        BasicTextField(
            value = query, onValueChange = onQueryChange, modifier = Modifier.weight(1f),
            singleLine = true, textStyle = TextStyle(fontSize = 16.sp, color = ds.text),
            cursorBrush = SolidColor(ds.accent),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
            decorationBox = { input ->
                Box {
                    if (query.isEmpty()) Text("搜索设置", fontSize = 16.sp, color = secondary)
                    input()
                }
            },
        )
        if (query.isNotEmpty()) {
            IconButton(onClick = { onQueryChange("") }, modifier = Modifier.size(40.dp)) {
                Icon(MiuixIcons.Close, contentDescription = "清除搜索", modifier = Modifier.size(18.dp), tint = secondary)
            }
        }
    }
}
