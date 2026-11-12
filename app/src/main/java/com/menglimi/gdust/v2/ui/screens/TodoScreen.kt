package com.menglimi.gdust.v2.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.menglimi.gdust.v2.data.Repository
import com.menglimi.gdust.v2.data.Store
import com.menglimi.gdust.v2.ui.GlassCard
import com.menglimi.gdust.v2.ui.LocalDs
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun TodoScreen(repo: Repository) {
    val ds = LocalDs.current
    var items by remember { mutableStateOf(repo.store.loadTodos()) }
    var draft by remember { mutableStateOf("") }
    var hint by remember { mutableStateOf("") }
    DisposableEffect(repo) {
        val stop = repo.store.observeTodos { items = repo.store.loadTodos() }
        items = repo.store.loadTodos()
        onDispose { stop() }
    }

    // 未完成在上（有截止的按时间排序，没截止的垫底），已完成折叠在下面的卡里
    val pending = items.filterNot { it.done }
        .sortedWith(compareBy({ it.due <= 0L }, { it.due }))
    val doneItems = items.filter { it.done }

    Column(
        Modifier
            .fillMaxSize()
            .background(com.menglimi.gdust.v2.ui.pageBackground())
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
    ) {
        Spacer(Modifier.height(24.dp))
        PageTitle(title = "待办", subtitle = "考试 · 作业 · 缴费 · 事项，都在这里")

        GlassCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                BasicTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    modifier = Modifier.weight(1f).padding(vertical = 4.dp),
                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 16.sp, color = ds.text),
                    singleLine = true,
                    cursorBrush = SolidColor(ds.accent),
                    decorationBox = { inner ->
                        Box {
                            if (draft.isEmpty()) {
                                Text("记一笔：期末考试 / 作业 / 缴费截止…", fontSize = 15.sp, color = ds.sub)
                            }
                            inner()
                        }
                    },
                )
                Text(
                    if (draft.isBlank()) "输入内容" else "添加",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (draft.isBlank()) ds.sub else ds.accent,
                    modifier = Modifier
                        // 空输入也能点：给反馈而不是静默无视
                        .clickable {
                            if (draft.isBlank()) {
                                hint = "先在上面写点什么，再点添加"
                            } else {
                                items = repo.store.addTodo(draft.trim())
                                draft = ""
                                hint = ""
                            }
                        }
                        .padding(start = 12.dp, top = 8.dp, bottom = 8.dp),
                )
            }
        }

        if (hint.isNotEmpty()) {
            Text(hint, fontSize = 12.sp, color = ds.danger, modifier = Modifier.padding(start = 6.dp, top = 6.dp))
        }

        if (items.isEmpty()) {
            Spacer(Modifier.height(12.dp))
            GlassCard { Text("还没有待办。期末周将至，先记一笔吧～", fontSize = 14.sp, color = ds.sub) }
        } else {
            Spacer(Modifier.height(14.dp))
            // 按课程分组：有最早截止的组在前，无截止组次之，「其他」（手动待办）垫底
            val groups = pending
                .groupBy { it.tag.ifEmpty { "其他" } }
                .entries
                .sortedWith(
                    compareBy(
                        { it.key == "其他" },
                        { it.value.filter { t -> t.due > 0 }.minOfOrNull { t -> t.due } ?: Long.MAX_VALUE },
                        { it.key },
                    ),
                )
            Text(
                "未完成 ${pending.size}",
                fontSize = 12.sp, color = ds.sub,
                modifier = Modifier.padding(start = 6.dp, bottom = 6.dp),
            )
            if (pending.isEmpty()) {
                GlassCard { Text("都完成啦，歇口气～", fontSize = 14.sp, color = ds.sub) }
            }
            groups.forEach { group ->
                Spacer(Modifier.height(4.dp))
                Text(
                    group.key,
                    fontSize = 13.sp, fontWeight = FontWeight.Bold, color = ds.accent,
                    modifier = Modifier.padding(start = 6.dp, bottom = 4.dp),
                )
                GlassCard {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        // 组内：有截止按时间升序，无截止垫底
                        group.value
                            .sortedWith(compareBy({ it.due <= 0L }, { it.due }))
                            .forEach { item ->
                                TodoRow(
                                    item = item,
                                    onToggle = {
                                        items = repo.store.updateTodos { current ->
                                            current.map { if (it.id == item.id) it.copy(done = !it.done) else it }
                                        }
                                    },
                                    onDelete = {
                                        items = repo.store.updateTodos { current -> current.filter { it.id != item.id } }
                                    },
                                )
                            }
                    }
                }
            }

            if (doneItems.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Text(
                    "已完成 ${doneItems.size}",
                    fontSize = 12.sp, color = ds.sub,
                    modifier = Modifier.padding(start = 6.dp, bottom = 6.dp),
                )
                GlassCard {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        doneItems.forEach { item ->
                            TodoRow(
                                item = item,
                                showTag = true,
                                onToggle = {
                                    items = repo.store.updateTodos { current ->
                                        current.map { if (it.id == item.id) it.copy(done = !it.done) else it }
                                    }
                                },
                                onDelete = {
                                    items = repo.store.updateTodos { current -> current.filter { it.id != item.id } }
                                },
                            )
                        }
                    }
                }
                Text(
                    "清除已完成",
                    fontSize = 13.sp,
                    color = ds.accent,
                    modifier = Modifier
                        .clickable {
                            items = repo.store.updateTodos { current -> current.filter { !it.done } }
                        }
                        .padding(vertical = 10.dp),
                )
            }
        }
        Spacer(Modifier.height(110.dp))
    }
}

/** 单条待办：勾选圈 + 标题；按课程分组时组名即课程名（showTag=false），已完成平铺时显示课程胶囊。 */
@Composable
private fun TodoRow(
    item: Store.Todo,
    showTag: Boolean = false,
    onToggle: () -> Unit,
    onDelete: () -> Unit,
) {
    val ds = LocalDs.current
    val due = dueLabel(item.due)
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier
                .size(22.dp)
                .background(if (item.done) ds.accent else ds.bg, CircleShape)
                // 浅色主题下未勾选的圆圈是白底白圈：加描边才看得见
                .then(
                    if (item.done) Modifier
                    else Modifier.border(1.dp, ds.sub.copy(alpha = 0.45f), CircleShape)
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (item.done) Text("✓", fontSize = 12.sp, color = androidx.compose.ui.graphics.Color.White)
        }
        Column(Modifier.weight(1f)) {
            Text(
                item.text,
                fontSize = 15.sp,
                color = if (item.done) ds.sub else ds.text,
            )
            if ((showTag && item.tag.isNotEmpty()) || due != null) {
                Spacer(Modifier.height(3.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (showTag && item.tag.isNotEmpty()) {
                        Text(
                            item.tag,
                            fontSize = 10.sp,
                            color = ds.accent,
                            modifier = Modifier
                                .background(ds.accent.copy(alpha = 0.12f), RoundedCornerShape(4.dp))
                                .padding(horizontal = 5.dp, vertical = 1.dp),
                        )
                    }
                    if (due != null && !item.done) {
                        Text(
                            due.first,
                            fontSize = 11.sp,
                            color = if (due.second) ds.danger else ds.sub,
                        )
                    }
                }
            }
        }
        Text(
            "删",
            fontSize = 12.sp,
            color = ds.danger,
            modifier = Modifier.clickable(onClick = onDelete),
        )
    }
}

/** 截止时间 → 「M月d日 · 剩N天/今天截止/已过期」；urgent=true 用警示色。 */
private fun dueLabel(due: Long): Pair<String, Boolean>? {
    if (due <= 0) return null
    val date = SimpleDateFormat("M月d日", Locale.CHINA).format(Date(due))
    val diff = due - System.currentTimeMillis()
    return when {
        diff < 0 -> "${date} · 已过期" to true
        diff < 24 * 3600_000L -> "${date} · 今天截止" to true
        else -> "${date} · 剩${diff / (24 * 3600_000L) + 1}天" to false
    }
}
