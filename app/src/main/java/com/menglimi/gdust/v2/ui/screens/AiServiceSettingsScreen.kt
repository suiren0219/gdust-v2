package com.menglimi.gdust.v2.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.haooz.nexioui.button.Button as NexioButton
import com.haooz.nexioui.button.ButtonDefaults
import com.haooz.nexioui.button.FlatTextButton
import com.haooz.nexioui.cardexpand.CardExpand
import com.haooz.nexioui.text.NexioText as Text
import com.haooz.nexioui.theme.NexioTheme
import com.menglimi.gdust.v2.data.Repository
import com.menglimi.gdust.v2.data.GdustApi
import com.menglimi.gdust.v2.data.LlmException
import com.menglimi.gdust.v2.data.validateLlmConfiguration
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import com.menglimi.gdust.v2.ui.AiSparkleIcon
import com.menglimi.gdust.v2.ui.AppCard
import com.menglimi.gdust.v2.ui.LocalDs
import top.yukonga.miuix.kmp.basic.Icon

@Composable
fun AiServiceSettingsScreen(
    repo: Repository,
    onBack: () -> Unit,
    onOpenNotices: () -> Unit,
    offlinePreview: Boolean = false,
) {
    val ds = LocalDs.current
    val theme = NexioTheme.colorScheme
    val focus = LocalFocusManager.current
    val haptic = LocalHapticFeedback.current
    var base by rememberSaveable { mutableStateOf(repo.store.llmBase) }
    // 密钥只留在当前组合的内存中，不写入 saved state，输入始终遮罩。
    var key by remember { mutableStateOf(repo.store.llmKey) }
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
        if (offlinePreview) return
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

    DisposableEffect(offlinePreview) {
        if (offlinePreview) cancelPending()
        onDispose { requestJob?.cancel() }
    }

    val canSave = !offlinePreview && base.isNotBlank() && key.isNotBlank() && model.isNotBlank()
    val primaryColors = ButtonDefaults.buttonColorsPrimary()
    val secondaryBase = if (theme.background.luminance() < 0.5f) Color.White else Color.Black
    val secondaryColors = ButtonDefaults.textButtonColors(
        color = secondaryBase,
        disabledColor = secondaryBase.copy(alpha = 0.06f),
    )

    SettingSubPage("AI 服务", "连接兼容 OpenAI Chat Completions 的模型", { cancelPending(); onBack() }) {
        AppCard(Modifier.fillMaxWidth()) {
            Row(
                Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(AiSparkleIcon, contentDescription = null, modifier = Modifier.size(22.dp), tint = ds.accent)
                Column(Modifier.weight(1f).padding(start = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        if (configured) "已配置模型服务" else "尚未配置模型服务",
                        fontSize = 17.sp, fontWeight = FontWeight.Medium, color = ds.text,
                    )
                    Text(
                        "对话与勾选的课表、待办摘要只发往你填写的地址，密钥存本机加密存储。",
                        fontSize = 13.sp, lineHeight = 19.sp, color = ds.sub,
                    )
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        SectionHeader("连接参数")
        SettingGroup {
            AiConfigField(
                value = base,
                onValueChange = { inputChanged(clearModels = true); base = it },
                label = "API 地址",
                placeholder = "https://api.example.com/v1",
                keyboardType = KeyboardType.Uri,
            )
            SettingDivider()
            AiConfigField(
                value = key,
                onValueChange = { inputChanged(clearModels = true); key = it },
                label = "API Key",
                placeholder = "输入服务密钥",
                keyboardType = KeyboardType.Password,
                visualTransformation = PasswordVisualTransformation(),
            )
            SettingDivider()
            AiConfigField(
                value = model,
                onValueChange = { inputChanged(clearModels = false); model = it },
                label = "模型名称",
                placeholder = "例如 gpt-4o-mini",
                imeAction = ImeAction.Done,
            )
            if (offlinePreview) {
                Text("离线预览 · 网络请求与配置保存已禁用。", fontSize = 13.sp, color = ds.sub,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
            }
            SettingEditor {
                NexioActionButton(
                    if (busy == "models") "正在获取模型…" else "获取模型列表",
                    enabled = !offlinePreview && busy == null && base.isNotBlank() && key.isNotBlank(),
                ) { startRequest(fetchModels = true) }
                modelsStatus?.let {
                    Text(it, fontSize = 13.sp, lineHeight = 19.sp, color = ds.sub)
                }
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
            SettingEditor {
                Text("测试连接会向当前地址的所选模型发送少量合成测试内容，可能产生费用；不会发送课表、公告或聊天记录。",
                    fontSize = 13.sp, lineHeight = 19.sp, color = ds.sub)
                NexioActionButton(
                    if (busy == "probe") "正在测试连接…" else "测试连接（可能计费）",
                    enabled = !offlinePreview && busy == null && base.isNotBlank() && key.isNotBlank() && model.isNotBlank(),
                ) { startRequest(fetchModels = false) }
                if (busy != null) {
                    NexioActionButton("取消请求") {
                        val wasModels = busy == "models"
                        cancelPending()
                        if (wasModels) modelsStatus = "已取消获取模型，仍可手动填写。"
                        else {
                            probeFailed = false
                            probeStatus = "已取消等待；已发送的测试仍可能计费。"
                        }
                    }
                }
                probeStatus?.let {
                    Text(it, fontSize = 13.sp, lineHeight = 19.sp, color = if (probeFailed) ds.danger else ds.sub)
                }
            }
            Text(
                "保存仅更新本机配置，不会自动验证连接。密钥始终以掩码显示。",
                fontSize = 13.sp, lineHeight = 19.sp, color = ds.sub,
                modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 14.dp),
            )
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FlatTextButton(
                    text = "返回",
                    onClick = { cancelPending(); onBack() },
                    cornerRadius = 100.dp,
                    minWidth = 64.dp,
                    minHeight = 44.dp,
                    insideMargin = PaddingValues(horizontal = 16.dp, vertical = 0.dp),
                    colors = secondaryColors,
                    textStyle = TextStyle(fontSize = 14.sp),
                )
                NexioButton(
                    onClick = {
                        if (offlinePreview) return@NexioButton
                        saveError = null
                        try {
                            validateLlmConfiguration(base, key, model)
                            repo.store.llmBase = base.trim()
                            repo.store.llmKey = key.trim()
                            repo.store.llmModel = model.trim()
                            configured = repo.store.hasLlm
                            saved = true
                            focus.clearFocus()
                            haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                        } catch (error: Exception) {
                            saved = false
                            saveError = (error as? LlmException)?.message ?: "无法保存配置，请稍后重试。"
                        }
                    },
                    enabled = canSave,
                    cornerRadius = 100.dp,
                    minWidth = 76.dp,
                    minHeight = 44.dp,
                    insideMargin = PaddingValues(horizontal = 20.dp, vertical = 0.dp),
                    colors = primaryColors,
                ) {
                    Text(
                        if (saved) "已保存" else "保存配置",
                        fontSize = 14.sp, fontWeight = FontWeight.Medium,
                        color = if (canSave) primaryColors.contentColor else primaryColors.disabledContentColor,
                    )
                }
            }
            saveError?.let { Text(it, fontSize = 13.sp, color = ds.danger, modifier = Modifier.padding(horizontal = 16.dp)) }
            CardExpand(visible = saved) {
                Text(
                    "已保存到本机；保存不会联网，连接状态请参考测试结果。",
                    fontSize = 13.sp, lineHeight = 19.sp, color = ds.sub,
                    modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 14.dp),
                )
            }
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
    visualTransformation: VisualTransformation = VisualTransformation.None,
    imeAction: ImeAction = ImeAction.Next,
) {
    val ds = LocalDs.current
    val focus = LocalFocusManager.current
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, fontSize = 17.sp, fontWeight = FontWeight.Medium, color = ds.text)
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.weight(1f).padding(start = 16.dp).heightIn(min = 44.dp)
                .semantics { contentDescription = label },
            singleLine = true,
            visualTransformation = visualTransformation,
            keyboardOptions = KeyboardOptions(
                keyboardType = keyboardType,
                autoCorrectEnabled = false,
                imeAction = imeAction,
            ),
            keyboardActions = KeyboardActions(
                onNext = { focus.moveFocus(FocusDirection.Next) },
                onDone = { focus.clearFocus() },
            ),
            textStyle = TextStyle(
                color = ds.text, fontSize = 17.sp, fontWeight = FontWeight.Medium,
                textAlign = TextAlign.End,
            ),
            cursorBrush = SolidColor(ds.accent),
            decorationBox = { inner ->
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                    if (value.isEmpty()) Text(
                        placeholder, fontSize = 14.sp, color = ds.sub,
                        textAlign = TextAlign.End, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                    inner()
                }
            },
        )
    }
}
