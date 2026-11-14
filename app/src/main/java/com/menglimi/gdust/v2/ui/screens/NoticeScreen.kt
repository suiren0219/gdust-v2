package com.menglimi.gdust.v2.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
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
import com.menglimi.gdust.v2.data.NoticeSource
import com.menglimi.gdust.v2.data.Repository
import com.menglimi.gdust.v2.data.matches
import com.menglimi.gdust.v2.data.noticeLabel
import com.menglimi.gdust.v2.data.noticeWebUrl
import com.menglimi.gdust.v2.data.readId
import com.menglimi.gdust.v2.ui.GlassCard
import com.menglimi.gdust.v2.ui.LocalDs
import com.menglimi.gdust.v2.ui.pageBackground
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch

/** 学校通知与新闻：切换来源、筛选内容，并按当前列表生成标题摘要。 */
@Composable
fun NoticeScreen(
    repo: Repository,
    notices: List<Notice>,
    loading: Boolean,
    error: String?,
    readIds: Set<String>,
    updatedAt: Long,
    onMarkRead: (List<Notice>) -> Unit,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    source: NoticeSource,
    onSourceChange: (NoticeSource) -> Unit,
) {
    val sessionVersion by repo.sessionVersion.collectAsState()
    // 换号或切换来源后销毁旧界面状态与协程，摘要、错误不会串到下一份列表。
    key(repo, sessionVersion, source) {
        val ds = LocalDs.current
        val scope = rememberCoroutineScope()
        val context = LocalContext.current
        val store = repo.store
        var summarizing by remember(notices) { mutableStateOf(false) }
        var summary by remember(notices) { mutableStateOf<String?>(null) }
        var summaryError by remember(notices) { mutableStateOf("") }
        var summaryJob by remember(notices) { mutableStateOf<Job?>(null) }
        var openErrors by remember(notices) { mutableStateOf<Map<Notice, String>>(emptyMap()) }
        var category by remember { mutableStateOf(NoticeCategory.ALL) }
        val visibleNotices = notices.filter { it.matches(category) }
        val unreadCount = notices.count { it.readId !in readIds }
        val label = source.noticeLabel
        val timeFmt = remember {
            SimpleDateFormat("MM-dd HH:mm", Locale.CHINA).apply {
                timeZone = TimeZone.getTimeZone("Asia/Shanghai")
            }
        }
        val updatedLabel = updatedAt.takeIf { it > 0L }?.let { timeFmt.format(Date(it)) }

        DisposableEffect(notices) {
            onDispose { summaryJob?.cancel() }
        }

        Column(
            Modifier
                .fillMaxSize()
                .background(pageBackground())
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            Spacer(Modifier.height(24.dp))
            PageTitle(title = "学校通知", subtitle = "学校公告与新闻 · 按来源查看", onBack = onBack)
            SegmentedChoice(
                options = listOf(
                    NoticeSource.PORTAL to "门户",
                    NoticeSource.ACADEMIC to "教务处",
                    NoticeSource.CAMPUS to "官网",
                ),
                selected = source,
                onSelect = { if (it != source) onSourceChange(it) },
            )
            Text(
                when (source) {
                    NoticeSource.PORTAL -> "门户通知 · 部分原文会打开钉钉通知列表"
                    NoticeSource.ACADEMIC -> "教务处公告 · 此来源待在线验证"
                    NoticeSource.CAMPUS -> "学校官网新闻"
                },
                fontSize = 13.sp,
                lineHeight = 20.sp,
                color = ds.sub,
                modifier = Modifier.padding(vertical = 10.dp),
            )
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
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        when {
                            loading -> "正在更新$label…"
                            error != null -> error
                            notices.isNotEmpty() -> "共 ${visibleNotices.size} 条相关公告" +
                                if (unreadCount > 0) " · $unreadCount 条未读" else ""
                            updatedAt > 0L -> "暂无$label"
                            else -> "$label 尚未加载"
                        },
                        fontSize = 13.sp,
                        lineHeight = 20.sp,
                        color = if (error != null && !loading) ds.danger else ds.sub,
                    )
                    if (updatedLabel != null) {
                        Text(
                            if (loading || error != null) "保留上次结果 · $updatedLabel 更新"
                            else "上次更新 $updatedLabel",
                            fontSize = 13.sp,
                            lineHeight = 20.sp,
                            color = ds.sub,
                        )
                    }
                }
                top.yukonga.miuix.kmp.basic.Button(
                    onClick = onRefresh,
                    enabled = !loading,
                    minHeight = 48.dp,
                    insideMargin = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                ) {
                    top.yukonga.miuix.kmp.basic.Text(
                        if (loading) "更新中…" else "刷新",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
            Spacer(Modifier.height(12.dp))

            GlassCard {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("AI 总结", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = ds.text)
                    Text(
                        if (store.hasLlm) "将当前来源的 ${notices.size} 条公告标题交给你配置的模型"
                        else "先到「设置 → 通知 AI 总结」填好模型配置",
                        fontSize = 13.sp,
                        lineHeight = 20.sp,
                        color = ds.sub,
                    )
                    summary?.let { Text(it, fontSize = 15.sp, lineHeight = 23.sp, color = ds.text) }
                    if (summaryError.isNotEmpty()) {
                        Text(summaryError, fontSize = 13.sp, lineHeight = 20.sp, color = ds.danger)
                    }
                    Button(
                        text = if (summarizing) "总结中…" else if (summary == null) "AI 总结当前公告" else "重新总结",
                        enabled = store.hasLlm && !summarizing && !loading && notices.isNotEmpty(),
                    ) {
                        val session = repo.portalSession()
                        if (session == null) {
                            summaryError = "请先登录学校门户后再总结。"
                        } else if (repo.sessionVersion.value == sessionVersion) {
                            val snapshot = notices.toList()
                            summaryJob?.cancel()
                            summarizing = true
                            summaryError = ""
                            summaryJob = scope.launch {
                                try {
                                    val result = repo.summarize(snapshot, session)
                                    currentCoroutineContext().ensureActive()
                                    if (repo.portalSession() == session && repo.sessionVersion.value == sessionVersion) {
                                        summary = result
                                    }
                                } catch (e: CancellationException) {
                                    throw e
                                } catch (_: Exception) {
                                    if (repo.portalSession() == session && repo.sessionVersion.value == sessionVersion) {
                                        summaryError = "总结失败，请检查模型配置与网络后重试。"
                                    }
                                } finally {
                                    summarizing = false
                                }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))

            when {
                loading && notices.isEmpty() -> {
                    GlassCard { Text("正在加载$label…", fontSize = 13.sp, color = ds.sub) }
                }
                notices.isEmpty() -> {
                    GlassCard {
                        Text(
                            when {
                                error != null -> "$label 加载失败，请根据上方提示重试。"
                                updatedAt > 0L -> "暂无$label，可稍后刷新。"
                                else -> "$label 尚未加载，登录学校门户后刷新即可获取。"
                            },
                            fontSize = 13.sp,
                            lineHeight = 20.sp,
                            color = if (error != null) ds.danger else ds.sub,
                        )
                    }
                }
                visibleNotices.isEmpty() -> {
                    GlassCard {
                        Text(
                            "$label 中暂无匹配的${if (category == NoticeCategory.EXAM) "考试" else "教务"}公告。可切换为「全部」。",
                            fontSize = 13.sp,
                            lineHeight = 20.sp,
                            color = ds.sub,
                        )
                    }
                }
                else -> {
                    Text(
                        "打开浏览器后标为已读，仅作本机标记，不代表原文已加载或阅读。",
                        fontSize = 13.sp,
                        lineHeight = 20.sp,
                        color = ds.sub,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                    if (unreadCount > 0) {
                        Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                            top.yukonga.miuix.kmp.basic.Button(
                                onClick = { onMarkRead(notices) },
                                minHeight = 48.dp,
                                insideMargin = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                            ) {
                                top.yukonga.miuix.kmp.basic.Text(
                                    "全部标为已读",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                )
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                    visibleNotices.forEach { notice ->
                        val unread = notice.readId !in readIds
                        GlassCard(
                            modifier = Modifier.padding(bottom = 12.dp),
                            onClick = {
                                val url = noticeWebUrl(notice.url)
                                val failure = if (url == null) {
                                    "原文链接无效，无法打开。"
                                } else {
                                    try {
                                        context.startActivity(
                                            Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                                .addCategory(Intent.CATEGORY_BROWSABLE)
                                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        )
                                        null
                                    } catch (_: Exception) {
                                        "无法打开原文，请检查是否安装浏览器后重试。"
                                    }
                                }
                                if (failure == null) {
                                    openErrors = openErrors - notice
                                    // 只确认已成功发出打开网页请求，不能判断浏览器加载/阅读结果。
                                    if (unread) onMarkRead(listOf(notice))
                                } else {
                                    openErrors = openErrors + (notice to failure)
                                }
                            },
                        ) {
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (unread) {
                                        Box(Modifier.size(8.dp).background(ds.accent, CircleShape))
                                        Spacer(Modifier.width(6.dp))
                                    }
                                    Text(
                                        notice.title,
                                        fontSize = 17.sp,
                                        lineHeight = 24.sp,
                                        fontWeight = if (unread) FontWeight.SemiBold else FontWeight.Medium,
                                        color = ds.text,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                                Spacer(Modifier.height(6.dp))
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    val dateLabel = if (notice.time > 0L) timeFmt.format(Date(notice.time)) else "发布时间未知"
                                    Text(
                                        listOfNotNull(notice.issuer?.takeIf { it.isNotBlank() }, dateLabel).joinToString(" · "),
                                        fontSize = 13.sp,
                                        lineHeight = 20.sp,
                                        color = ds.sub,
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                    if (notice.type.isNotBlank()) {
                                        Text(notice.type, fontSize = 13.sp, lineHeight = 20.sp, color = ds.accent)
                                    }
                                    openErrors[notice]?.let {
                                        Text(it, fontSize = 13.sp, lineHeight = 20.sp, color = ds.danger)
                                    }
                                }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(110.dp))
        }
    }
}
