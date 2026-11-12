// Adapted from NexioSchedule 1.7, revision 28a99a3.
// Sources under ui-shared/src/commonMain/kotlin/com/haooz/chedule/ui/activities/:
// today/TodayScreen.kt (period headings / cards / CourseItemContent spacing),
// CourseManageScreen.kt (16dp cards and metadata rows).
// Nexio has no matching Gdust todo model: only the card/row presentation is adapted;
// Store.observeTodos and atomic Store.updateTodos remain the source of truth.
package com.menglimi.gdust.v2.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import com.menglimi.gdust.v2.data.Store
import com.menglimi.gdust.v2.ui.pageBackground
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
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

@Composable
fun TodoScreen(repo: Repository) {
    val store = repo.store
    val colors = MiuixTheme.colorScheme
    var items by remember(store) { mutableStateOf(store.loadTodos()) }
    var draft by rememberSaveable { mutableStateOf("") }
    val focusManager = LocalFocusManager.current
    DisposableEffect(store) {
        val stop = store.observeTodos { items = store.loadTodos() }
        items = store.loadTodos()
        onDispose { stop() }
    }
    val pending = remember(items) { items.filterNot { it.done } }
    val completed = remember(items) { items.filter { it.done } }
    val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    fun addDraft() {
        val text = draft.trim()
        if (text.isEmpty()) return
        items = store.addTodo(text)
        draft = ""
        focusManager.clearFocus()
    }
    fun toggleTodo(id: Long) {
        // Read/transform under Store's lock, including when a notification adds a todo.
        items = store.updateTodos { current ->
            current.map { if (it.id == id) it.copy(done = !it.done) else it }
        }
    }
    fun deleteTodo(id: Long) {
        items = store.updateTodos { current -> current.filterNot { it.id == id } }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(pageBackground()).statusBarsPadding().imePadding(),
        contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 104.dp + bottomInset),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "todo-heading") {
            Column(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "待办",
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Normal,
                        color = colors.onSurface,
                        modifier = Modifier.semantics { heading() },
                    )
                    Text(
                        text = pending.size.toString(),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Medium,
                        color = colors.primary,
                        modifier = Modifier.background(colors.primary.copy(alpha = 0.10f), RoundedCornerShape(12.dp))
                            .padding(horizontal = 12.dp, vertical = 5.dp)
                            .semantics { contentDescription = "${pending.size} 项待完成" },
                    )
                }
                Text(
                    text = if (items.isEmpty()) "把重要的事记在这里" else "待完成 ${pending.size} 项，已完成 ${completed.size} 项",
                    fontSize = 14.sp,
                    color = colors.onSurfaceVariantSummary,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }
        item(key = "todo-input") {
            Card(modifier = Modifier.fillMaxWidth(), cornerRadius = 20.dp, insideMargin = PaddingValues(16.dp)) {
                Text("添加待办", fontSize = 16.sp, fontWeight = FontWeight.Medium, color = colors.onSurface)
                Spacer(Modifier.height(12.dp))
                BasicTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
                        .clip(RoundedCornerShape(12.dp)).background(colors.surface)
                        .padding(horizontal = 14.dp, vertical = 14.dp)
                        .semantics { contentDescription = "待办内容" },
                    textStyle = MiuixTheme.textStyles.body1.copy(fontSize = 16.sp, color = colors.onSurface),
                    singleLine = true,
                    cursorBrush = SolidColor(colors.primary),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { addDraft() }),
                    decorationBox = { innerTextField ->
                        Box {
                            if (draft.isEmpty()) {
                                Text("作业、考试或要做的事", fontSize = 16.sp, color = colors.onSurfaceVariantSummary)
                            }
                            innerTextField()
                        }
                    },
                )
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = { addDraft() },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = draft.isNotBlank(),
                    minHeight = 48.dp,
                    colors = ButtonDefaults.buttonColorsPrimary(),
                ) {
                    Icon(MiuixIcons.Add, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("添加", fontSize = 16.sp, fontWeight = FontWeight.Medium)
                }
            }
        }
        item(key = "todo-pending-heading") {
            TodoGroupHeading("待完成", pending.size)
        }
        if (pending.isEmpty()) {
            item(key = "todo-pending-empty") {
                TodoEmptyCard(
                    title = if (completed.isEmpty()) "还没有待办" else "待办都完成了",
                    message = if (completed.isEmpty()) "在上方输入一件事，点击添加开始记录。" else "已完成的事项保留在下方，也可以重新勾选。",
                )
            }
        } else {
            items(pending, key = { item -> "todo-${item.id}" }) { item ->
                TodoItemCard(item, onToggle = { toggleTodo(item.id) }, onDelete = { deleteTodo(item.id) })
            }
        }
        if (completed.isNotEmpty()) {
            item(key = "todo-completed-heading") {
                TodoGroupHeading("已完成", completed.size)
            }
            items(completed, key = { item -> "todo-${item.id}" }) { item ->
                TodoItemCard(item, onToggle = { toggleTodo(item.id) }, onDelete = { deleteTodo(item.id) })
            }
            item(key = "todo-clear-completed") {
                TextButton(
                    text = "清理已完成（${completed.size}）",
                    onClick = { items = store.updateTodos { current -> current.filterNot { it.done } } },
                    modifier = Modifier.fillMaxWidth(),
                    minHeight = 48.dp,
                    colors = ButtonDefaults.textButtonColors(textColor = colors.primary),
                )
            }
        }
    }
}

@Composable
private fun TodoGroupHeading(title: String, count: Int) {
    Text(
        text = "$title  $count",
        fontSize = 14.sp,
        fontWeight = FontWeight.Medium,
        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        modifier = Modifier.padding(start = 4.dp, top = 8.dp).semantics { heading() },
    )
}

@Composable
private fun TodoItemCard(item: Store.Todo, onToggle: () -> Unit, onDelete: () -> Unit) {
    val colors = MiuixTheme.colorScheme
    Card(modifier = Modifier.fillMaxWidth(), cornerRadius = 16.dp) {
        Row(
            Modifier.fillMaxWidth().padding(end = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // The whole text/checkbox region is one accessible checkbox; delete is a
            // sibling 48dp target so tapping it never toggles completion as a side effect.
            Row(
                modifier = Modifier.weight(1f).heightIn(min = 64.dp)
                    .toggleable(value = item.done, role = Role.Checkbox, onValueChange = { onToggle() })
                    .semantics { stateDescription = if (item.done) "已完成" else "待完成" }
                    .padding(start = 4.dp, top = 8.dp, end = 6.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                    Checkbox(
                        state = if (item.done) ToggleableState.On else ToggleableState.Off,
                        onClick = null,
                        modifier = Modifier.clearAndSetSemantics { },
                    )
                }
                Text(
                    item.text,
                    fontSize = 16.sp,
                    lineHeight = 24.sp,
                    color = if (item.done) colors.onSurfaceVariantSummary else colors.onSurface,
                    textDecoration = if (item.done) TextDecoration.LineThrough else null,
                    modifier = Modifier.weight(1f),
                )
            }
            IconButton(
                onClick = onDelete,
                modifier = Modifier.size(48.dp).semantics { contentDescription = "删除待办：${item.text}" },
                minWidth = 48.dp,
                minHeight = 48.dp,
            ) {
                Icon(MiuixIcons.Delete, contentDescription = null, tint = colors.primary, modifier = Modifier.size(22.dp))
            }
        }
    }
}

@Composable
private fun TodoEmptyCard(title: String, message: String) {
    val colors = MiuixTheme.colorScheme
    Card(modifier = Modifier.fillMaxWidth(), cornerRadius = 20.dp, insideMargin = PaddingValues(24.dp)) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(MiuixIcons.Tasks, contentDescription = null, tint = colors.primary, modifier = Modifier.size(36.dp))
            Text(title, fontSize = 17.sp, fontWeight = FontWeight.Medium, color = colors.onSurface)
            Text(message, fontSize = 14.sp, lineHeight = 21.sp, textAlign = TextAlign.Center, color = colors.onSurfaceVariantSummary)
        }
    }
}
