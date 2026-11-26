// 服务连接状态卡：门户、教务与学习通三行独立展示，各自验证与补连互不影响。
// 状态词只按数据层已分类的证据描述：认证失效才给补连按钮；
// 网络不通与读取失败都是「重试验证」，未分类的异常一律 UNCONFIRMED，不冒充 NETWORK。
// 行内纵向排版：状态词全宽换行，操作按钮独占底部，窄屏大字号下不被挤成一字一行。
package com.menglimi.gdust.v2.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.haooz.nexioui.button.ButtonDefaults
import com.haooz.nexioui.button.TextButtonColors
import com.haooz.nexioui.text.NexioText as Text
import com.haooz.nexioui.theme.NexioTheme
import com.menglimi.gdust.v2.data.Repository
import com.menglimi.gdust.v2.data.SchoolService
import com.menglimi.gdust.v2.data.SchoolServiceStatus
import com.menglimi.gdust.v2.data.ServiceAuthStatus
import com.menglimi.gdust.v2.data.ServiceProblem
import com.menglimi.gdust.v2.ui.AppCard
import com.menglimi.gdust.v2.ui.AppUiTokens
import com.menglimi.gdust.v2.ui.LocalDs
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 三项学校服务的连接状态卡；进入页面不自动探活，验证只由本卡按钮逐项触发。 */
@Composable
fun ServiceStatusCard(
    repo: Repository,
    onReconnect: (SchoolService) -> Unit,
    modifier: Modifier = Modifier,
    actionsEnabled: Boolean = true,
    offlinePreview: Boolean = false,
) {
    val ds = LocalDs.current
    val state by repo.serviceConnections.collectAsState()
    val actionColors = ButtonDefaults.textButtonColors(
        disabledColor = (if (NexioTheme.colorScheme.background.luminance() < 0.5f) {
            Color.White
        } else {
            Color.Black
        }).copy(alpha = 0.06f),
    )
    AppCard(
        modifier = modifier.fillMaxWidth(),
        insideMargin = PaddingValues(AppUiTokens.CardPadding),
    ) {
        Column {
            Text("服务连接", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = ds.text)
            Text(
                if (offlinePreview) "离线演示 · 状态为虚构数据，连接操作已禁用。"
                else "门户、教务与学习通各自连接；验证或补连一项，不影响其他项。",
                fontSize = 13.sp,
                lineHeight = 20.sp,
                color = ds.sub,
                modifier = Modifier.padding(top = 4.dp),
            )
            Spacer(Modifier.height(12.dp))
            SchoolService.entries.forEachIndexed { index, service ->
                if (index > 0) Spacer(Modifier.height(14.dp))
                ServiceStatusRow(repo, service, state[service], onReconnect, actionColors, actionsEnabled)
            }
        }
    }
    Spacer(Modifier.height(AppUiTokens.CardGap))
}

@Composable
private fun ServiceStatusRow(
    repo: Repository,
    service: SchoolService,
    status: SchoolServiceStatus,
    onReconnect: (SchoolService) -> Unit,
    actionColors: TextButtonColors,
    actionsEnabled: Boolean,
) {
    val ds = LocalDs.current
    val scope = rememberCoroutineScope()
    // 本行自己的验证协程；仓库的 checking 是共享状态，本行 busy 由 finally 保证释放。
    // 不新增通用 catch：Repository 承诺吞掉业务异常，CancellationException 照常向上重抛。
    var localChecking by remember { mutableStateOf(false) }
    val checking = status.checking || localChecking
    val presentation = serviceRowPresentation(status.auth, checking, status.problem)
    val dotColor = when {
        presentation.isError -> ds.danger
        presentation.isGood -> ds.success
        else -> ds.sub
    }
    Column(
        Modifier.fillMaxWidth().semantics {
            contentDescription = "${service.displayName()}：${presentation.statusText}"
        },
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .padding(end = 10.dp)
                    .size(8.dp)
                    .background(dotColor, CircleShape),
            )
            Text(
                service.purposeLine(),
                fontSize = 15.sp,
                lineHeight = 22.sp,
                fontWeight = FontWeight.SemiBold,
                color = ds.text,
                modifier = Modifier.weight(1f),
            )
        }
        Text(
            presentation.statusText,
            fontSize = 13.sp,
            lineHeight = 20.sp,
            color = when {
                presentation.isError -> ds.danger
                presentation.isGood -> ds.text
                else -> ds.sub
            },
        )
        if (status.checkedAt > 0 && !checking) {
            Text(
                "上次验证 " + SimpleDateFormat("MM-dd HH:mm", Locale.CHINA).format(Date(status.checkedAt)),
                fontSize = 12.sp,
                lineHeight = 17.sp,
                color = ds.sub,
            )
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Spacer(Modifier.weight(1f))
            SettingActionText(
                text = presentation.actionLabel,
                enabled = presentation.actionEnabled && actionsEnabled,
                onClick = if (presentation.actionReconnects) {
                    { onReconnect(service) }
                } else {
                    {
                        scope.launch {
                            localChecking = true
                            try {
                                repo.refreshServiceConnection(service)
                            } finally {
                                localChecking = false
                            }
                        }
                    }
                },
                minHeight = 44.dp,
                colors = actionColors,
            )
        }
    }
}

internal data class ServiceRowPresentation(
    val statusText: String,
    val isError: Boolean = false,
    val isGood: Boolean = false,
    val actionLabel: String = "验证",
    val actionReconnects: Boolean = false,
    val actionEnabled: Boolean = true,
)

/** 认证失效优先于遗留问题展示：重连是唯一修复；未验证成功的问题一律不给绿色。 */
internal fun serviceRowPresentation(
    auth: ServiceAuthStatus,
    checking: Boolean,
    problem: ServiceProblem?,
): ServiceRowPresentation = when {
    checking -> ServiceRowPresentation(
        statusText = "正在验证…",
        actionLabel = "验证中",
        actionEnabled = false,
    )
    auth == ServiceAuthStatus.VERIFIED && problem != null -> ServiceRowPresentation(
        statusText = when (problem) {
            ServiceProblem.NETWORK -> "上次已验证，本次网络读取未完成。"
            ServiceProblem.READ -> "上次已验证，本次读取未完成。"
            ServiceProblem.UNCONFIRMED -> "上次已验证，本次验证未完成。"
        },
        actionLabel = "重试验证",
    )
    auth == ServiceAuthStatus.VERIFIED -> ServiceRowPresentation(
        statusText = "已连接",
        isGood = true,
        actionLabel = "重新检查",
    )
    auth == ServiceAuthStatus.AUTH_REQUIRED -> ServiceRowPresentation(
        statusText = "认证已失效，需要重新连接",
        isError = true,
        actionLabel = "重新连接",
        actionReconnects = true,
    )
    auth == ServiceAuthStatus.NOT_CONNECTED -> ServiceRowPresentation(
        statusText = when (problem) {
            null -> "未连接"
            else -> "未连接；最近一次尝试${problemPhrase(problem)}。"
        },
        actionLabel = "连接",
        actionReconnects = true,
    )
    else -> when (problem) { // SAVED：本地有候选，按最近一次问题证据描述
        ServiceProblem.NETWORK -> ServiceRowPresentation(
            statusText = "网络不通，暂时无法验证",
            actionLabel = "重试验证",
        )
        ServiceProblem.READ -> ServiceRowPresentation(
            statusText = "服务响应异常，暂时无法验证",
            actionLabel = "重试验证",
        )
        ServiceProblem.UNCONFIRMED -> ServiceRowPresentation(
            statusText = "暂时无法验证，可稍后重试",
            actionLabel = "重试验证",
        )
        null -> ServiceRowPresentation(
            statusText = "已保存连接，等待本次验证",
            actionLabel = "验证",
        )
    }
}

private fun problemPhrase(problem: ServiceProblem): String = when (problem) {
    ServiceProblem.NETWORK -> "网络不通"
    ServiceProblem.READ -> "服务响应异常"
    ServiceProblem.UNCONFIRMED -> "未能完成验证"
}

private fun SchoolService.displayName(): String = when (this) {
    SchoolService.PORTAL -> "门户"
    SchoolService.TEACHING -> "教务"
    SchoolService.CHX -> "学习通"
}

private fun SchoolService.purposeLine(): String = when (this) {
    SchoolService.PORTAL -> "门户 · 统一身份与学校通知"
    SchoolService.TEACHING -> "教务 · 课表、成绩与学期导入"
    SchoolService.CHX -> "学习通 · 课程作业与待办"
}
