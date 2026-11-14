package com.menglimi.gdust.v2.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.menglimi.gdust.v2.data.AuthFailed
import com.menglimi.gdust.v2.data.GradeLoadException
import com.menglimi.gdust.v2.data.GradeReport
import com.menglimi.gdust.v2.data.Repository
import com.menglimi.gdust.v2.data.TeachingGrade
import com.menglimi.gdust.v2.ui.AppCard
import com.menglimi.gdust.v2.ui.AppUiTokens
import com.menglimi.gdust.v2.ui.LocalDs
import java.io.IOException
import java.time.LocalDate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

@Composable
fun GradesScreen(repo: Repository, onBack: () -> Unit, onWebLogin: () -> Unit) {
    val sessionVersion by repo.sessionVersion.collectAsState()
    // 会话变化销毁整份页面状态，含详情、筛选和旧请求，个人成绩不进入 rememberSaveable。
    key(repo, sessionVersion) {
        GradeContent(repo, sessionVersion, onBack, onWebLogin)
    }
}

private data class GradeSelection(val year: String, val semester: String) {
    val valid: Boolean get() = year.matches(Regex("[0-9]{4}")) && semester in setOf("3", "12")
}

@Composable
private fun GradeContent(repo: Repository, sessionVersion: Long, onBack: () -> Unit, onWebLogin: () -> Unit) {
    val ds = LocalDs.current
    val calendar = remember { repo.store.loadCalendar() }
    var year by remember {
        mutableStateOf(calendar?.year?.takeIf { it.matches(Regex("[0-9]{4}")) } ?: LocalDate.now().year.toString())
    }
    var semester by remember { mutableStateOf(calendar?.semester?.takeIf { it == "3" || it == "12" } ?: "3") }
    val selection = GradeSelection(year, semester)
    // 仅同一学期刷新保留旧结果；切换筛选同步更换状态，不能让旧学期结果冒充新学期。
    var report by remember(selection) { mutableStateOf<GradeReport?>(null) }
    var selected by remember(selection) { mutableStateOf<TeachingGrade?>(null) }
    var error by remember(selection) { mutableStateOf<String?>(null) }
    var needsLogin by remember(selection) { mutableStateOf(false) }
    var loading by remember(selection) { mutableStateOf(selection.valid) }
    var refresh by remember(selection) { mutableIntStateOf(0) }

    LaunchedEffect(selection, refresh) {
        if (!selection.valid || !repo.isCurrentSession(sessionVersion)) {
            loading = false
            return@LaunchedEffect
        }
        loading = true
        error = null
        needsLogin = false
        try {
            val result = repo.grades(selection.year, selection.semester)
            currentCoroutineContext().ensureActive()
            if (repo.isCurrentSession(sessionVersion)) {
                report = result
                selected = null
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            if (repo.isCurrentSession(sessionVersion)) {
                needsLogin = failure is AuthFailed
                // 不把异常 message、URL、响应片段或个人字段放进状态提示。
                error = when (failure) {
                    is AuthFailed -> "教务登录已失效，请先完成教务网页登录，再返回刷新。"
                    is GradeLoadException -> failure.reason.message
                    is IOException -> "网络连接失败，请检查网络后重试。"
                    else -> "成绩暂时无法读取，请稍后重试。"
                }
            }
        } finally {
            if (repo.isCurrentSession(sessionVersion)) loading = false
        }
    }

    val currentSession = repo.isCurrentSession(sessionVersion)
    val visibleReport = report.takeIf { currentSession }
    val detail = selected.takeIf { currentSession }
    if (detail != null) {
        GradeDetails(detail, onBack = { selected = null })
        return
    }

    SettingSubPage("学期成绩", "按学年与学期查询教务成绩", onBack) {
        SettingGroup {
            SettingEditor {
                Text("起始学年", fontSize = 13.sp, color = ds.sub)
                InputField(
                    year,
                    { value -> year = value.filter { it in '0'..'9' }.take(4) },
                    "例如 2026，表示 2026–2027 学年",
                    KeyboardType.Number,
                    modifier = Modifier.semantics { contentDescription = "起始学年" },
                )
                if (selection.valid) {
                    Text("${year}–${year.toInt() + 1} 学年", fontSize = 13.sp, color = ds.sub)
                }
                SegmentedChoice(
                    listOf("3" to "第一学期", "12" to "第二学期"),
                    semester,
                    { semester = it },
                )
                Button(
                    if (loading) "正在读取成绩…" else if (visibleReport == null) "查询成绩" else "刷新成绩",
                    enabled = selection.valid && !loading && currentSession,
                ) { refresh++ }
            }
        }

        val message = when {
            !currentSession -> "登录状态已变化，请重新查询。"
            !selection.valid -> "请输入四位起始学年。"
            loading && visibleReport != null -> "正在刷新，暂时显示上次完整查询结果。"
            loading -> "正在读取该学期的全部成绩…"
            error != null -> error.orEmpty() + if (visibleReport != null) "\n本次刷新未完成，已保留该学期上次的完整结果。" else ""
            visibleReport != null -> "已读取该学期全部 ${visibleReport.total} 条成绩。"
            else -> "选择学年和学期后查询成绩。"
        }
        Text(message, fontSize = 13.sp, lineHeight = 19.sp,
            color = if (error != null && currentSession) ds.danger else ds.sub,
            modifier = Modifier.padding(vertical = 8.dp))

        if (currentSession && (needsLogin || (!loading && visibleReport == null))) {
            Spacer(Modifier.height(8.dp))
            SettingGroup {
                SettingOption(
                    title = "教务网页登录",
                    subtitle = "登录完成后返回本页刷新",
                    onClick = onWebLogin,
                )
            }
        }

        if (visibleReport != null) {
            SectionHeader(if (error != null || loading) "上次查询结果" else "成绩明细")
            if (visibleReport.entries.isEmpty()) {
                AppCard(Modifier.fillMaxWidth(), insideMargin = PaddingValues(AppUiTokens.CardPadding)) {
                    Text("该学期暂无成绩。", color = ds.sub, fontSize = 14.sp)
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(AppUiTokens.CardGap)) {
                    visibleReport.entries.chunked(2).forEach { row ->
                        Row(
                            Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                            horizontalArrangement = Arrangement.spacedBy(AppUiTokens.CardGap),
                        ) {
                            row.forEach { grade ->
                                GradeCourseCard(grade, Modifier.weight(1f).fillMaxHeight()) { selected = grade }
                            }
                            if (row.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }
            Text("成绩、学分和绩点均按教务原文展示，点按课程查看详情。", fontSize = 13.sp,
                lineHeight = 20.sp, color = ds.sub, modifier = Modifier.padding(top = 12.dp))
        }
    }
}

@Composable
private fun GradeCourseCard(grade: TeachingGrade, modifier: Modifier, onClick: () -> Unit) {
    val ds = LocalDs.current
    AppCard(modifier, insideMargin = PaddingValues(AppUiTokens.CardPadding), onClick = onClick) {
        Text(grade.courseName.gradeText(), color = ds.text, fontSize = 17.sp,
            lineHeight = 24.sp, fontWeight = FontWeight.SemiBold, minLines = 2, maxLines = 2,
            overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(16.dp))
        Text("成绩", color = ds.sub, fontSize = 13.sp)
        Text(grade.score.gradeText(), color = ds.text, fontSize = 34.sp,
            lineHeight = 40.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
        Text("学分 ${grade.credits.gradeText()}", color = ds.sub, fontSize = 13.sp, lineHeight = 20.sp)
        Text("绩点 ${grade.gradePoint.gradeText()}", color = ds.sub, fontSize = 13.sp, lineHeight = 20.sp)
    }
}

@Composable
private fun GradeDetails(grade: TeachingGrade, onBack: () -> Unit) {
    SettingSubPage("成绩详情", "教务原文", onBack) {
        SectionHeader("成绩")
        val metrics = listOf(
            "成绩" to grade.score, "学分" to grade.credits,
            "绩点" to grade.gradePoint, "学分绩点" to grade.creditGradePoint,
        )
        Column(verticalArrangement = Arrangement.spacedBy(AppUiTokens.CardGap)) {
            metrics.chunked(2).forEach { row ->
                Row(
                    Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                    horizontalArrangement = Arrangement.spacedBy(AppUiTokens.CardGap),
                ) {
                    row.forEach { (label, value) ->
                        GradeMetricCard(label, value, Modifier.weight(1f).fillMaxHeight())
                    }
                }
            }
        }
        SectionHeader("课程")
        AppCard(Modifier.fillMaxWidth(), insideMargin = PaddingValues(AppUiTokens.CardPadding)) {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                GradeDetailField("课程名称", grade.courseName)
                GradeDetailField("课程代码", grade.courseCode)
                GradeDetailField("学年", grade.yearLabel)
                GradeDetailField("学期", grade.semesterLabel)
                GradeDetailField("课程性质", grade.courseNature)
                GradeDetailField("开课部门", grade.department)
                GradeDetailField("考试性质", grade.examType)
            }
        }
        val optionalFields = listOf(
            "课程类别" to grade.courseCategory, "课程归属" to grade.courseAttribute,
            "考核方式" to grade.assessmentMethod, "教学班" to grade.teachingClass,
            "任课教师" to grade.teacher, "是否学位课程" to grade.degreeCourse,
            "课程标记" to grade.courseMark,
        ).filter { it.second != null }
        if (optionalFields.isNotEmpty()) {
            SectionHeader("其他信息")
            AppCard(Modifier.fillMaxWidth(), insideMargin = PaddingValues(AppUiTokens.CardPadding)) {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    optionalFields.forEach { (label, value) -> GradeDetailField(label, value.orEmpty()) }
                }
            }
        }
    }
}

@Composable
private fun GradeMetricCard(label: String, value: String, modifier: Modifier) {
    val ds = LocalDs.current
    AppCard(modifier, insideMargin = PaddingValues(AppUiTokens.CardPadding)) {
        Text(label, color = ds.sub, fontSize = 13.sp)
        Spacer(Modifier.height(8.dp))
        Text(value.gradeText(), color = ds.text, fontSize = 34.sp,
            lineHeight = 40.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun GradeDetailField(label: String, value: String) {
    val ds = LocalDs.current
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, color = ds.sub, fontSize = 13.sp)
        Text(value.gradeText(), color = ds.text, fontSize = 16.sp, lineHeight = 24.sp)
    }
}

private fun String.gradeText(): String = ifBlank { "—" }
