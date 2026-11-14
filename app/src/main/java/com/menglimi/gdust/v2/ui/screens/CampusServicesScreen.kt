package com.menglimi.gdust.v2.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.menglimi.gdust.v2.ui.AppUiTokens
import com.menglimi.gdust.v2.ui.LocalDs
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.ChevronForward
import top.yukonga.miuix.kmp.icon.extended.Close
import top.yukonga.miuix.kmp.icon.extended.Search

/** 首页与服务目录共用入口定义，所有操作由 AppRoot 路由。 */
enum class CampusService(val title: String, val description: String, val group: String) {
    SCHEDULE("完整课表", "按周查看课程、教室与教师", "学习"),
    GRADES("学期成绩", "查询成绩、学分与绩点 GPA", "学习"),
    CHX_COURSES("学习通课程", "课程列表与任务点进度", "学习"),
    IMPORT("导入课表", "网页登录教务，导入整学期课程", "学习"),
    TODO("作业与待办", "考试、作业和自定义事项", "校园生活"),
    POWER("宿舍电量", "绑定宿舍，查看用电趋势与历史", "校园生活"),
    NOTICES("学校公告", "门户通知、教务处公告与官网新闻", "校园生活"),
    FIVE_EDUCATION("五育平台", "活动与综合评价 · 需校园网", "校园生活"),
    LESSON_REMINDER("课程提醒", "课前、次日与课中提醒", "提醒与桌面"),
    TODO_REMINDER("待办提醒", "作业、考试与事项的到期提醒", "提醒与桌面"),
    WIDGETS("桌面小部件", "课表、电量、待办 · 九款预览与添加", "提醒与桌面"),
    CHX_LINK("学习通联动", "登录学习通，手动同步作业", "连接与工具"),
    CHX_NOTIFICATIONS("学习通通知识别", "从通知生成待办 · 需授予通知读取权限", "连接与工具"),
    SUMMARY("公告总结", "使用已配置的 AI 服务提炼公告", "连接与工具"),
    SETTINGS("应用设置", "账号、外观、自动同步与数据备份", "连接与工具"),
}

@Composable
fun CampusServicesScreen(
    signedIn: Boolean,
    accountName: String,
    onLogin: () -> Unit,
    onOpen: (CampusService) -> Unit,
    onBack: () -> Unit,
) {
    val ds = LocalDs.current
    val focus = LocalFocusManager.current
    var query by rememberSaveable { mutableStateOf("") }
    val search = query.trim()
    val services = CampusService.entries.filter {
        search.isBlank() || "${it.title} ${it.description} ${it.group}".contains(search, ignoreCase = true)
    }
    BackHandler {
        if (query.isNotEmpty()) { query = ""; focus.clearFocus() } else onBack()
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize().background(ds.bg).statusBarsPadding().imePadding(),
        contentPadding = PaddingValues(horizontal = AppUiTokens.PagePadding, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { PageTitle("校园服务", "学习、生活与提醒，都在这里", onBack) }
        item {
            Row(
                Modifier.fillMaxWidth().background(ds.card, RoundedCornerShape(20.dp)).padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(MiuixIcons.Search, null, Modifier.size(22.dp), tint = ds.sub)
                BasicTextField(
                    value = query, onValueChange = { query = it }, singleLine = true,
                    textStyle = androidx.compose.ui.text.TextStyle(color = ds.text, fontSize = 16.sp),
                    cursorBrush = SolidColor(ds.accent),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { focus.clearFocus() }),
                    modifier = Modifier.weight(1f).padding(14.dp).semantics { contentDescription = "搜索校园服务" },
                    decorationBox = { input ->
                        Box {
                            if (query.isEmpty()) Text("搜索服务、成绩、提醒…", fontSize = 15.sp, color = ds.sub)
                            input()
                        }
                    },
                )
                if (query.isNotEmpty()) IconButton(onClick = { query = "" }, modifier = Modifier.size(48.dp)) {
                    Icon(MiuixIcons.Close, "清除搜索", Modifier.size(20.dp), tint = ds.sub)
                }
            }
        }
        if (search.isBlank()) item {
            DashboardCard {
                ServiceActionRow(
                    title = if (signedIn) accountName.ifBlank { "账号与个人信息" } else "登录校园账号",
                    description = if (signedIn) "账号信息与同步设置" else "登录后同步课表；电量和本地待办也可独立使用",
                    onClick = { if (signedIn) onOpen(CampusService.SETTINGS) else onLogin() },
                )
            }
        }
        services.groupBy { it.group }.forEach { (group, entries) ->
            item(key = "heading-$group") {
                Text(group, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = ds.sub,
                    modifier = Modifier.padding(start = 4.dp, top = 8.dp).semantics { heading() })
            }
            item(key = group) {
                DashboardCard {
                    entries.forEach { service ->
                        ServiceActionRow(service.title, service.description, onClick = { onOpen(service) })
                    }
                }
            }
        }
        if (services.isEmpty()) item {
            DashboardCard(insideMargin = PaddingValues(20.dp)) {
                Text("没有找到相关服务", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = ds.text)
                Text("试试“课表”“学习通”或“提醒”。", fontSize = 14.sp, color = ds.sub,
                    modifier = Modifier.padding(top = 8.dp))
            }
        }
        item { Spacer(Modifier.navigationBarsPadding().height(12.dp)) }
    }
}

/** 实底数据卡：按下只加轻微 tint，字号与留白负责分层。 */
@Composable
internal fun DashboardCard(
    modifier: Modifier = Modifier,
    insideMargin: PaddingValues = PaddingValues(0.dp),
    color: Color = LocalDs.current.card,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val ds = LocalDs.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Card(
        modifier = modifier.fillMaxWidth().then(
            if (onClick != null) Modifier.clickable(interactionSource = interaction, indication = null,
                role = Role.Button, onClick = onClick) else Modifier,
        ),
        cornerRadius = AppUiTokens.CardCorner,
        colors = CardDefaults.defaultColors(color = color, contentColor = ds.text),
    ) {
        Column(Modifier.fillMaxWidth()
            .background(if (pressed) ds.accent.copy(alpha = 0.07f) else Color.Transparent)
            .padding(insideMargin), content = content)
    }
}

@Composable
internal fun ServiceActionRow(
    title: String,
    description: String,
    onClick: () -> Unit,
    detail: String? = null,
) {
    val ds = LocalDs.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Row(
        Modifier.fillMaxWidth().heightIn(min = 76.dp)
            .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick)
            .background(if (pressed) ds.accent.copy(alpha = 0.07f) else Color.Transparent)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(title, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = ds.text)
            Text(description, fontSize = 13.sp, lineHeight = 19.sp, color = ds.sub)
            if (detail != null) Text(detail, fontSize = 12.sp, color = ds.accent)
        }
        Icon(MiuixIcons.ChevronForward, null, Modifier.size(18.dp), tint = ds.sub)
    }
}
