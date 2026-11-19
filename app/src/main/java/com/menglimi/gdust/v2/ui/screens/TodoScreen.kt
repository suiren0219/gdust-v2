// Todo grouping and storage are GDUST local logic; presentation follows CZeroX.
package com.menglimi.gdust.v2.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.menglimi.gdust.v2.data.Repository
import com.menglimi.gdust.v2.data.Todo
import com.menglimi.gdust.v2.data.TodoKind
import com.menglimi.gdust.v2.data.classifyNotificationTodo
import com.menglimi.gdust.v2.data.groupTodos
import com.menglimi.gdust.v2.ui.AppCard
import com.menglimi.gdust.v2.ui.AppUiTokens
import com.menglimi.gdust.v2.ui.LocalDs
import com.menglimi.gdust.v2.widget.LocalWidgetTodoId
import kotlinx.coroutines.delay
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Checkbox
import top.yukonga.miuix.kmp.basic.CheckboxDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val dueFormat = SimpleDateFormat("MM-dd HH:mm", Locale.CHINA)

@Composable
fun TodoScreen(repo: Repository) {
    val store = repo.store
    val ds = LocalDs.current
    var items by remember(store) { mutableStateOf(store.loadTodos()) }
    var draft by rememberSaveable { mutableStateOf("") }
    val focusManager = LocalFocusManager.current
    DisposableEffect(store) {
        val stop = store.observeTodos { items = store.loadTodos() }
        items = store.loadTodos()
        onDispose { stop() }
    }
    val nowMs = System.currentTimeMillis()
    val groups = groupTodos(items, nowMs)
    val pendingAll = groups.overdue + groups.today + groups.thisWeek + groups.later + groups.noDue
    val exams = pendingAll.filter { it.kind == TodoKind.EXAM }.sortedBy { it.dueAt }
    val byCourse = pendingAll.filter { it.kind != TodoKind.EXAM }
        .groupBy { it.course.ifBlank { "其他" } }
        .entries
        .sortedWith(
            compareBy(
                { it.key == "其他" },
                { e -> e.value.filter { t -> t.dueAt > 0L }.minOfOrNull { t -> t.dueAt } ?: Long.MAX_VALUE },
                { it.key },
            ),
        )
        .map { it.key to it.value.sortedWith(compareBy({ it.dueAt <= 0L }, { it.dueAt })) }
    // 已完成默认折叠；小部件的旧卡片仍可定位已经完成的同一个 ID。
    var doneExpanded by rememberSaveable { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val widgetTodo = LocalWidgetTodoId.current
    val requestedId = widgetTodo?.value
    var highlightedId by remember { mutableStateOf<Long?>(null) }
    // 页头含统计卡，仍占一个 item；加上输入卡，分组起始索引保持为 2。
    val targetIndex = requestedId?.let { id ->
        var index = 2
        var found: Int? = null
        if (exams.isNotEmpty()) {
            index++
            val offset = exams.indexOfFirst { it.id == id }
            if (offset >= 0) found = index + offset
            index += exams.size
        }
        byCourse.forEach { (_, courseItems) ->
            index++
            val offset = courseItems.indexOfFirst { it.id == id }
            if (offset >= 0) found = index + offset
            index += courseItems.size
        }
        if (groups.pendingCount == 0) index++
        val doneOffset = groups.completed.indexOfFirst { it.id == id }
        if (doneOffset >= 0 && doneExpanded) found = index + 1 + doneOffset
        found
    }
    LaunchedEffect(requestedId, targetIndex, items) {
        val id = requestedId ?: return@LaunchedEffect
        val target = items.firstOrNull { it.id == id }
        if (target == null) {
            widgetTodo?.value = null
        } else if (target.done && !doneExpanded) {
            doneExpanded = true
        } else if (targetIndex != null) {
            listState.scrollToItem(targetIndex)
            highlightedId = id
            widgetTodo?.value = null
        }
    }
    LaunchedEffect(highlightedId) {
        if (highlightedId != null) {
            delay(3_000L)
            highlightedId = null
        }
    }
    val startOfToday = java.time.Instant.ofEpochMilli(nowMs)
        .atZone(java.time.ZoneId.systemDefault())
        .toLocalDate().atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
    val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val addInteraction = remember { MutableInteractionSource() }
    val addPressed by addInteraction.collectIsPressedAsState()

    fun addDraft() {
        val text = draft.trim()
        if (text.isEmpty()) return
        val (kind, course) = classifyNotificationTodo(text)
        items = store.addTodo(text, kind = kind, course = course)
        draft = ""
        focusManager.clearFocus()
    }
    fun setDone(id: Long, done: Boolean) {
        items = store.setTodoDone(id, done)
    }
    fun deleteTodo(id: Long) {
        items = store.updateTodos { current -> current.filterNot { it.id == id } }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize().background(ds.bg).statusBarsPadding().imePadding(),
        contentPadding = PaddingValues(
            start = AppUiTokens.PagePadding,
            end = AppUiTokens.PagePadding,
            top = 24.dp,
            bottom = 104.dp + bottomInset,
        ),
        verticalArrangement = Arrangement.spacedBy(AppUiTokens.CardGap),
    ) {
        item(key = "todo-heading") {
            Column(Modifier.fillMaxWidth()) {
                Text("待办", fontSize = AppUiTokens.TitleSize, fontWeight = AppUiTokens.TitleWeight,
                    color = ds.text, modifier = Modifier.semantics { heading() })
                Text("考试优先 · 按课程与截止时间整理", fontSize = 13.sp, lineHeight = 20.sp,
                    color = ds.sub, modifier = Modifier.padding(top = 8.dp))
                Row(
                    Modifier.fillMaxWidth().padding(top = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(AppUiTokens.CardGap),
                ) {
                    TodoMetricCard("待完成", groups.pendingCount, Modifier.weight(1f))
                    TodoMetricCard("已完成", groups.completed.size, Modifier.weight(1f))
                }
            }
        }
        item(key = "todo-input") {
            AppCard(modifier = Modifier.fillMaxWidth(), insideMargin = PaddingValues(AppUiTokens.CardPadding)) {
                Text("添加待办", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = ds.text)
                Row(
                    Modifier.fillMaxWidth().padding(top = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    BasicTextField(
                        value = draft,
                        onValueChange = { draft = it },
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                            .padding(vertical = 12.dp)
                            .semantics { contentDescription = "待办内容" },
                        textStyle = MiuixTheme.textStyles.body1.copy(fontSize = 16.sp, color = ds.text),
                        singleLine = true,
                        cursorBrush = SolidColor(ds.accent),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { addDraft() }),
                        decorationBox = { inner ->
                            Box {
                                if (draft.isEmpty()) Text("作业、考试或要做的事", fontSize = 16.sp, color = ds.sub)
                                inner()
                            }
                        },
                    )
                    TextButton(
                        text = "添加",
                        onClick = { addDraft() },
                        enabled = draft.isNotBlank(),
                        minHeight = 48.dp,
                        colors = ButtonDefaults.textButtonColors(
                            color = if (addPressed) ds.accent.copy(alpha = 0.07f).compositeOver(ds.card) else ds.card,
                            disabledColor = ds.card,
                            textColor = ds.accent,
                            disabledTextColor = ds.sub,
                            alpha = 1f,
                        ),
                        interactionSource = addInteraction,
                        indication = null,
                    )
                }
            }
        }

        // 考试优先独立成段（跨课程、按截止时间排序）
        if (exams.isNotEmpty()) {
            item(key = "head-exam") { TodoGroupHeading("考试", exams.size) }
            items(exams, key = { "exam-${it.id}" }) { exam ->
                TodoItemCard(exam, onToggle = { setDone(exam.id, it) }, onDelete = { deleteTodo(exam.id) },
                    highlighted = highlightedId == exam.id)
            }
        }

        // 课程优先分组：课程名作组头，组内有截止按时间升序、无截止垫底；
        // 课程按「组内最早截止」排序，没截止的课靠后，无课程的归「其他」垫底。
        byCourse.forEach { (course, courseItems) ->
            item(key = "head-course-$course") {
                val overdue = courseItems.count { it.dueAt in 1 until startOfToday }
                TodoGroupHeading(
                    title = course,
                    count = courseItems.size,
                    suffix = if (overdue > 0) "$overdue 项已逾期" else "",
                )
            }
            items(
                courseItems,
                key = { "todo-${it.id}" },
            ) { item ->
                TodoItemCard(item, onToggle = { setDone(item.id, it) }, onDelete = { deleteTodo(item.id) },
                    highlighted = highlightedId == item.id)
            }
        }
        if (groups.pendingCount == 0) {
            item(key = "todo-empty") {
                TodoEmptyCard("待办都完成了", "已完成的事项保留在下方，也可以重新勾选。")
            }
        }
        if (groups.completed.isNotEmpty()) {
            item(key = "head-done") {
                AppCard(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                        .semantics { stateDescription = if (doneExpanded) "已展开" else "已折叠" },
                    insideMargin = PaddingValues(AppUiTokens.CardPadding),
                    onClick = { doneExpanded = !doneExpanded },
                ) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("已完成  ${groups.completed.size}", fontSize = 18.sp, fontWeight = FontWeight.SemiBold,
                            color = ds.text, modifier = Modifier.weight(1f).semantics { heading() })
                        Text(if (doneExpanded) "收起" else "展开", fontSize = 13.sp,
                            color = ds.accent, modifier = Modifier.padding(start = 12.dp))
                    }
                    Text("已完成事项可以随时恢复", fontSize = 13.sp, lineHeight = 20.sp,
                        color = ds.sub, modifier = Modifier.padding(top = 6.dp))
                }
            }
            if (doneExpanded) {
                items(groups.completed, key = { "done-${it.id}" }) { item ->
                    TodoItemCard(item, onToggle = { setDone(item.id, it) }, onDelete = { deleteTodo(item.id) },
                        highlighted = highlightedId == item.id)
                }
                item(key = "clear-done") {
                    AppCard(
                        modifier = Modifier.fillMaxWidth(),
                        insideMargin = PaddingValues(AppUiTokens.CardPadding),
                        onClick = { items = store.updateTodos { c -> c.filterNot { it.done } } },
                    ) {
                        Text("清理已完成", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = ds.text)
                        Text("删除 ${groups.completed.size} 项已完成事项", fontSize = 13.sp, lineHeight = 20.sp,
                            color = ds.sub, modifier = Modifier.padding(top = 6.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun TodoMetricCard(label: String, value: Int, modifier: Modifier = Modifier) {
    val ds = LocalDs.current
    AppCard(modifier = modifier, insideMargin = PaddingValues(AppUiTokens.CardPadding)) {
        Text(label, fontSize = 13.sp, color = ds.sub)
        Text(value.toString(), fontSize = 36.sp, fontWeight = FontWeight.Bold, color = ds.text,
            modifier = Modifier.padding(top = 8.dp))
    }
}

@Composable
private fun TodoGroupHeading(title: String, count: Int, suffix: String = "") {
    val ds = LocalDs.current
    Column(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 2.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, fontSize = 17.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold,
                color = ds.text, modifier = Modifier.weight(1f).semantics { heading() })
            Text("$count 项", fontSize = 13.sp, lineHeight = 24.sp, color = ds.sub)
        }
        if (suffix.isNotBlank()) {
            Text(suffix, fontSize = 13.sp, lineHeight = 20.sp, color = ds.danger,
                modifier = Modifier.padding(top = 4.dp))
        }
    }
}

@Composable
private fun TodoItemCard(item: Todo, onToggle: (Boolean) -> Unit, onDelete: () -> Unit, highlighted: Boolean = false) {
    val ds = LocalDs.current
    val toggleInteraction = remember { MutableInteractionSource() }
    val togglePressed by toggleInteraction.collectIsPressedAsState()
    val deleteInteraction = remember { MutableInteractionSource() }
    val deletePressed by deleteInteraction.collectIsPressedAsState()
    val cardColor = if (highlighted || togglePressed) ds.accent.copy(alpha = 0.07f).compositeOver(ds.card) else ds.card
    AppCard(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = AppUiTokens.CardCorner,
        insideMargin = PaddingValues(AppUiTokens.CardPadding),
        color = cardColor,
    ) {
        val label = when (item.kind) {
            TodoKind.EXAM -> "考试"
            TodoKind.HOMEWORK -> "作业"
            TodoKind.GENERAL -> "事项"
        }
        Column(
            Modifier.fillMaxWidth()
                .toggleable(
                    value = item.done,
                    interactionSource = toggleInteraction,
                    indication = null,
                    role = Role.Checkbox,
                    onValueChange = onToggle,
                )
                .semantics { stateDescription = if (item.done) "已完成" else "待完成" },
        ) {
            Text(
                buildList {
                    add(label)
                    if (item.course.isNotBlank()) add(item.course)
                }.joinToString(" · "),
                fontSize = 13.sp,
                lineHeight = 20.sp,
                color = ds.sub,
            )
            Text(item.text, fontSize = 18.sp, lineHeight = 26.sp, fontWeight = FontWeight.SemiBold,
                color = ds.text, textDecoration = if (item.done) TextDecoration.LineThrough else null,
                modifier = Modifier.padding(top = 10.dp))
            Text(
                text = if (item.dueAt > 0L) "${if (item.kind == TodoKind.EXAM) "考试时间" else "截止"} · ${dueFormat.format(Date(item.dueAt))}"
                else "未设置截止时间",
                fontSize = 14.sp,
                lineHeight = 21.sp,
                color = if (item.dueAt > 0L) ds.text else ds.sub,
                modifier = Modifier.padding(top = 10.dp),
            )
        }
        Row(
            Modifier.fillMaxWidth().padding(top = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                Modifier.weight(1f).heightIn(min = 48.dp)
                    .toggleable(
                        value = item.done,
                        interactionSource = toggleInteraction,
                        indication = null,
                        role = Role.Checkbox,
                        onValueChange = onToggle,
                    )
                    .semantics {
                        contentDescription = if (item.done) "恢复待办：${item.text}" else "完成待办：${item.text}"
                        stateDescription = if (item.done) "已完成" else "待完成"
                    },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                // 交互由外层负责；关闭控件自身按压缩放，并保持正常的状态颜色。
                Checkbox(
                    state = if (item.done) ToggleableState.On else ToggleableState.Off,
                    onClick = null,
                    enabled = false,
                    colors = CheckboxDefaults.checkboxColors(
                        disabledCheckedBackgroundColor = ds.accent,
                        disabledCheckedForegroundColor = ds.card,
                        disabledUncheckedBackgroundColor = ds.sub.copy(alpha = 0.18f).compositeOver(cardColor),
                        disabledUncheckedForegroundColor = ds.sub,
                    ),
                    modifier = Modifier.clearAndSetSemantics { },
                )
                Text(if (item.done) "已完成" else "标记完成", fontSize = 13.sp, color = ds.sub)
            }
            Box(
                modifier = Modifier.size(48.dp).clip(CircleShape)
                    .background(if (deletePressed) ds.text.copy(alpha = 0.06f).compositeOver(cardColor) else cardColor)
                    .clickable(interactionSource = deleteInteraction, indication = null, role = Role.Button, onClick = onDelete)
                    .semantics { contentDescription = "删除待办：${item.text}" },
                contentAlignment = Alignment.Center,
            ) {
                Icon(MiuixIcons.Delete, contentDescription = null, tint = ds.sub, modifier = Modifier.size(22.dp))
            }
        }
    }
}

@Composable
private fun TodoEmptyCard(title: String, message: String) {
    val ds = LocalDs.current
    AppCard(modifier = Modifier.fillMaxWidth(), insideMargin = PaddingValues(AppUiTokens.CardPadding)) {
        Text(title, fontSize = 18.sp, lineHeight = 26.sp, fontWeight = FontWeight.SemiBold, color = ds.text)
        Text(message, fontSize = 13.sp, lineHeight = 20.sp, color = ds.sub, modifier = Modifier.padding(top = 8.dp))
    }
}
