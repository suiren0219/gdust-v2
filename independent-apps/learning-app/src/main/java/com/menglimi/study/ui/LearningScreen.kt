package com.menglimi.study.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
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
import com.menglimi.ksu.ui.EmptyCard
import com.menglimi.ksu.ui.Glyph
import com.menglimi.ksu.ui.HeroCard
import com.menglimi.ksu.ui.KCard
import com.menglimi.ksu.ui.KIcon
import com.menglimi.ksu.ui.MessageCard
import com.menglimi.ksu.ui.PageHeader
import com.menglimi.ksu.ui.SectionTitle
import com.menglimi.ksu.ui.StatCard
import com.menglimi.ksu.ui.StatusTag
import com.menglimi.ksu.ui.displayTime
import com.menglimi.study.learning.LearningUiState
import com.menglimi.study.learning.WorkFilter
import com.menglimi.study.learning.cacheKey
import com.menglimi.study.learning.selectHomework

@Composable
fun LearningScreen(
    state: LearningUiState, onRefresh: () -> Unit, onRetryCourse: (String) -> Unit,
    onLogin: () -> Unit, onOpenWork: (String) -> Unit, onClearError: () -> Unit,
    modifier: Modifier = Modifier, preview: Boolean = false,
) {
    var filter by rememberSaveable { mutableIntStateOf(0) }
    val now = System.currentTimeMillis()
    val pending = selectHomework(state.works, WorkFilter.PENDING, now)
    val approaching = selectHomework(state.works, WorkFilter.DUE_SOON, now)
    val submitted = selectHomework(state.works, WorkFilter.SUBMITTED, now)
    val selected = selectHomework(state.works, WorkFilter.entries[filter], now)
    val unknown = state.failedCourses > 0 || state.error != null || state.fromCache || !state.connected
    LazyColumn(modifier, contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 116.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { PageHeader("作业", if (preview) "合成预览 · 不访问网络或正式数据" else "让每一项作业都有着落", onRefresh, state.loading) }
        item {
            HeroCard(tags = listOf("学习助手", when {
                state.loading -> "正在读取"
                state.fromCache -> "已缓存"
                state.connected -> "已连接"
                else -> "未登录"
            }), title = when {
                state.loading && !state.hasSnapshot -> "正在读取作业"
                state.hasSnapshot -> "${pending.size} 项待交"
                state.error != null -> "暂未读到结果"
                else -> "连接学习通"
            }, subtitle = when {
                state.fromCache -> "当前含上次读取的记录，刷新后确认。最近完整记录 ${displayTime(state.updatedAt)}"
                state.failedCourses > 0 -> "${state.failedCourses} 门课程尚未读完，当前计数不代表全部作业。"
                state.hasSnapshot -> "已读取 ${state.courseCount} 门课程 · ${displayTime(state.updatedAt)}"
                else -> "在学习通官网登录，集中查看课程作业与截止时间。"
            }, glyph = Glyph.BOOK, warning = state.error != null && !state.hasSnapshot) {
                if (!state.connected) Button(onClick = onLogin, enabled = !state.loading) {
                    KIcon(Glyph.LINK, Modifier.size(18.dp), MaterialTheme.colorScheme.onPrimary)
                    Spacer(Modifier.width(8.dp)); Text("登录学习通")
                }
            }
        }
        state.error?.let { error -> item { MessageCard(error, onClearError) } }
        items(state.failures, key = { "failure:${it.course.cacheKey()}" }) { failure ->
            KCard(Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.errorContainer) {
                Text(failure.course.name, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onErrorContainer)
                Text(failure.message, fontSize = 13.sp, lineHeight = 20.sp,
                    color = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.padding(top = 6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(if (failure.hasCachedResult) "保留上次记录" else "是否有作业仍未知", fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.weight(1f))
                    TextButton(onClick = { onRetryCourse(failure.course.cacheKey()) }, enabled = !state.loading) { Text("重试此课") }
                }
            }
        }
        if (state.hasSnapshot) {
            item { Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatCard(approaching.size.toString(), "24 小时内截止")
                StatCard(submitted.size.toString(), "已交")
                StatCard(state.courseCount.toString(), "已发现课程")
            } }
            item { Column {
                SectionTitle("我的作业", "${selected.size} 项")
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    WorkFilter.entries.forEachIndexed { index, item ->
                        FilterChip(selected = filter == index, onClick = { filter = index }, label = { Text(item.title) })
                    }
                }
            } }
            if (selected.isEmpty()) item {
                EmptyCard(if (unknown) "暂未读到此类作业" else "已读课程中没有${WorkFilter.entries[filter].title}作业",
                    if (unknown) "当前结果未完整确认，请重试失败课程或刷新。不能据此判断没有作业。"
                    else "这里只展示本次成功读取的课程；最终状态以学习通网页为准。", Glyph.CHECK)
            }
            items(selected, key = { it.key }) { work ->
                val overdue = work.status == "未交" && work.dueAt in 1 until now
                val soon = work.status == "未交" && work.dueAt in now..(now + 24 * 3600_000L)
                KCard(Modifier.fillMaxWidth(), onClick = { onOpenWork(work.detailUrl) }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(work.courseName, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f).padding(end = 8.dp))
                        StatusTag(if (overdue) "已到截止时间" else if (soon) "临近截止" else if (work.status == "未交") "待交" else "已交",
                            if (overdue || soon) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer,
                            if (overdue || soon) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSecondaryContainer)
                    }
                    Text(work.title, fontSize = 18.sp, lineHeight = 26.sp, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(top = 12.dp, bottom = 14.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        KIcon(Glyph.CLOCK, Modifier.size(15.dp), MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(6.dp))
                        Text(if (work.dueAt > 0) "${displayTime(work.dueAt)} 截止" else "截止时间未提供",
                            fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                        KIcon(Glyph.ARROW, Modifier.size(16.dp), MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        } else item {
            EmptyCard(if (state.loading) "正在读取课程与作业" else "作业与截止时间，一处查看",
                if (state.loading) "逐门课程读取中，完成后展示结果。" else "使用学习通官方网页登录。账号密码不保存到应用，课程和作业快照仅在本机加密保存。", Glyph.BOOK)
        }
        item { Text("仅查看已读取的作业，点击卡片打开学习通原网页。本应用没有自动做题或自动提交能力。",
            fontSize = 12.sp, lineHeight = 19.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)) }
    }
}
