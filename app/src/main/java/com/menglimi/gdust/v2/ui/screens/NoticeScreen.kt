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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.menglimi.gdust.v2.data.Notice
import com.menglimi.gdust.v2.data.NoticeCategory
import com.menglimi.gdust.v2.data.Repository
import com.menglimi.gdust.v2.data.matches
import com.menglimi.gdust.v2.ui.GlassCard
import com.menglimi.gdust.v2.ui.LocalDs
import com.menglimi.gdust.v2.ui.pageBackground
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch

/**
 * 学校通知（公告）列表：拉取门户公告，未读红点，点击用浏览器打开原文；
 * 顶部一键「AI 总结」把最近公告提炼成要点。
 */
@Composable
fun NoticeScreen(
    repo: Repository,
    notices: List<Notice>,
    loading: Boolean,
    error: String?,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
) {
    val ds = LocalDs.current
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val store = repo.store

    var summarizing by remember { mutableStateOf(false) }
    var summary by remember { mutableStateOf<String?>(null) }
    var summaryError by remember { mutableStateOf("") }
    var category by remember { mutableStateOf(NoticeCategory.ALL) }
    val visibleNotices = notices.filter { it.matches(category) }

    val timeFmt = remember { SimpleDateFormat("MM-dd HH:mm", Locale.CHINA) }

    Column(
        Modifier
            .fillMaxSize()
            .background(pageBackground())
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
    ) {
        Spacer(Modifier.height(24.dp))
        PageTitle(title = "学校通知", subtitle = "门户公告 · 考试与教务通知筛选", onBack = onBack)
        SegmentedChoice(
            options = listOf(
                NoticeCategory.ALL to "全部",
                NoticeCategory.ACADEMIC to "教务",
                NoticeCategory.EXAM to "考试",
            ),
            selected = category,
            onSelect = { category = it },
        )
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                when {
                    loading -> "正在更新门户公告…"
                    error != null -> error
                    notices.isNotEmpty() -> "共 ${visibleNotices.size} 条相关公告"
                    else -> "门户公告尚未加载"
                },
                fontSize = 12.sp,
                color = if (error != null) ds.danger else ds.sub,
                modifier = Modifier.weight(1f),
                maxLines = 2,
            )
            Button(text = if (loading) "更新中…" else "刷新", enabled = !loading) {
                onRefresh()
            }
        }

        // ── AI 总结 ──
        GlassCard {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("AI 总结", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = ds.text)
                    Spacer(Modifier.weight(1f))
                    Text(
                        if (store.hasLlm) "用你配置的模型" else "需先在设置配置 LLM",
                        fontSize = 11.sp, color = ds.sub,
                    )
                }
                summary?.let {
                    Text(it, fontSize = 13.sp, lineHeight = 18.sp, color = ds.text)
                } ?: Text(
                    if (store.hasLlm) "把最近的公告交给你的模型提炼要点"
                    else "先到「设置 → 通知 AI 总结」填好中转站配置",
                    fontSize = 12.sp, color = ds.sub,
                )
                summaryError.takeIf { it.isNotEmpty() }?.let {
                    Text(it, fontSize = 12.sp, color = ds.danger)
                }
                Button(
                    text = if (summarizing) "总结中…" else if (summary == null) "AI 总结最近公告" else "重新总结",
                    enabled = store.hasLlm && !summarizing && notices.isNotEmpty(),
                ) {
                    summarizing = true
                    summaryError = ""
                    scope.launch {
                        runCatching { repo.summarize() }
                            .onSuccess { summary = it }
                            .onFailure { summaryError = it.message ?: "总结失败" }
                        summarizing = false
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))

        // ── 公告列表 ──
        if (loading && notices.isEmpty()) {
            GlassCard { Text("正在拉取公告…", fontSize = 13.sp, color = ds.sub) }
        } else if (notices.isEmpty()) {
            GlassCard {
                Text(
                    error ?: "暂无门户公告。登录学校门户后刷新即可获取。",
                    fontSize = 13.sp,
                    color = if (error != null) ds.danger else ds.sub,
                )
            }
        } else if (visibleNotices.isEmpty()) {
            GlassCard {
                Text("当前没有匹配的${if (category == NoticeCategory.EXAM) "考试" else "教务"}公告。", fontSize = 13.sp, color = ds.sub)
            }
        } else {
            // 打开页面即把最新公告时间记为已读水位
            androidx.compose.runtime.LaunchedEffect(notices) {
                val maxTime = notices.maxOf { it.time }
                if (maxTime > store.lastNoticeReadAt) store.lastNoticeReadAt = maxTime
            }
            visibleNotices.forEach { n ->
                val unread = n.time > store.lastNoticeReadAt
                GlassCard(
                    modifier = Modifier.padding(bottom = 10.dp),
                    onClick = {
                        runCatching {
                            context.startActivity(
                                android.content.Intent(
                                    android.content.Intent.ACTION_VIEW,
                                    android.net.Uri.parse(n.url),
                                )
                            )
                        }
                        if (n.time > store.lastNoticeReadAt) store.lastNoticeReadAt = n.time
                    },
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (unread) {
                                Box(
                                    Modifier
                                        .size(8.dp)
                                        .background(ds.accent, CircleShape)
                                )
                                Spacer(Modifier.width(6.dp))
                            }
                            Text(
                                n.title,
                                fontSize = 14.sp,
                                fontWeight = if (unread) FontWeight.Bold else FontWeight.Medium,
                                color = ds.text,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            n.issuer.takeIf { !it.isNullOrBlank() }?.let {
                                Text(it, fontSize = 11.sp, color = ds.sub, maxLines = 1)
                            }
                            Text(timeFmt.format(Date(n.time)), fontSize = 11.sp, color = ds.sub)
                            n.type.takeIf { !it.isNullOrBlank() }?.let {
                                Text(it, fontSize = 11.sp, color = ds.accent, maxLines = 1)
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(110.dp))
    }
}
