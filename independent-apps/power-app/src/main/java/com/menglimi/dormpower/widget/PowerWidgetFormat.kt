package com.menglimi.dormpower.widget

import android.content.Context
import com.menglimi.dormpower.power.powerValue
import java.util.Locale

/** 小部件只处理展示文案；请求与调度仍由 PowerMonitor/Worker 负责。 */

internal fun widgetBalanceText(odd: Double?): String =
    if (odd?.takeIf { it.isFinite() } == null) "—" else powerValue(odd)

internal fun widgetAgeText(updatedAt: Long, now: Long): String = when {
    updatedAt <= 0 -> "暂无更新"
    updatedAt > now -> "时间待确认"
    now - updatedAt < 60_000L -> "刚刚更新"
    now - updatedAt < 3_600_000L -> "${(now - updatedAt) / 60_000L} 分钟前更新"
    now - updatedAt < 86_400_000L -> "${(now - updatedAt) / 3_600_000L} 小时前更新"
    else -> "${(now - updatedAt) / 86_400_000L} 天前更新"
}

internal fun widgetStatusText(odd: Double?, lowThreshold: Double, stale: Boolean): String = when {
    odd?.takeIf { it.isFinite() } == null -> "暂无读数"
    odd < 0 -> "负余额"
    odd == 0.0 -> "电量为零"
    stale -> "待更新"
    odd <= lowThreshold -> "低电量"
    else -> "电量正常"
}

internal fun widgetStale(updatedAt: Long, now: Long, intervalMinutes: Int?): Boolean {
    if (updatedAt <= 0 || updatedAt > now) return true
    val maxAge = intervalMinutes?.times(60_000L) ?: 86_400_000L
    return now - updatedAt >= maxAge
}

internal fun widgetUsageText(value: Double?): String =
    value?.takeIf { it.isFinite() && it >= 0 }?.let { String.format(Locale.CHINA, "%.2f", it) } ?: "—"
