// Adapted from NexioSchedule 28a99a3 course/task card presentation.
// Structure (kind/course/dueAt) and grouping are GDUST local adaptations.
package com.menglimi.gdust.v2.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.style.TextAlign
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
import com.menglimi.gdust.v2.ui.LocalWallpaperActive
import com.menglimi.gdust.v2.ui.pageBackground
import com.menglimi.gdust.v2.ui.pageTextBacking
import com.menglimi.gdust.v2.ui.secondaryTextColor
import com.menglimi.gdust.v2.widget.LocalWidgetTodoId
import kotlinx.coroutines.delay
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Checkbox
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Add
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.icon.extended.Tasks
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val dueFormat = SimpleDateFormat("MM-dd HH:mm", Locale.CHINA)

@Composable
fun TodoScreen(repo: Repository) {
    val store = repo.store
    val colors = MiuixTheme.colorScheme
    val secondary = secondaryTextColor()
    val onWallpaper = LocalWallpaperActive.current
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
    // 索引与下方 LazyColumn 的标题/输入/分组布局一一对应，避免只滚到当前已可见的条目。
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
        modifier = Modifier.fillMaxSize().background(pageBackground()).statusBarsPadding().imePadding(),
        contentPadding = PaddingValues(start = AppUiTokens.PagePadding, end = AppUiTokens.PagePadding, top = 24.dp, bottom = 104.dp + bottomInset),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "todo-heading") {
            Column(
                Modifier.fillMaxWidth().padding(bottom = 12.dp)
                    .pageTextBacking().padding(if (onWallpaper) 12.dp else 0.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("待办", fontSize = AppUiTokens.TitleSize, fontWeight = AppUiTokens.TitleWeight, color = colors.onSurface,
                        modifier = Modifier.semantics { heading() })
                    Text("${groups.pendingCount}", fontSize = 18.sp, fontWeight = FontWeight.Medium, color = colors.primary,
                        modifier = Modifier.background(colors.primary.copy(alpha = 0.10f), RoundedCornerShape(12.dp))
                            .padding(horizontal = 12.dp, vertical = 5.dp)
                            .semantics { contentDescription = "${groups.pendingCount} 项待完成" })
                }
                Text("按时间 · 课程 · 类型分组；考试与作业自动标记",
                    fontSize = 14.sp, lineHeight = 20.sp, color = secondary,
                    modifier = Modifier.padding(top = 6.dp))
            }
        }
        item(key = "todo-input") {
            AppCard(modifier = Modifier.fillMaxWidth(), cornerRadius = 20.dp, insideMargin = PaddingValues(16.dp)) {
                Text("添加待办", fontSize = 16.sp, fontWeight = FontWeight.Medium, color = colors.onSurface)
                Spacer(Modifier.height(12.dp))
                BasicTextField(
                    value = draft, onValueChange = { draft = it },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
                        .clip(RoundedCornerShape(12.dp)).background(colors.surface)
                        .padding(horizontal = 14.dp, vertical = 14.dp)
                        .semantics { contentDescription = "待办内容" },
                    textStyle = MiuixTheme.textStyles.body1.copy(fontSize = 16.sp, color = colors.onSurface),
                    singleLine = true, cursorBrush = SolidColor(colors.primary),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { addDraft() }),
                    decorationBox = { inner ->
                        Box {
                            if (draft.isEmpty()) Text("作业、考试或要做的事", fontSize = 16.sp, color = secondary)
                            inner()
                        }
                    },
                )
                Spacer(Modifier.height(12.dp))
                Button(onClick = { addDraft() }, modifier = Modifier.fillMaxWidth(), enabled = draft.isNotBlank(),
                    minHeight = 48.dp, colors = ButtonDefaults.buttonColorsPrimary()) {
                    Icon(MiuixIcons.Add, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("添加", fontSize = 16.sp, fontWeight = FontWeight.Medium)
                }
            }
        }

        // 考试优先独立成段（跨课程、按截止时间排序）
        if (exams.isNotEmpty()) {
            item(key = "head-exam") { TodoGroupHeading("考试", exams.size, emphasize = true) }
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
                    emphasize = overdue > 0,
                    suffix = if (overdue > 0) "· $overdue 项已逾期" else "",
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
                Row(
                    Modifier.fillMaxWidth().padding(top = 8.dp).pageTextBacking()
                        .toggleable(value = doneExpanded, role = Role.Button, onValueChange = { doneExpanded = it })
                        .heightIn(min = 48.dp)
                        .padding(horizontal = if (onWallpaper) 12.dp else 0.dp, vertical = if (onWallpaper) 6.dp else 0.dp)
                        .semantics { stateDescription = if (doneExpanded) "已展开" else "已折叠" },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("已完成  ${groups.completed.size}", fontSize = 14.sp, fontWeight = FontWeight.Medium,
                        color = secondary, modifier = Modifier.weight(1f).semantics { heading() })
                    Text(
                        if (doneExpanded) "收起" else "展开",
                        fontSize = 13.sp, color = colors.primary,
                        modifier = Modifier.padding(start = 12.dp),
                    )
                }
            }
            if (doneExpanded) {
                items(groups.completed, key = { "done-${it.id}" }) { item ->
                    TodoItemCard(item, onToggle = { setDone(item.id, it) }, onDelete = { deleteTodo(item.id) },
                        highlighted = highlightedId == item.id)
                }
                item(key = "clear-done") {
                    TextButton(text = "清理已完成（${groups.completed.size}）",
                        onClick = { items = store.updateTodos { c -> c.filterNot { it.done } } },
                        modifier = Modifier.fillMaxWidth().pageTextBacking(cornerRadius = ButtonDefaults.CornerRadius), minHeight = 48.dp,
                        colors = ButtonDefaults.textButtonColors(textColor = colors.primary))
                }
            }
        }
    }
}

@Composable
private fun TodoGroupHeading(
    title: String,
    count: Int,
    emphasize: Boolean = false,
    suffix: String = "",
) {
    val colors = MiuixTheme.colorScheme
    val secondary = secondaryTextColor()
    val onWallpaper = LocalWallpaperActive.current
    Text("$title  $count${suffix.replace("  ", " ")}", fontSize = 14.sp, fontWeight = FontWeight.Medium,
        color = if (emphasize) colors.error else secondary,
        modifier = Modifier.padding(top = 8.dp).pageTextBacking()
            .padding(horizontal = if (onWallpaper) 12.dp else 0.dp, vertical = if (onWallpaper) 6.dp else 0.dp)
            .semantics { heading() })
}

@Composable
private fun TodoItemCard(item: Todo, onToggle: (Boolean) -> Unit, onDelete: () -> Unit, highlighted: Boolean = false) {
    val colors = MiuixTheme.colorScheme
    val summary = secondaryTextColor()
    AppCard(modifier = Modifier.fillMaxWidth().then(
        if (highlighted) Modifier.border(2.dp, colors.primary, RoundedCornerShape(16.dp)) else Modifier,
    ), cornerRadius = 16.dp) {
        Column(Modifier.fillMaxWidth().padding(start = 4.dp, top = 6.dp, end = 6.dp, bottom = 6.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                val (label, tint) = when (item.kind) {
                    TodoKind.EXAM -> "考试" to colors.error
                    TodoKind.HOMEWORK -> "作业" to colors.primary
                    TodoKind.GENERAL -> "事项" to summary
                }
                Text(label, fontSize = 10.sp, fontWeight = FontWeight.Medium, color = tint,
                    modifier = Modifier.background(tint.copy(alpha = 0.10f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp))
                if (item.dueAt > 0L) Text(dueFormat.format(Date(item.dueAt)), fontSize = 10.sp, color = summary)
                if (item.course.isNotBlank()) Text(item.course, fontSize = 10.sp, color = summary, maxLines = 1)
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Row(
                    modifier = Modifier.weight(1f).heightIn(min = 56.dp)
                        .toggleable(value = item.done, role = Role.Checkbox, onValueChange = onToggle)
                        .semantics { stateDescription = if (item.done) "已完成" else "待完成" }
                        .padding(top = 2.dp, bottom = 2.dp, end = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) {
                        Checkbox(state = if (item.done) ToggleableState.On else ToggleableState.Off,
                            onClick = null, modifier = Modifier.clearAndSetSemantics { })
                    }
                    Text(item.text, fontSize = 16.sp, lineHeight = 24.sp,
                        color = if (item.done) colors.onSurfaceVariantSummary else colors.onSurface,
                        textDecoration = if (item.done) TextDecoration.LineThrough else null,
                        modifier = Modifier.weight(1f))
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(48.dp)
                    .semantics { contentDescription = "删除待办：${item.text}" },
                    minWidth = 48.dp, minHeight = 48.dp) {
                    Icon(MiuixIcons.Delete, contentDescription = null, tint = colors.primary, modifier = Modifier.size(22.dp))
                }
            }
        }
    }
}

@Composable
private fun TodoEmptyCard(title: String, message: String) {
    val colors = MiuixTheme.colorScheme
    val secondary = secondaryTextColor()
    AppCard(modifier = Modifier.fillMaxWidth(), cornerRadius = 20.dp, insideMargin = PaddingValues(24.dp)) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(MiuixIcons.Tasks, contentDescription = null, tint = colors.primary, modifier = Modifier.size(36.dp))
            Text(title, fontSize = 17.sp, fontWeight = FontWeight.Medium, color = colors.onSurface)
            Text(message, fontSize = 14.sp, lineHeight = 21.sp, textAlign = TextAlign.Center,
                color = secondary)
        }
    }
}
