package com.menglimi.gdust.v2.ui.screens

import androidx.compose.foundation.background
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
import com.menglimi.gdust.v2.ui.GlassCard
import com.menglimi.gdust.v2.ui.LocalDs

@Composable
fun TodoScreen(repo: Repository) {
    val ds = LocalDs.current
    var items by remember { mutableStateOf(repo.store.loadTodos()) }
    var draft by remember { mutableStateOf("") }
    var hint by remember { mutableStateOf("") }
    var seq by remember { mutableStateOf(System.currentTimeMillis()) }

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
                                val item = com.menglimi.gdust.v2.data.Store.Todo(seq++, draft.trim(), false)
                                items = listOf(item) + items
                                repo.store.saveTodos(items)
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
            Spacer(Modifier.height(12.dp))
            GlassCard {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    items.forEach { item ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable {
                                    items = items.map { if (it.id == item.id) it.copy(done = !it.done) else it }
                                    repo.store.saveTodos(items)
                                }
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Box(
                                Modifier
                                    .size(22.dp)
                                    .background(if (item.done) ds.accent else ds.bg, CircleShape),
                                contentAlignment = Alignment.Center,
                            ) {
                                if (item.done) Text("✓", fontSize = 12.sp, color = androidx.compose.ui.graphics.Color.White)
                            }
                            Text(
                                item.text,
                                fontSize = 15.sp,
                                color = if (item.done) ds.sub else ds.text,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                "删",
                                fontSize = 12.sp,
                                color = ds.danger,
                                modifier = Modifier.clickable {
                                    items = items.filter { it.id != item.id }
                                    repo.store.saveTodos(items)
                                },
                            )
                        }
                    }
                }
            }
            if (items.any { it.done }) {
                Text(
                    "清除已完成",
                    fontSize = 13.sp,
                    color = ds.accent,
                    modifier = Modifier
                        .clickable {
                            items = items.filter { !it.done }
                            repo.store.saveTodos(items)
                        }
                        .padding(vertical = 10.dp),
                )
            }
        }
        Spacer(Modifier.height(110.dp))
    }
}
