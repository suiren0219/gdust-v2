package com.menglimi.gdust.v2.ui.screens

import android.net.Uri

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.menglimi.gdust.v2.data.Repository
import com.menglimi.gdust.v2.ui.LocalDs
import com.menglimi.gdust.v2.ui.LocalUiStyle
import com.menglimi.gdust.v2.ui.UiStyle
import kotlinx.coroutines.launch

/**
 * 设置：分组行式选项，编辑区点开才展开（不再一页平铺一堆输入框和大卡）。
 * openSection 由 AppRoot（主题树内）持有，切风格收起编辑区即可，不丢页面栈。
 */
@Composable
fun SettingsScreen(
    repo: Repository,
    syncError: String?,
    onSync: () -> Unit,
    onWebImport: () -> Unit,
    onWallpaperChanged: () -> Unit,
    onProfile: () -> Unit,
    onAbout: () -> Unit,
    onExportBackup: androidx.activity.compose.ManagedActivityResultLauncher<String, Uri?>,
    onImportBackup: (Uri) -> Unit,
    onLogout: () -> Unit,
    onBack: () -> Unit,
    loggedIn: Boolean = true,
    monetEnabled: androidx.compose.runtime.MutableState<Boolean>,
    openSection: androidx.compose.runtime.MutableState<String?>,
    syncing: Boolean = false,
) {
    val ds = LocalDs.current
    val store = repo.store
    val scope = rememberCoroutineScope()

    var base by remember { mutableStateOf(store.llmBase) }
    var key by remember { mutableStateOf(store.llmKey) }
    var model by remember { mutableStateOf(store.llmModel) }
    var saved by remember { mutableStateOf(false) }
    var summary by remember { mutableStateOf<String?>(null) }
    var summarizing by remember { mutableStateOf(false) }
    var summaryError by remember { mutableStateOf("") }
    var thresholdText by remember { mutableStateOf(store.warnThreshold.toInt().toString()) }
    var dropText by remember { mutableStateOf(store.warnDropRate.toInt().toString()) }
    var thresholdSaved by remember { mutableStateOf(false) }
    var synced by remember { mutableStateOf(false) }
    var termStart by remember { mutableStateOf(store.termStartDate) }
    var termWeeksText by remember { mutableStateOf(store.termWeeks.takeIf { it > 0 }?.toString() ?: "") }
    var termWeekText by remember { mutableStateOf(store.termCurrentWeek.takeIf { it > 0 }?.toString() ?: "") }
    var termSaved by remember { mutableStateOf(false) }
    var backupMsg by remember { mutableStateOf<String?>(null) }
    val hostContext = androidx.compose.ui.platform.LocalContext.current
    var wallpaperPath by remember { mutableStateOf(store.wallpaperPath) }
    var wallpaperBlur by remember { mutableStateOf(store.wallpaperBlur) }
    var wallpaperDim by remember { mutableStateOf(store.wallpaperDim) }
    val wallpaperLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null) {
            val path = com.menglimi.gdust.v2.ui.importWallpaper(hostContext, uri)
            if (path.isNotBlank()) {
                store.wallpaperPath = path
                wallpaperPath = path
                onWallpaperChanged()
            }
        }
    }
    val context = androidx.compose.ui.platform.LocalContext.current
    val importLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching {
            context.contentResolver.openInputStream(uri)?.bufferedReader()?.readText().orEmpty()
        }.map { repo.store.importBackup(it) }
            .onSuccess { backupMsg = "已恢复：$it" }
            .onFailure { backupMsg = "导入失败：${it.message}" }
    }
    val intervalOptions = listOf(15, 30, 60, 120)

    fun toggle(section: String) {
        openSection.value = if (openSection.value == section) null else section
    }

    // 系统返回（含左缘手势）只关设置页，不再直接退出 App
    androidx.activity.compose.BackHandler { onBack() }
    // 页内左滑返回：挂在外层容器上，避免和纵向滚动手势竞争
    val density = androidx.compose.ui.platform.LocalDensity.current
    val backThreshold = with(density) { 96.dp.toPx() }
    var backPx by remember { mutableStateOf(0f) }

    Box(
        Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = { start -> backPx = if (start.x < 120f) 0f else Float.NaN },
                    onDragEnd = {
                        if (!backPx.isNaN() && backPx >= backThreshold) onBack()
                        backPx = 0f
                    },
                    onDragCancel = { backPx = 0f },
                ) { change, dragAmount ->
                    if (!backPx.isNaN()) backPx += dragAmount
                    change.consume()
                }
            },
    ) {
    Column(
        Modifier
            .fillMaxSize()
            .background(ds.bg)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
    ) {
        Spacer(Modifier.height(24.dp))
        PageTitle(title = "设置", subtitle = "外观 · 数据 · 账号 · 关于", onBack = onBack)

        // ── 外观 ──
        SectionHeader("外观")
        SettingGroup {
            val styleState = LocalUiStyle.current
            SettingOption(
                title = "界面风格",
                subtitle = "整套主题、卡片、输入框、按钮、底栏都跟着换",
                detail = styleState.value.label,
                onClick = { toggle("style") },
            )
            if (openSection.value == "style") {
                SettingDivider()
                SettingEditor {
                    SegmentedChoice(
                        options = UiStyle.entries.map { it to it.label },
                        selected = styleState.value,
                        onSelect = { styleState.value = it },
                    )
                    Text(styleState.value.desc, fontSize = 11.sp, color = ds.sub)
                }
            }
            SettingDivider()
            SettingOption(
                title = "启动页",
                subtitle = "打开 App 默认进入的页面",
                detail = when (store.startTab) {
                    0 -> "今日"; 2 -> "电费"; 3 -> "待办"; else -> "课表"
                },
                onClick = { toggle("startTab") },
            )
            if (openSection.value == "startTab") {
                SettingDivider()
                SettingEditor {
                    SegmentedChoice(
                        options = listOf(0 to "今日", 1 to "课表", 2 to "电费", 3 to "待办"),
                        selected = store.startTab,
                        onSelect = { store.startTab = it },
                    )
                }
            }
            SettingDivider()
            SettingOption(
                title = "莫奈取色",
                subtitle = "跟随系统壁纸配色；关闭则用默认蓝",
                switch = monetEnabled.value,
                onSwitch = {
                    monetEnabled.value = it
                    store.monetEnabled = it
                },
            )
        }

        // ── 壁纸 ──
        SectionHeader("壁纸")
        SettingGroup {
            SettingOption(
                title = "课表壁纸",
                subtitle = "从相册选一张图作为页面背景；模糊与压暗可调",
                detail = if (wallpaperPath.isBlank()) "未设置" else "已设置",
                onClick = {
                    wallpaperLauncher.launch(
                        androidx.activity.result.PickVisualMediaRequest(
                            androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly,
                        )
                    )
                },
            )
            if (wallpaperPath.isNotBlank()) {
                SettingDivider()
                SettingEditor {
                    Text("模糊 ${wallpaperBlur.toInt()} dp", fontSize = 12.sp, color = ds.sub)
                    androidx.compose.material3.Slider(
                        value = wallpaperBlur,
                        onValueChange = {
                            wallpaperBlur = it
                            store.wallpaperBlur = it
                            onWallpaperChanged()
                        },
                        valueRange = 0f..40f,
                        steps = 19,
                    )
                    Text("压暗 ${(wallpaperDim * 100).toInt()}%", fontSize = 12.sp, color = ds.sub)
                    androidx.compose.material3.Slider(
                        value = wallpaperDim,
                        onValueChange = {
                            wallpaperDim = it
                            store.wallpaperDim = it
                            onWallpaperChanged()
                        },
                        valueRange = 0f..0.85f,
                        steps = 16,
                    )
                    Button("清除壁纸") {
                        store.wallpaperPath = ""
                        wallpaperPath = ""
                        onWallpaperChanged()
                    }
                }
            }
        }

        // ── 账号与数据 ──
        SectionHeader("账号与数据")
        SettingGroup {
            SettingOption(
                title = store.realName.ifEmpty { "未登录" },
                subtitle = if (store.jobNumber.isEmpty()) "查看本机数据快照" else "学号 ${store.jobNumber}",
                onClick = onProfile,
            )
            SettingDivider()
            SettingOption(
                title = "账号密码直连同步",
                subtitle = "推荐：用当前登录账号直接读取教务整学期课表，不打开网页",
                detail = when {
                    syncing -> "同步中…"
                    syncError != null -> "失败"
                    synced -> "已同步 ✓"
                    else -> null
                },
                onClick = { onSync(); synced = true },
            )
            if (syncError != null) {
                SettingEditor { Text("失败：$syncError", fontSize = 11.sp, color = ds.danger) }
            }
            SettingDivider()
            SettingOption(
                title = "网页登录导入（兼容备用）",
                subtitle = "拾光式：网页中登录教务后读取原始周次；网页登录会话单独保留",
                detail = if (store.loadWebImportPage().isNotBlank()) "已保存会话" else null,
                onClick = onWebImport,
            )

            SettingDivider()
            SettingOption(title = "本地缓存课程", detail = "${store.loadBlocks().size} 张卡")
            SettingDivider()
            SettingOption(
                title = "校历",
                detail = store.loadCalendar()?.let { "第 ${it.week}/${it.allWeek} 周" } ?: "未同步",
            )
        }

        // ── 学期设置（拾光式：开学日期 / 总周数 / 当前周）──
        SectionHeader("学期设置")
        SettingGroup {
            SettingOption(
                title = "开学日期",
                subtitle = "学期第一周周一，YYYY-MM-DD；留空则用校历",
                detail = store.termStartDate.ifBlank { "跟随校历" },
                onClick = { toggle("term") },
            )
            if (openSection.value == "term") {
                SettingDivider()
                SettingEditor {
                    InputField(termStart, { termStart = it; termSaved = false }, "开学日期，如 2026-09-07")
                    InputField(termWeeksText, { termWeeksText = it; termSaved = false }, "学期总周数，如 20", KeyboardType.Number)
                    InputField(termWeekText, { termWeekText = it; termSaved = false }, "当前第几周，如 10", KeyboardType.Number)
                    Button(if (termSaved) "已保存 ✓ 重新同步生效" else "保存学期设置") {
                        store.termStartDate = termStart.trim()
                        store.termWeeks = termWeeksText.toIntOrNull() ?: 0
                        store.termCurrentWeek = termWeekText.toIntOrNull() ?: 0
                        termSaved = true
                    }
                    Text(
                        "这里只调整周次与日期的对应关系，不会新增课程。学期课程请使用「教务学期导入」；未取得的周次不能视为无课。",
                        fontSize = 11.sp, lineHeight = 15.sp, color = ds.sub,
                    )
                }
            }
        }

        // ── 通知 AI 总结 ──
        SectionHeader("通知 AI 总结")
        SettingGroup {
            SettingOption(
                title = "中转站配置",
                subtitle = "自填 OpenAI 兼容端点，密钥只存本机",
                detail = if (store.hasLlm) "已配置" else "未配置",
                onClick = { toggle("llm") },
            )
            if (openSection.value == "llm") {
                SettingDivider()
                SettingEditor {
                    InputField(base, { base = it; saved = false }, "API Base，如 https://…/v1")
                    InputField(key, { key = it; saved = false }, "API Key", visual = PasswordVisualTransformation())
                    InputField(model, { model = it; saved = false }, "模型名")
                    Button(
                        text = if (saved) "已保存 ✓" else "保存配置",
                        enabled = base.isNotBlank() && key.isNotBlank() && model.isNotBlank(),
                        onClick = {
                            store.llmBase = base.trim()
                            store.llmKey = key.trim()
                            store.llmModel = model.trim()
                            saved = true
                        },
                    )
                }
            }
            SettingDivider()
            SettingOption(
                title = "生成总结",
                subtitle = if (store.hasLlm) "把最近的学校公告交给你的模型提炼要点" else "先填好上面的中转站配置",
                detail = if (summary != null) "查看" else null,
                onClick = { toggle("summary") },
            )
            if (openSection.value == "summary") {
                SettingDivider()
                SettingEditor {
                    summary?.let { Text(it, fontSize = 13.sp, lineHeight = 18.sp, color = ds.text) }
                        ?: Text(
                            if (store.hasLlm) "点下方按钮生成" else "中转站还没配置好",
                            fontSize = 12.sp, color = ds.sub,
                        )
                    Button(
                        text = if (summarizing) "思考中…" else if (summary == null) "生成总结" else "重新总结",
                        enabled = store.hasLlm && !summarizing,
                        onClick = {
                            summarizing = true
                            summaryError = ""
                            scope.launch {
                                runCatching { repo.summarize() }
                                    .onSuccess { summary = it }
                                    .onFailure { summaryError = it.message ?: "总结失败" }
                                summarizing = false
                            }
                        },
                    )
                    if (summaryError.isNotEmpty()) Text(summaryError, fontSize = 12.sp, color = ds.danger)
                }
            }
        }

        // ── 水电预警 ──
        SectionHeader("水电预警")
        SettingGroup {
            SettingOption(
                title = "低电量阈值",
                subtitle = "剩余电量低于此值时提醒",
                detail = "${store.warnThreshold.toInt()} 度",
                onClick = { toggle("warn") },
            )
            if (openSection.value == "warn") {
                SettingDivider()
                SettingEditor {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        InputField(thresholdText, { thresholdText = it; thresholdSaved = false }, "低电量阈值(度)", KeyboardType.Number, Modifier.weight(1f))
                        InputField(dropText, { dropText = it; thresholdSaved = false }, "掉电速率(度/时)", KeyboardType.Number, Modifier.weight(1f))
                    }
                    Button(if (thresholdSaved) "已保存 ✓" else "保存阈值") {
                        thresholdText.toFloatOrNull()?.let { store.warnThreshold = it }
                        dropText.toFloatOrNull()?.let { store.warnDropRate = it }
                        thresholdSaved = true
                    }
                    Text("后台每 15 分钟巡检，同类预警 12 小时冷却", fontSize = 11.sp, color = ds.sub)
                }
            }
            SettingDivider()
            SettingOption(
                title = "采样间隔",
                subtitle = "记录电量走势的时间间隔",
                detail = "${store.sampleIntervalMinutes} 分钟",
                onClick = { toggle("interval") },
            )
            if (openSection.value == "interval") {
                SettingDivider()
                SettingEditor {
                    SegmentedChoice(
                        options = intervalOptions.map { it to "${it}分" },
                        selected = store.sampleIntervalMinutes,
                        onSelect = { store.sampleIntervalMinutes = it },
                    )
                    Text("间隔越密，走势越细；后台仍按 15 分钟巡检", fontSize = 11.sp, color = ds.sub)
                }
            }
        }

        // ── 备份与恢复 ──
        SectionHeader("备份与恢复")
        SettingGroup {
            SettingOption(
                title = "导出备份",
                subtitle = "课表/校历/待办/偏好/水电绑定存为 JSON（不含登录凭证与密码）",
                onClick = { onExportBackup.launch("gdust-backup-${java.time.LocalDate.now()}.json") },
            )
            SettingDivider()
            SettingOption(
                title = "导入备份",
                subtitle = "从 JSON 恢复；导入会覆盖现有课表与偏好",
                onClick = { importLauncher.launch(arrayOf("application/json", "text/*")) },
            )
            if (backupMsg != null) {
                SettingEditor { Text(backupMsg!!, fontSize = 12.sp, color = ds.sub) }
            }
        }

        // ── 关于（二级页入口）──
        SectionHeader("关于")
        SettingGroup {
            SettingOption(
                title = "个人信息",
                subtitle = "身份、数据快照与隐私说明",
                onClick = onProfile,
            )
            SettingDivider()
            SettingOption(
                title = "关于与更新",
                subtitle = "版本、本版更新说明、数据链路",
                onClick = onAbout,
            )
        }
        if (loggedIn) {
            SettingGroup {
                SettingOption(title = "退出登录并清除本机数据", danger = true, onClick = onLogout)
            }
        }

        Spacer(Modifier.height(110.dp))
    }
    }
}
