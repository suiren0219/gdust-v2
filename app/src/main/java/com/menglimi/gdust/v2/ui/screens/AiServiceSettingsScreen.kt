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
import com.menglimi.gdust.v2.ui.AiSparkleIcon
import com.menglimi.gdust.v2.ui.AppCard
import com.menglimi.gdust.v2.ui.LocalDs
import top.yukonga.miuix.kmp.basic.Icon

@Composable
fun AiServiceSettingsScreen(
    repo: Repository,
    onBack: () -> Unit,
    onOpenNotices: () -> Unit,
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
    val canSave = base.isNotBlank() && key.isNotBlank() && model.isNotBlank()
    val primaryColors = ButtonDefaults.buttonColorsPrimary()
    val secondaryBase = if (theme.background.luminance() < 0.5f) Color.White else Color.Black
    val secondaryColors = ButtonDefaults.textButtonColors(
        color = secondaryBase,
        disabledColor = secondaryBase.copy(alpha = 0.06f),
    )

    SettingSubPage("AI 服务", "连接兼容 OpenAI Chat Completions 的模型", onBack) {
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
                onValueChange = { base = it; saved = false },
                label = "API 地址",
                placeholder = "https://api.example.com/v1",
                keyboardType = KeyboardType.Uri,
            )
            SettingDivider()
            AiConfigField(
                value = key,
                onValueChange = { key = it; saved = false },
                label = "API Key",
                placeholder = "输入服务密钥",
                keyboardType = KeyboardType.Password,
                visualTransformation = PasswordVisualTransformation(),
            )
            SettingDivider()
            AiConfigField(
                value = model,
                onValueChange = { model = it; saved = false },
                label = "模型名称",
                placeholder = "例如 gpt-4o-mini",
                imeAction = ImeAction.Done,
            )
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
                    onClick = onBack,
                    cornerRadius = 100.dp,
                    minWidth = 64.dp,
                    minHeight = 44.dp,
                    insideMargin = PaddingValues(horizontal = 16.dp, vertical = 0.dp),
                    colors = secondaryColors,
                    textStyle = TextStyle(fontSize = 14.sp),
                )
                NexioButton(
                    onClick = {
                        repo.store.llmBase = base.trim()
                        repo.store.llmKey = key.trim()
                        repo.store.llmModel = model.trim()
                        configured = repo.store.hasLlm
                        saved = true
                        focus.clearFocus()
                        haptic.performHapticFeedback(HapticFeedbackType.Confirm)
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
            CardExpand(visible = saved) {
                Text(
                    "已保存到本机，尚未验证连接。",
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
