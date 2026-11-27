package com.menglimi.gdust.v2.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.menglimi.gdust.v2.data.GdustApi
import com.menglimi.gdust.v2.data.LlmException
import com.menglimi.gdust.v2.data.Repository
import com.menglimi.gdust.v2.data.validateLlmConfiguration
import com.menglimi.gdust.v2.ui.AiSparkleIcon
import com.menglimi.gdust.v2.ui.AppCard
import com.menglimi.gdust.v2.ui.AppUiTokens
import com.menglimi.gdust.v2.ui.LocalDs
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text

@Composable
fun AiServiceSettingsScreen(
    repo: Repository,
    onBack: () -> Unit,
    onOpenNotices: () -> Unit,
) {
    val ds = LocalDs.current
    var base by rememberSaveable { mutableStateOf(repo.store.llmBase) }
    var key by androidx.compose.runtime.remember { mutableStateOf(repo.store.llmKey) }
    var model by rememberSaveable { mutableStateOf(repo.store.llmModel) }
    var configured by rememberSaveable { mutableStateOf(repo.store.hasLlm) }
    var saved by rememberSaveable { mutableStateOf(false) }
    val api = remember { GdustApi() }
    val scope = rememberCoroutineScope()
    var requestJob by remember { mutableStateOf<Job?>(null) }
    var requestVersion by remember { mutableStateOf(0L) }
    var busy by remember { mutableStateOf<String?>(null) }
    var models by remember { mutableStateOf<List<String>>(emptyList()) }
    var showModels by remember { mutableStateOf(false) }
    var modelsStatus by remember { mutableStateOf<String?>(null) }
    var probeStatus by remember { mutableStateOf<String?>(null) }
    var probeFailed by remember { mutableStateOf(false) }
    var saveError by remember { mutableStateOf<String?>(null) }

    fun cancelPending() {
        requestVersion++
        requestJob?.cancel()
        requestJob = null
        busy = null
    }

    fun inputChanged(clearModels: Boolean) {
        cancelPending()
        saved = false
        saveError = null
        probeStatus = null
        if (clearModels) {
            models = emptyList()
            showModels = false
            modelsStatus = null
        }
    }

    fun startRequest(fetchModels: Boolean) {
        cancelPending()
        val version = requestVersion
        val snapshot = Triple(base, key, model)
        busy = if (fetchModels) "models" else "probe"
        if (fetchModels) modelsStatus = null else probeStatus = null
        requestJob = scope.launch {
            fun current() = version == requestVersion && snapshot == Triple(base, key, model)
            try {
                if (fetchModels) {
                    val result = api.models(snapshot.first, snapshot.second)
                    ensureActive()
                    if (current()) {
                        models = result
                        showModels = result.isNotEmpty()
                        modelsStatus = if (result.isEmpty()) "服务未返回可选模型，仍可手动填写。"
                            else "已获取 ${result.size} 个模型，选择后仍可手动修改。"
                    }
                } else {
                    val result = api.probe(snapshot.first, snapshot.second, snapshot.third)
                    ensureActive()
                    if (current()) {
                        probeFailed = !result.success
                        probeStatus = if (result.success) "连接成功 · ${result.latencyMs} ms（含生成耗时）"
                            else "测试失败 · ${result.latencyMs} ms\n${result.error ?: "请检查服务配置。"}"
                    }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                ensureActive()
                if (current()) {
                    val hint = (error as? LlmException)?.message ?: "请求失败，请检查网络与服务配置。"
                    if (fetchModels) {
                        modelsStatus = "$hint\n仍可手动填写模型名称。"
                    } else {
                        probeFailed = true
                        probeStatus = hint
                    }
                }
            } finally {
                if (current()) {
                    busy = null
                    requestJob = null
                }
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose { requestJob?.cancel() }
    }

    SettingSubPage("AI 服务", "连接兼容 OpenAI Chat Completions 的模型", { cancelPending(); onBack() }) {
        AppCard(Modifier.fillMaxWidth()) {
            Row(
                Modifier.padding(horizontal = 16.dp, vertical = 15.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(AiSparkleIcon, contentDescription = null, modifier = Modifier.size(22.dp), tint = ds.accent)
                Column(Modifier.weight(1f).padding(start = 12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(
                        if (configured) "已配置模型服务" else "尚未配置模型服务",
                        fontSize = 15.sp, fontWeight = FontWeight.Medium, color = ds.text,
                    )
                    Text("对话与勾选的课表摘要只发往你填写的地址，密钥存本机加密存储。",
                        fontSize = 12.sp, lineHeight = 18.sp, color = ds.sub)
                }
            }
        }
        SectionHeader("连接参数")
        AppCard(Modifier.fillMaxWidth()) {
            Column(
                Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                AiConfigField(
                    value = base,
                    onValueChange = { inputChanged(clearModels = true); base = it },
                    label = "API 地址",
                    placeholder = "https://api.example.com/v1",
                    keyboardType = KeyboardType.Uri,
                )
                Text("根地址默认使用 /v1；自定义前缀保持不变，也可填写完整 /models 或 /chat/completions 地址。建议使用 HTTPS。",
                    fontSize = 12.sp, lineHeight = 18.sp, color = ds.sub)
                AiConfigField(
                    value = key,
                    onValueChange = { inputChanged(clearModels = true); key = it },
                    label = "API Key",
                    placeholder = "输入服务密钥",
                    visualTransformation = PasswordVisualTransformation(),
                )
                AiConfigField(
                    value = model,
                    onValueChange = { inputChanged(clearModels = false); model = it },
                    label = "模型名称",
                    placeholder = "例如 gpt-4o-mini",
                )
                Button(
                    onClick = { startRequest(fetchModels = true) },
                    enabled = busy == null && base.isNotBlank() && key.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(), minHeight = 46.dp,
                ) {
                    Text(if (busy == "models") "正在获取模型…" else "获取模型列表", fontSize = 14.sp)
                }
                modelsStatus?.let {
                    Text(it, fontSize = 12.sp, lineHeight = 18.sp, color = ds.sub)
                }
                if (models.isNotEmpty()) {
                    SettingOption(
                        title = "可选模型",
                        subtitle = "列表不代表模型一定支持对话，选定后可测试连接。",
                        detail = if (showModels) "收起" else "展开 ${models.size} 项",
                        onClick = { showModels = !showModels },
                    )
                    if (showModels) {
                        LazyColumn(Modifier.fillMaxWidth().heightIn(max = 240.dp)) {
                            items(models, key = { it }) { item ->
                                SettingOption(
                                    title = item,
                                    detail = if (model.trim() == item) "已选择" else null,
                                    onClick = {
                                        inputChanged(clearModels = false)
                                        model = item
                                        showModels = false
                                    },
                                )
                            }
                        }
                    }
                }
                Text("测试连接会向当前地址的所选模型发送少量合成测试内容，可能产生费用；不会发送课表、公告或聊天记录。",
                    fontSize = 12.sp, lineHeight = 18.sp, color = ds.sub)
                Button(
                    onClick = { startRequest(fetchModels = false) },
                    enabled = busy == null && base.isNotBlank() && key.isNotBlank() && model.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(), minHeight = 46.dp,
                ) {
                    Text(if (busy == "probe") "正在测试连接…" else "测试连接（可能计费）", fontSize = 14.sp)
                }
                if (busy != null) {
                    Button(
                        onClick = {
                            val wasModels = busy == "models"
                            cancelPending()
                            if (wasModels) modelsStatus = "已取消获取模型，仍可手动填写。"
                            else {
                                probeFailed = false
                                probeStatus = "已取消等待；已发送的测试仍可能计费。"
                            }
                        },
                        modifier = Modifier.fillMaxWidth(), minHeight = 46.dp,
                    ) { Text("取消请求", fontSize = 14.sp) }
                }
                probeStatus?.let {
                    Text(it, fontSize = 12.sp, lineHeight = 18.sp, color = if (probeFailed) ds.danger else ds.sub)
                }
                Button(
                    onClick = {
                        saveError = null
                        try {
                            validateLlmConfiguration(base, key, model)
                            repo.store.llmBase = base.trim()
                            repo.store.llmKey = key.trim()
                            repo.store.llmModel = model.trim()
                            configured = repo.store.hasLlm
                            saved = true
                        } catch (error: Exception) {
                            saved = false
                            saveError = (error as? LlmException)?.message ?: "无法保存配置，请稍后重试。"
                        }
                    },
                    enabled = base.isNotBlank() && key.isNotBlank() && model.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                    minHeight = 46.dp,
                    insideMargin = PaddingValues(horizontal = 18.dp, vertical = 0.dp),
                ) {
                    Text(if (saved) "已保存" else "保存配置", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                }
                saveError?.let {
                    Text(it, fontSize = 12.sp, lineHeight = 18.sp, color = ds.danger)
                }
            }
        }
        if (saved) {
            Text("已保存到本机；保存操作不会联网，连接状态见上方测试结果。", fontSize = 12.sp, color = ds.sub,
                modifier = Modifier.padding(start = 4.dp, top = 8.dp))
        }
        SectionHeader("相关功能")
        SettingGroup {
            SettingOption(
                title = "公告总结",
                subtitle = "对当前获取的公告标题生成简短摘要",
                detail = if (configured) "已就绪" else "需配置",
                onClick = onOpenNotices,
            )
        }
    }
}

@Composable
private fun AiConfigField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String,
    keyboardType: KeyboardType = KeyboardType.Text,
    visualTransformation: androidx.compose.ui.text.input.VisualTransformation = androidx.compose.ui.text.input.VisualTransformation.None,
) {
    val ds = LocalDs.current
    val focusManager = LocalFocusManager.current
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = ds.text)
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                .semantics { contentDescription = label },
            singleLine = true,
            visualTransformation = visualTransformation,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
            textStyle = androidx.compose.ui.text.TextStyle(color = ds.text, fontSize = 17.sp),
            cursorBrush = SolidColor(ds.accent),
            decorationBox = { inner ->
                Box(Modifier.fillMaxWidth().heightIn(min = 48.dp), contentAlignment = Alignment.CenterStart) {
                    if (value.isEmpty()) Text(placeholder, fontSize = 15.sp, color = ds.sub)
                    inner()
                }
            },
        )
    }
}
