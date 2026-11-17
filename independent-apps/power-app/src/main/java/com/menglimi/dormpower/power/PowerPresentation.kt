package com.menglimi.dormpower.power

import com.menglimi.power.PowerQueryPolicy
import com.menglimi.power.PowerSample
import java.util.Locale

internal enum class ReadingStatus(val title: String) {
    UNBOUND("未绑定"), UNKNOWN("暂无读数"), ERROR("刷新失败"), STALE("数据已过期"), LOW("低电量"), READY("电量正常"),
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

internal fun powerValue(value: Double?): String = value?.takeIf { it.isFinite() }
    ?.let { String.format(Locale.CHINA, "%.2f", it) } ?: "—"

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
