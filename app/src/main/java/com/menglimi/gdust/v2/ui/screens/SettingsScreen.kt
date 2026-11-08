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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.menglimi.gdust.v2.data.Repository
import com.menglimi.gdust.v2.ui.GlassCard
import com.menglimi.gdust.v2.ui.LocalDs

@Composable
fun SettingsScreen(
    repo: Repository,
    syncError: String?,
    onSync: () -> Unit,
    onLogout: () -> Unit,
    onBack: () -> Unit,
) {
    val ds = LocalDs.current
    val store = repo.store

    var base by remember { mutableStateOf(store.llmBase) }
    var key by remember { mutableStateOf(store.llmKey) }
    var model by remember { mutableStateOf(store.llmModel) }
    val scope = rememberCoroutineScope()
    var saved by remember { mutableStateOf(false) }
    var summary by remember { mutableStateOf<String?>(null) }
    var summarizing by remember { mutableStateOf(false) }
    var summaryError by remember { mutableStateOf("") }
    var thresholdText by remember { mutableStateOf(store.warnThreshold.toInt().toString()) }
    var dropText by remember { mutableStateOf(store.warnDropRate.toInt().toString()) }

    Column(
        Modifier
            .fillMaxSize()
            .background(ds.bg)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
    ) {
        Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("<", fontSize = 22.sp, color = ds.accent, modifier = Modifier.clickable { onBack() }.padding(horizontal = 10.dp))
            Text("设置", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = ds.text)
        }

        // 账号 + 同步：Glasmio 原生分组玻璃列表
        io.github.neoruaa.glasmio.ui.GlasmioGroupedList {
            io.github.neoruaa.glasmio.ui.GlasmioListRow(
                title = store.realName.ifEmpty { "已登录" },
                subtitle = "学号 ${store.jobNumber} · 本机直连门户，登录凭证只存本机",
                showTopSeparator = false,
            )
            io.github.neoruaa.glasmio.ui.GlasmioListRow(
                title = "同步课表",
                subtitle = "拉取课表+校历并合并连堂",
                onClick = onSync,
            )
        }
        if (syncError != null) {
            Text("同步失败：$syncError", fontSize = 12.sp, color = ds.danger, modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp))
        }

        // LLM
        Spacer(Modifier.height(12.dp))
        GlassCard {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("通知 AI 总结", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = ds.text)
                Text("用户自填 OpenAI 兼容端点；不填则不启用，密钥只存本机", fontSize = 11.sp, color = ds.sub)
                InputField(base, { base = it; saved = false }, "API Base，如 https://…/v1")
                InputField(key, { key = it; saved = false }, "API Key", visual = PasswordVisualTransformation())
                InputField(model, { model = it; saved = false }, "模型名")
                Button(
                    text = if (saved) "已保存 ✓" else "保存",
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

        // 通知总结
        if (store.hasLlm) {
            Spacer(Modifier.height(12.dp))
            GlassCard {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("通知 AI 总结", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = ds.text)
                    summary?.let { Text(it, fontSize = 13.sp, color = ds.text) }
                        ?: Text("把最近的学校公告交给你的中转站模型提炼要点", fontSize = 12.sp, color = ds.sub)
                    Button(
                        text = if (summarizing) "思考中…" else if (summary == null) "生成总结" else "重新总结",
                        enabled = !summarizing,
                        onClick = {
                            summarizing = true
                            summaryError = ""
                            // 用 MainScope 起协程：设置页有独立 scope
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
        }

        // 预警阈值
        Spacer(Modifier.height(12.dp))
        GlassCard {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("水电预警", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = ds.text)
                Text("阈值（度）/ 掉电速率（度/小时）", fontSize = 12.sp, color = ds.sub)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    InputField(thresholdText, { thresholdText = it }, "低电量阈值", KeyboardType.Number, Modifier.weight(1f))
                    InputField(dropText, { dropText = it }, "掉电速率", KeyboardType.Number, Modifier.weight(1f))
                }
                Button("保存阈值") {
                    thresholdText.toFloatOrNull()?.let { store.warnThreshold = it }
                    dropText.toFloatOrNull()?.let { store.warnDropRate = it }
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        GlassCard(Modifier.clickable { onLogout() }) {
            Text("退出登录并清除本机数据", fontSize = 15.sp, color = ds.danger)
        }

        Spacer(Modifier.height(16.dp))
        Text("广科课程表 v2 · 闭源自用版 · 数据全部本机直连学校系统", fontSize = 11.sp, color = ds.sub)
        Spacer(Modifier.height(40.dp))
    }
}
