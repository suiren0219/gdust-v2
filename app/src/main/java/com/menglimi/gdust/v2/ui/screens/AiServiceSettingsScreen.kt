package com.menglimi.gdust.v2.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
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
import com.menglimi.gdust.v2.data.Repository
import com.menglimi.gdust.v2.ui.AiSparkleIcon
import com.menglimi.gdust.v2.ui.AppCard
import com.menglimi.gdust.v2.ui.AppUiTokens
import com.menglimi.gdust.v2.ui.LocalDs
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

    SettingSubPage("AI 服务", "连接兼容 OpenAI Chat Completions 的模型", onBack) {
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
                    onValueChange = { base = it; saved = false },
                    label = "API 地址",
                    placeholder = "https://api.example.com/v1",
                    keyboardType = KeyboardType.Uri,
                )
                AiConfigField(
                    value = key,
                    onValueChange = { key = it; saved = false },
                    label = "API Key",
                    placeholder = "输入服务密钥",
                    visualTransformation = PasswordVisualTransformation(),
                )
                AiConfigField(
                    value = model,
                    onValueChange = { model = it; saved = false },
                    label = "模型名称",
                    placeholder = "例如 gpt-4o-mini",
                )
                Button(
                    onClick = {
                        repo.store.llmBase = base.trim()
                        repo.store.llmKey = key.trim()
                        repo.store.llmModel = model.trim()
                        configured = repo.store.hasLlm
                        saved = true
                    },
                    enabled = base.isNotBlank() && key.isNotBlank() && model.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                    minHeight = 46.dp,
                    insideMargin = PaddingValues(horizontal = 18.dp, vertical = 0.dp),
                ) {
                    Text(if (saved) "已保存" else "保存配置", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                }
            }
        }
        if (saved) {
            Text("已保存到本机，尚未验证连接。", fontSize = 12.sp, color = ds.sub,
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
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = ds.sub,
            modifier = Modifier.padding(start = 2.dp))
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(AppUiTokens.ItemCorner))
                .background(ds.bg).padding(horizontal = 14.dp, vertical = 14.dp),
            singleLine = true,
            visualTransformation = visualTransformation,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            textStyle = androidx.compose.ui.text.TextStyle(color = ds.text, fontSize = 15.sp),
            cursorBrush = SolidColor(ds.accent),
            decorationBox = { inner ->
                Box {
                    if (value.isEmpty()) Text(placeholder, fontSize = 14.sp, color = ds.sub)
                    inner()
                }
            },
        )
    }
}
