package com.menglimi.dormpower.power

import com.menglimi.power.PowerEstimateState
import com.menglimi.power.PowerQueryPolicy
import com.menglimi.power.PowerUsageEstimate
import com.menglimi.power.PowerSample
import java.util.Locale

internal enum class ReadingStatus(val title: String) {
    UNBOUND("未绑定"), UNKNOWN("暂无读数"), ERROR("刷新失败"), STALE("待更新"), LOW("低电量"), READY("电量正常"),
}

/** 缓存超过当前查询节奏即标过期；手动模式按 24 小时，设备时间回退也不冒充新数据。 */
internal fun readingStatus(state: PowerUiState, now: Long): ReadingStatus {
    if (state.config.binding?.isValid != true) return ReadingStatus.UNBOUND
    if (state.error != null) return ReadingStatus.ERROR
    val reading = state.reading ?: return ReadingStatus.UNKNOWN
    val odd = reading.detail.odd?.takeIf { it.isFinite() } ?: return ReadingStatus.UNKNOWN
    if (reading.updatedAt <= 0) return ReadingStatus.UNKNOWN
    val interval = PowerQueryPolicy.plan(state.config.preset, state.config.customIntervalMinutes, state.samples,
        now, state.config.effectiveLowThreshold, state.config.effectiveDropRateThreshold).intervalMinutes
    val maxAge = interval?.times(60_000L) ?: 86_400_000L
    if (reading.updatedAt > now || now - reading.updatedAt >= maxAge ||
        (!state.busy && state.lastAttemptAt > state.lastSampleAt && now - state.lastAttemptAt > 120_000L)) {
        return ReadingStatus.STALE
    }
    return if (odd <= state.config.effectiveLowThreshold) ReadingStatus.LOW else ReadingStatus.READY
}

internal data class PowerReadingPresentation(
    val tags: List<String>,
    val warning: Boolean,
    val notice: String?,
    val cached: Boolean,
)

/** 电量与新鲜度分别表达；缓存旧或请求失败都不能冒充低电量。 */
internal fun readingPresentation(state: PowerUiState, now: Long): PowerReadingPresentation {
    val status = readingStatus(state, now)
    if (status == ReadingStatus.UNBOUND) return PowerReadingPresentation(listOf(status.title), false, null, false)
    val odd = state.reading?.takeIf { it.updatedAt > 0 }?.detail?.odd?.takeIf { it.isFinite() }
    val warning = odd != null && odd <= state.config.effectiveLowThreshold
    val cached = status == ReadingStatus.STALE || status == ReadingStatus.ERROR
    val balanceTag = when {
        odd == null -> "暂无读数"
        odd < 0 -> "负余额"
        odd == 0.0 -> "电量为零"
        warning -> "低电量"
        cached -> "上次电量充足"
        else -> "电量充足"
    }
    val freshness = when {
        state.busy -> "更新中"
        status == ReadingStatus.ERROR -> "刷新失败"
        status == ReadingStatus.STALE -> "待更新"
        else -> null
    }
    val notice = when {
        odd == null -> if (state.busy) "正在获取首次读数…" else "尚无有效电量，请刷新获取。"
        state.busy -> "正在获取最新电量，先显示上次读数。"
        status == ReadingStatus.ERROR -> "本次刷新未成功，保留上次读数。"
        state.reading?.updatedAt?.let { it > now } == true -> "设备时间早于数据时间，请检查系统时间后刷新。"
        status == ReadingStatus.STALE -> "已到更新时点，当前显示上次读数。"
        odd < 0 -> "服务端返回负余额，请确认充值情况及当前供电状态。"
        odd == 0.0 -> "剩余电量为零，请及时充值并确认供电状态。"
        warning -> "已达到 ${powerValue(state.config.effectiveLowThreshold)} 度预警线，请留意充值。"
        else -> null
    }
    return PowerReadingPresentation(listOfNotNull(balanceTag, freshness), warning, notice, cached)
}

internal fun powerValue(value: Double?): String = value?.takeIf { it.isFinite() }
    ?.let { String.format(Locale.CHINA, "%.2f", if (it == 0.0) 0.0 else it) } ?: "—"

internal fun usageValue(value: Double?): String = powerValue(value?.takeIf { it >= 0 })

internal fun estimateTitle(estimate: PowerUsageEstimate): String = when {
    estimate.daysLeft?.let { it.isFinite() && it > 0 && it < 1 } == true -> "不足 1 天"
    estimate.daysLeft?.let { it.isFinite() && it >= 1 } == true -> "约 ${powerValue(estimate.daysLeft)} 天"
    estimate.state == PowerEstimateState.NO_BALANCE -> "暂无可用电量"
    else -> "暂无法估算"
}

internal fun schoolCode(text: String): Int? = text.trim()
    .takeIf { it.isNotEmpty() && it.all { char -> char in '0'..'9' } }
    ?.toIntOrNull()?.takeIf { it > 0 }

internal fun trendSamples(samples: List<PowerSample>, days: Int, now: Long): List<PowerSample> = samples
    .filter { it.time > 0 && it.time in (now - days.coerceIn(1, 30) * 86_400_000L)..now && it.odd.isFinite() }
    .sortedBy { it.time }.distinctBy { it.time }

internal data class TrendAxis(val min: Double, val max: Double) {
    val ticks: List<Double> get() = listOf(max, (max + min) / 2, min)
}

internal fun trendAxis(points: List<PowerSample>): TrendAxis {
    if (points.isEmpty()) return TrendAxis(0.0, 1.0)
    val low = points.minOf { it.odd }
    val high = points.maxOf { it.odd }
    return if (high == low) TrendAxis(low - 0.5, high + 0.5) else TrendAxis(low, high)
}

/** 一次采样不能计算日内变化；正值明确包含充值/修正，不称作耗电。 */
internal fun balanceChange(samples: List<PowerSample>): String {
    val valid = samples.filter { it.time > 0 && it.odd.isFinite() }.sortedBy { it.time }.distinctBy { it.time }
    if (valid.size < 2) return "—（仅一次采样）"
    val change = valid.last().odd - valid.first().odd
    return "${if (change > 0) "+" else ""}${powerValue(change)} 度${if (change > 0) " · 可能充值或读数修正" else ""}"
}
