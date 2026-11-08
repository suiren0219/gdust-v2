package com.menglimi.gdust.v2.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.menglimi.gdust.v2.data.Repository
import com.menglimi.gdust.v2.ui.GlassCard
import com.menglimi.gdust.v2.ui.LocalDs
import com.menglimi.gdust.v2.ui.LocalUiStyle
import com.menglimi.gdust.v2.ui.UiStyle
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    repo: Repository,
    syncError: String?,
    onSync: () -> Unit,
    onLogout: () -> Unit,
    onBack: () -> Unit,
    loggedIn: Boolean = true,
) {
    val ds = LocalDs.current
    val store = repo.store
    val scope = rememberCoroutineScope()

    var base by remember { mutableStateOf(store.llmBase) }
    var key by remember { mutableStateOf(store.llmKey) }
    var model by remember { mutableStateOf(store.llmModel) }
    var saved by remember { mutableStateOf(false) }
    var summary by remember { mutableStateOf<String?>(null) }
    var summarizing by remember { mutableStateOf(false) }
    var summaryError by remember { mutableStateOf("") }
    var thresholdText by remember { mutableStateOf(store.warnThreshold.toInt().toString()) }
    var dropText by remember { mutableStateOf(store.warnDropRate.toInt().toString()) }
    var thresholdSaved by remember { mutableStateOf(false) }
    var synced by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .background(ds.bg)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
    ) {
        Spacer(Modifier.height(24.dp))
        PageTitle(title = "设置", subtitle = "外观 · 数据 · 账号 · 关于", onBack = onBack)

        // ── 外观 ──
        SectionHeader("外观")
        GlassCard {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("界面风格", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = ds.text)
                Text("切换后整套主题、卡片、输入框、按钮、底栏都换风格", fontSize = 12.sp, color = ds.sub)
                val styleState = LocalUiStyle.current
                SegmentedChoice(
                    options = UiStyle.entries.map { it to it.label },
                    selected = styleState.value,
                    onSelect = { styleState.value = it },
                )
                Text(styleState.value.desc, fontSize = 11.sp, color = ds.sub)
            }
        }

        // ── 账号与数据 ──
        SectionHeader("账号与数据")
        GlassCard {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(store.realName.ifEmpty { "未登录" }, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = ds.text)
                SettingRow(
                    title = "学号",
                    detail = store.jobNumber.ifEmpty { "—" },
                )
                Text("本机直连门户，登录凭证只存本机；密码不落盘", fontSize = 11.sp, color = ds.sub)
            }
        }
        Spacer(Modifier.height(10.dp))
        GlassCard(onClick = {
            onSync()
            synced = true
        }) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("同步课表", fontSize = 15.sp, fontWeight = FontWeight.Medium, color = ds.text)
                    Text("拉取课表 + 校历，连堂自动合并成整卡", fontSize = 11.sp, color = ds.sub, modifier = Modifier.padding(top = 3.dp))
                    if (synced && syncError == null) {
                        Text("已同步 ✓", fontSize = 11.sp, color = ds.accent, modifier = Modifier.padding(top = 4.dp))
                    }
                    if (syncError != null) {
                        Text("失败：$syncError", fontSize = 11.sp, color = ds.danger, modifier = Modifier.padding(top = 4.dp))
                    }
                }
                Text("›", fontSize = 18.sp, color = ds.sub)
            }
        }
        Spacer(Modifier.height(10.dp))
        GlassCard {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("课表数据", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = ds.text)
                SettingRow(title = "本地缓存课程", detail = "${store.loadBlocks().size} 张卡")
                SettingRow(title = "校历", detail = store.loadCalendar()?.let { "第 ${it.week}/${it.allWeek} 周" } ?: "未同步")
            }
        }

        // ── 通知 AI 总结 ──
        SectionHeader("通知 AI 总结")
        GlassCard {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("中转站配置", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = ds.text)
                Text("用户自填 OpenAI 兼容端点；不填则不启用，密钥只存本机", fontSize = 11.sp, color = ds.sub)
                InputField(base, { base = it; saved = false }, "API Base，如 https://…/v1")
                InputField(key, { key = it; saved = false }, "API Key", visual = PasswordVisualTransformation())
                InputField(model, { model = it; saved = false }, "模型名")
                Button(
                    text = if (saved) "已保存 ✓" else "保存配置",
                    enabled = base.isNotBlank() && key.isNotBlank() && model.isNotBlank(),
                    onClick = {
                        store.llmBase = base.trim()
                        store.llmKey = key.trim()
                        store.llmModel = model.trim()
                        saved = true
                    },
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        GlassCard {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("生成总结", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = ds.text)
                summary?.let { Text(it, fontSize = 13.sp, lineHeight = 18.sp, color = ds.text) }
                    ?: Text(
                        if (store.hasLlm) "把最近的学校公告交给你的模型提炼要点"
                        else "先填好上面的中转站配置，才能生成总结",
                        fontSize = 12.sp,
                        color = ds.sub,
                    )
                Button(
                    text = if (summarizing) "思考中…" else if (summary == null) "生成总结" else "重新总结",
                    enabled = store.hasLlm && !summarizing,
                    onClick = {
                        summarizing = true
                        summaryError = ""
                        scope.launch {
                            runCatching { repo.summarize() }
                                .onSuccess { summary = it }
                                .onFailure { summaryError = it.message ?: "总结失败" }
                            summarizing = false
                        }
                    },
                )
                if (summaryError.isNotEmpty()) Text(summaryError, fontSize = 12.sp, color = ds.danger)
            }
        }

        // ── 水电预警 ──
        SectionHeader("水电预警")
        GlassCard {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("阈值与采样", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = ds.text)
                Text("后台每 15 分钟巡检，按自选间隔采样；同类预警 12 小时冷却", fontSize = 11.sp, color = ds.sub)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    InputField(thresholdText, { thresholdText = it; thresholdSaved = false }, "低电量阈值(度)", KeyboardType.Number, Modifier.weight(1f))
                    InputField(dropText, { dropText = it; thresholdSaved = false }, "掉电速率(度/时)", KeyboardType.Number, Modifier.weight(1f))
                }
                Button(if (thresholdSaved) "已保存 ✓" else "保存阈值") {
                    thresholdText.toFloatOrNull()?.let { store.warnThreshold = it }
                    dropText.toFloatOrNull()?.let { store.warnDropRate = it }
                    thresholdSaved = true
                }
                Text("采样间隔：在「电费」页可切换 15 / 30 / 60 / 120 分钟", fontSize = 11.sp, color = ds.sub)
            }
        }

        // ── 关于与账户 ──
        SectionHeader("关于")
        GlassCard {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                SettingRow(title = "版本", detail = "v2 · 双风格")
                SettingRow(title = "数据链路", detail = "本机直连")
                Text("课表/校历/通知 → 学校门户；水电 → 完美校园；总结 → 你自填的 API", fontSize = 11.sp, color = ds.sub)
                Text("不使用任何自建服务器，无账号体系、无遥测", fontSize = 11.sp, color = ds.sub)
            }
        }
        if (loggedIn) {
            Spacer(Modifier.height(10.dp))
            GlassCard(onClick = onLogout) {
                Text("退出登录并清除本机数据", fontSize = 15.sp, fontWeight = FontWeight.Medium, color = ds.danger)
            }
        }

        Spacer(Modifier.height(24.dp))
        Text(
            "广科课程表 · 闭源自用版",
            fontSize = 11.sp,
            color = ds.sub,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
        Spacer(Modifier.height(110.dp))
    }
}
