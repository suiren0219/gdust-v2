package com.menglimi.gdust.v2.ui.screens

/** 只解析 Repository 已有的固定结果文案，不推算未知数量，也不改变同步数据。 */
internal enum class ChxSyncOutcome { COMPLETE, PARTIAL, FAILED, UNCONFIRMED }

internal data class ChxWorkSyncSummary(
    val outcome: ChxSyncOutcome,
    val successfulCourses: Int?,
    val failedCourses: Int?,
    val changeSummary: String?,
    val authRequired: Boolean,
) {
    val title: String get() = when (outcome) {
        ChxSyncOutcome.COMPLETE -> "同步完成"
        ChxSyncOutcome.PARTIAL -> "部分完成"
        ChxSyncOutcome.FAILED -> "同步未完成"
        ChxSyncOutcome.UNCONFIRMED -> "同步结果待确认"
    }
    val needsAttention: Boolean get() = outcome != ChxSyncOutcome.COMPLETE
}

internal fun chxWorkSyncSummary(message: String, failed: Boolean = false): ChxWorkSyncSummary {
    val firstLine = message.lineSequence().firstOrNull().orEmpty()
    val successful = Regex("(?:检查了|已检查)\\s+(\\d+)\\s+门课").find(firstLine)
        ?.groupValues?.get(1)?.toIntOrNull()
    val failures = Regex("(\\d+)\\s+门(?:读取失败|课程未能读取)").find(firstLine)
        ?.groupValues?.get(1)?.toIntOrNull()
    val emptyCourses = message == "已读取课程列表，当前没有课程；本地待办已保留"
    val noWork = Regex("已检查 \\d+ 门课程，未发现作业；本地待办已保留").matches(message)
    val allFailed = firstLine.contains("门课程未能读取")
    val authRequired = message.contains("要求重新登录学习通") || message.contains("学习通会话已失效")
    // 部分失败是一次正常返回，不能以没有抛异常作为成功证据。
    val outcome = when {
        failed || allFailed -> ChxSyncOutcome.FAILED
        firstLine.startsWith("部分完成") || (failures ?: 0) > 0 -> ChxSyncOutcome.PARTIAL
        authRequired || firstLine.startsWith("作业同步未完成") -> ChxSyncOutcome.FAILED
        emptyCourses || noWork -> ChxSyncOutcome.COMPLETE
        firstLine.startsWith("同步完成：") && successful != null && !firstLine.contains("失败") -> ChxSyncOutcome.COMPLETE
        else -> ChxSyncOutcome.UNCONFIRMED
    }
    val changes = Regex("新增 (\\d+) 条、更新 (\\d+) 条(?:、合并 (\\d+) 条通知重复)?，未交 (\\d+) 条$")
        .find(message)
    val changeSummary = when {
        emptyCourses -> "当前课程页为 0 门"
        noWork -> "未发现作业"
        changes != null -> buildString {
            append("新增 ${changes.groupValues[1]} · 更新 ${changes.groupValues[2]}")
            changes.groupValues[3].takeIf { it.isNotEmpty() }?.let { append(" · 合并 $it") }
            append(" · 未交 ${changes.groupValues[4]}")
        }
        else -> null
    }
    return ChxWorkSyncSummary(
        outcome = outcome,
        successfulCourses = successful ?: if (emptyCourses || allFailed) 0 else null,
        failedCourses = failures ?: if (outcome == ChxSyncOutcome.COMPLETE) 0 else null,
        changeSummary = changeSummary,
        authRequired = authRequired,
    )
}

internal data class ChxConnectionPresentation(val title: String, val verified: Boolean)

/** 首页证据、当前候选会话和后续认证失败必须一起判断；网络未知保持待验证。 */
internal fun chxConnectionPresentation(
    saved: Boolean,
    homeVerified: Boolean?,
    checking: Boolean = false,
    authRequired: Boolean = false,
): ChxConnectionPresentation = when {
    checking -> ChxConnectionPresentation("正在验证", false)
    authRequired || homeVerified == false -> ChxConnectionPresentation("需要重新连接", false)
    saved && homeVerified == true -> ChxConnectionPresentation("已连接", true)
    saved -> ChxConnectionPresentation("待验证", false)
    else -> ChxConnectionPresentation("未连接", false)
}
