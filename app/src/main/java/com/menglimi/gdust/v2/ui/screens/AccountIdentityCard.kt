package com.menglimi.gdust.v2.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.menglimi.gdust.v2.data.AccountLoginState
import com.menglimi.gdust.v2.data.Repository
import com.menglimi.gdust.v2.ui.AppCard
import com.menglimi.gdust.v2.ui.AppUiTokens
import com.menglimi.gdust.v2.ui.LocalDs
import androidx.compose.material3.Icon
import com.haooz.nexioui.button.ButtonDefaults
import com.haooz.nexioui.button.FlatTextButton
import com.haooz.nexioui.icon.NexioIcons
import com.haooz.nexioui.icon.extended.ContactsCircle
import com.haooz.nexioui.text.NexioText as Text
import kotlinx.coroutines.launch

/** 隐藏态不向可访问性树提供明文；页面离开或进入后台后重新遮罩。 */
@Composable
internal fun AccountIdentityCard(repo: Repository, onOpenProfile: (() -> Unit)? = null) {
    val state by repo.accountLogin.collectAsState()
    val session by repo.sessionVersion.collectAsState()
    val owner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    LaunchedEffect(repo, session) { repo.refreshAccountLogin() }
    DisposableEffect(owner, repo) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) scope.launch { repo.refreshAccountLogin() }
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    key(session) {
        AccountIdentityContent(repo.store.realName, repo.store.jobNumber, state,
            canVerify = repo.store.hasSession,
            onVerify = { scope.launch { repo.refreshAccountLogin(force = true) } },
            onOpenProfile = onOpenProfile)
    }
}

@Composable
internal fun AccountIdentityContent(
    name: String,
    account: String,
    state: AccountLoginState,
    canVerify: Boolean,
    onVerify: () -> Unit,
    onOpenProfile: (() -> Unit)? = null,
) {
    val ds = LocalDs.current
    var revealed by remember(name, account) { mutableStateOf(false) }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_PAUSE) revealed = false }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    val hasIdentity = name.isNotBlank() || account.isNotBlank()
    val valid = state == AccountLoginState.VALID
    AppCard(
        modifier = Modifier.fillMaxWidth(),
        color = if (valid) ds.successTint else ds.card,
        insideMargin = PaddingValues(AppUiTokens.CardPadding),
    ) {
        Column(Modifier.fillMaxWidth().heightIn(min = 56.dp)
            .semantics { stateDescription = if (revealed) "身份已展开" else "身份已隐藏" }
            .clickable(enabled = hasIdentity, role = Role.Button,
                onClickLabel = if (revealed) "隐藏身份" else "展开身份") { revealed = !revealed }) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Icon(NexioIcons.ContactsCircle, null, Modifier.size(28.dp), tint = if (valid) ds.success else ds.sub)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(when {
                        !hasIdentity -> "未登录"
                        revealed -> name.ifBlank { "个人信息" }
                        else -> "*****"
                    }, fontSize = 17.sp, fontWeight = FontWeight.Medium, color = ds.text)
                    Text(if (hasIdentity) "广东科技学院 · " + if (revealed) account.ifBlank { "—" } else "*****"
                        else "广东科技学院 · 本机数据与偏好", fontSize = 13.sp, color = ds.sub)
                    if (hasIdentity) Text(if (revealed) "点按隐藏姓名与学号" else "点按展开姓名与学号", fontSize = 12.sp, color = ds.sub)
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        val actionColors = ButtonDefaults.textButtonColors(
            disabledColor = (if (ds.isDark) Color.White else Color.Black).copy(alpha = 0.06f),
        )
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(state.label, fontSize = 13.sp, fontWeight = FontWeight.Medium,
                color = if (state == AccountLoginState.EXPIRED) ds.danger else ds.text,
                modifier = Modifier.weight(1f))
            if (canVerify) FlatTextButton(
                text = "验证登录",
                enabled = state != AccountLoginState.CHECKING,
                onClick = onVerify,
                minHeight = 44.dp,
                cornerRadius = 100.dp,
                insideMargin = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                textStyle = TextStyle(fontSize = 14.sp),
                colors = actionColors,
            )
            if (revealed && onOpenProfile != null) FlatTextButton(
                text = "个人信息",
                onClick = onOpenProfile,
                minHeight = 44.dp,
                cornerRadius = 100.dp,
                insideMargin = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                textStyle = TextStyle(fontSize = 14.sp),
                colors = actionColors,
            )
        }
    }
    Spacer(Modifier.height(AppUiTokens.CardGap))
}
