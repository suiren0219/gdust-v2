package com.menglimi.kedian.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.menglimi.kedian.learning.Homework
import com.menglimi.kedian.learning.LearningUiState

@Composable
fun LearningScreen(
    state: LearningUiState,
    onRefresh: () -> Unit,
    onLogin: () -> Unit,
    onOpenWork: (String) -> Unit,
    onClearError: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var filter by rememberSaveable { mutableIntStateOf(0) }
    val now = System.currentTimeMillis()
    val pending = state.works.filter { it.status == "未交" }
    val approaching = pending.count { it.dueAt in now..(now + 24 * 3600_000L) }
    val selected = when (filter) {
        1 -> pending.filter { it.dueAt in now..(now + 24 * 3600_000L) }
        2 -> state.works.filter { it.status != "未交" }
        else -> pending
    }.sortedWith(compareBy<Homework> { if (it.dueAt > 0) it.dueAt else Long.MAX_VALUE }.thenBy { it.courseName })
    LazyColumn(modifier, contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 116.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { PageHeader("学习通", "让每一项作业都有着落", if (state.connected || state.updatedAt > 0) onRefresh else null, state.loading) }
        item {
            HeroCard(
                tags = listOf("作业", if (state.connected) "已连接" else if (state.updatedAt > 0) "离线记录" else "未连接"),
                title = if (state.connected) "${pending.size} 项待完成" else "连接学习通",
                subtitle = if (state.connected) {
                    if (state.failedCourses > 0) "${state.failedCourses} 门课程未更新，部分条目保留上次记录。"
                    else "最近更新 ${displayTime(state.updatedAt)}"
                } else if (state.updatedAt > 0) "保留了上次作业记录，重新连接后更新。"
                else "使用学习通账号登录，查看课程作业与截止时间。",
                glyph = Glyph.BOOK,
            ) {
                if (!state.connected) Button(onClick = onLogin, enabled = !state.loading) {
                    KIcon(Glyph.LINK, Modifier.size(18.dp), MaterialTheme.colorScheme.onPrimary)
                    Spacer(Modifier.width(8.dp)); Text("登录学习通")
                }
            }
        }
        state.error?.let { error -> item { MessageCard(error, onClearError) } }
        if (state.connected || state.works.isNotEmpty()) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatCard(approaching.toString(), "24 小时内截止")
                    StatCard(state.works.count { it.status != "未交" }.toString(), "已提交")
                    StatCard(state.works.map { it.courseName }.distinct().size.toString(), "含作业课程")
                }
            }
            item {
                Column {
                    SectionTitle("我的作业", "${selected.size} 项")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("待完成", "即将截止", "已提交").forEachIndexed { index, title ->
                            FilterChip(selected = filter == index, onClick = { filter = index }, label = { Text(title) })
                        }
                    }
                }
            }
            if (selected.isEmpty()) item {
                EmptyCard(if (state.failedCourses > 0) "暂未读到此类作业" else "当前没有${listOf("待完成", "即将截止", "已提交")[filter]}作业",
                    if (state.failedCourses > 0) "部分课程读取失败，请刷新或进入学习通网页确认。"
                    else "这里展示本次成功读取的课程。课程页有更新时，点右上角刷新。", Glyph.CHECK)
            }
            items(selected, key = { it.key }) { work ->
                val overdue = work.status == "未交" && work.dueAt in 1 until now
                val soon = work.status == "未交" && work.dueAt in now..(now + 24 * 3600_000L)
                KCard(Modifier.fillMaxWidth(), onClick = { onOpenWork(work.detailUrl) }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(work.courseName, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f))
                        StatusTag(if (overdue) "已到截止时间" else if (soon) "即将截止" else work.status,
                            container = if (overdue || soon) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer,
                            foreground = if (overdue || soon) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSecondaryContainer)
                    }
                    Text(work.title, fontSize = 18.sp, lineHeight = 26.sp, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(top = 12.dp, bottom = 14.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        KIcon(Glyph.CLOCK, Modifier.size(15.dp), MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(6.dp))
                        Text(if (work.dueAt > 0) "${displayTime(work.dueAt)} 截止" else "截止时间未提供",
                            color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, modifier = Modifier.weight(1f))
                        KIcon(Glyph.ARROW, Modifier.size(16.dp), MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        } else {
            item { SectionTitle("轻装上课") }
            item { EmptyCard("作业与截止时间，一处查看", "登录后读取课程与作业。点开条目可进入学习通原网页，提交操作在原网页完成。", Glyph.CLOCK) }
            item {
                KCard(Modifier.fillMaxWidth()) {
                    SettingRow("数据留在本机", "不经过自建服务器，不保存账号密码", Glyph.LINK) { }
                    SettingRow("独立使用", "学习通登录与宿舍电费绑定分别管理", Glyph.CHECK) { }
                }
            }
        }
        item {
            Text("只展示已读取的课程作业；最终截止时间和提交状态以学习通原网页为准。",
                fontSize = 12.sp, lineHeight = 19.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, bottom = 8.dp))
        }
    }
}
