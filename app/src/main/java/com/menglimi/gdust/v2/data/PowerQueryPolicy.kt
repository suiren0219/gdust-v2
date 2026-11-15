package com.menglimi.gdust.v2.data

import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId

/** 自定间隔用于保留升级前的选择；新的默认值是均衡。 */
enum class PowerQueryPreset(val key: String, val title: String, val description: String) {
    MANUAL("manual", "手动", "仅手动查询，不运行后台采样；查询时仍检查低电量和掉电预警。"),
    BALANCED("balanced", "均衡", "通常每 120 分钟查询；电量偏低、估算不足 2 天或近期掉电较快时改为 60 分钟。"),
    LOW_POWER("low_power", "低电量关注", "通常每 60 分钟查询；电量偏低、估算不足 2 天或近期掉电较快时改为 30 分钟。"),
    CUSTOM("custom", "自定间隔", "按所选间隔查询，保留低电量和掉电预警。"),
    ;

    companion object {
        fun fromKey(key: String?): PowerQueryPreset = entries.firstOrNull { it.key == key } ?: BALANCED
    }
}

/** 与 Android 存储无关的读数，供趋势和调度共用。 */
data class PowerSample(val time: Long, val odd: Double)

enum class PowerEstimateState(val explanation: String) {
    AVAILABLE("按近期本机样本平均用量粗估，实际用电和充值会改变结果。"),
    INSUFFICIENT("至少需要 3 个有效样本，覆盖 24 小时后再估算。"),
    RECHARGED("检测到余额上升，可能充值或读数修正；重新累计满 24 小时后再估算。"),
    STALE("最近样本已超过 24 小时，请刷新后再查看估算。"),
    NO_USAGE("这段时间未记录到足够的用电变化，暂不估算可用天数。"),
    NO_BALANCE("剩余电量不大于 0，请查询或充值后再查看估算。"),
    INVALID("当前读数无效，暂不估算。"),
}

data class PowerUsageEstimate(
    val state: PowerEstimateState,
    val dailyUse: Double? = null,
    val daysLeft: Double? = null,
)

data class PowerQueryPlan(val intervalMinutes: Int?, val reason: String)

enum class PowerScheduleAction { CANCEL, KEEP, REPLACE }

object PowerQueryPolicy {
    private const val MINUTE = 60_000L
    private const val HOUR = 60 * MINUTE
    private const val DAY = 24 * HOUR

    /**
     * 只看最近 7 天、最近一次明显余额上升之后的连续记录。
     * 不把充值前后的差值当耗电，也不拿几分钟内的小差值外推整天。
     */
    fun estimate(samples: List<PowerSample>, now: Long): PowerUsageEstimate {
        val recent = samples.filter { it.time > 0 && it.time <= now && it.time >= now - 7 * DAY }
            .sortedBy { it.time }.distinctBy { it.time }
        val last = recent.lastOrNull() ?: return PowerUsageEstimate(PowerEstimateState.INSUFFICIENT)
        if (!last.odd.isFinite()) return PowerUsageEstimate(PowerEstimateState.INVALID)
        if (last.odd <= 0) return PowerUsageEstimate(PowerEstimateState.NO_BALANCE)
        if (now - last.time > DAY) return PowerUsageEstimate(PowerEstimateState.STALE)
        val valid = recent.filter { it.odd.isFinite() && it.odd >= 0 }
        var start = 0
        var recharged = false
        for (i in 1 until valid.size) {
            if (valid[i].odd - valid[i - 1].odd > 0.1 + 1e-9) {
                start = i
                recharged = true
            } else if (valid[i].time - valid[i - 1].time > 2 * DAY) {
                start = i
                recharged = false
            }
        }
        val segment = valid.drop(start)
        val hours = (last.time - segment.first().time) / HOUR.toDouble()
        if (segment.size < 3 || hours < 24) {
            return PowerUsageEstimate(if (recharged) PowerEstimateState.RECHARGED else PowerEstimateState.INSUFFICIENT)
        }
        val daily = (segment.first().odd - last.odd) / hours * 24
        if (!daily.isFinite() || daily <= 0.01) return PowerUsageEstimate(PowerEstimateState.NO_USAGE)
        return PowerUsageEstimate(PowerEstimateState.AVAILABLE, dailyUse = daily, daysLeft = last.odd / daily)
    }

    /** 手动连续点刷新不放大小误差；充值/读数上升不产生掉电预警。 */
    fun dropRate(previous: PowerSample?, current: PowerSample): Double? {
        if (previous == null || !previous.odd.isFinite() || !current.odd.isFinite()) return null
        val duration = current.time - previous.time
        if (duration < 15 * MINUTE || current.odd >= previous.odd) return null
        return ((previous.odd - current.odd) / (duration / HOUR.toDouble())).takeIf { it.isFinite() }
    }

    fun plan(
        preset: PowerQueryPreset,
        customIntervalMinutes: Int,
        samples: List<PowerSample>,
        now: Long,
        lowThreshold: Double,
        dropRateThreshold: Double,
    ): PowerQueryPlan {
        if (preset == PowerQueryPreset.MANUAL) return PowerQueryPlan(null, "已关闭后台查询")
        if (preset == PowerQueryPreset.CUSTOM) return PowerQueryPlan(customIntervalMinutes.coerceIn(15, 120), "使用自定间隔")
        val valid = samples.filter { it.time in 1..now && it.odd.isFinite() }.sortedBy { it.time }
        val latest = valid.lastOrNull()
        val threshold = lowThreshold.takeIf { it.isFinite() && it >= 0 } ?: 20.0
        val dropLimit = dropRateThreshold.takeIf { it.isFinite() && it > 0 } ?: 5.0
        val estimate = estimate(samples, now)
        val reason = when {
            latest != null && latest.odd <= threshold -> "剩余电量已到预警阈值"
            estimate.daysLeft?.let { it <= 2 } == true -> "按近期用量粗估不足 2 天"
            latest != null && now - latest.time <= DAY &&
                dropRate(valid.getOrNull(valid.lastIndex - 1), latest)?.let { it >= dropLimit } == true -> "近期掉电较快"
            else -> null
        }
        val interval = when (preset) {
            PowerQueryPreset.LOW_POWER -> if (reason != null) 30 else 60
            else -> if (reason != null) 60 else 120
        }
        return PowerQueryPlan(interval, reason ?: "按常规节奏查询")
    }

    /** 配置不变只补排，不能每次开 App 都推迟已有任务。 */
    fun scheduleAction(hasBinding: Boolean, preset: PowerQueryPreset, configurationChanged: Boolean): PowerScheduleAction = when {
        !hasBinding || preset == PowerQueryPreset.MANUAL -> PowerScheduleAction.CANCEL
        configurationChanged -> PowerScheduleAction.REPLACE
        else -> PowerScheduleAction.KEEP
    }

    fun inWindow(now: Long, start: LocalTime, end: LocalTime, zone: ZoneId): Boolean {
        val time = Instant.ofEpochMilli(now).atZone(zone).toLocalTime()
        return !time.isBefore(start) && time.isBefore(end)
    }

    /** 以最近成功或失败的请求为锚点；重启不会把下一次查询继续向后推。 */
    fun nextSampleAt(
        now: Long,
        lastSampleAt: Long,
        lastAttemptAt: Long,
        intervalMinutes: Int?,
        start: LocalTime,
        end: LocalTime,
        zone: ZoneId,
    ): Long? {
        if (intervalMinutes == null) return null
        val anchor = listOf(lastSampleAt, lastAttemptAt).filter { it in 1..now }.maxOrNull() ?: now
        val due = maxOf(now, anchor + intervalMinutes.coerceAtLeast(15) * MINUTE)
        val local = Instant.ofEpochMilli(due).atZone(zone)
        val scheduled = when {
            local.toLocalTime().isBefore(start) -> local.toLocalDate().atTime(start).atZone(zone)
            !local.toLocalTime().isBefore(end) -> local.toLocalDate().plusDays(1).atTime(start).atZone(zone)
            else -> local
        }
        return scheduled.toInstant().toEpochMilli()
    }

    fun queryDue(now: Long, lastSampleAt: Long, lastAttemptAt: Long, intervalMinutes: Int?): Boolean {
        if (intervalMinutes == null) return false
        val anchor = listOf(lastSampleAt, lastAttemptAt).filter { it in 1..now }.maxOrNull() ?: return true
        return now - anchor >= intervalMinutes.coerceAtLeast(15) * MINUTE
    }

    /** 同一 15 分钟桶保留最后读数；自动与手动共享历史，不重复堆积同一时段。 */
    fun appendSample(samples: List<PowerSample>, sample: PowerSample): List<PowerSample> {
        if (!sample.odd.isFinite() || sample.time <= 0) return samples
        val bucket = sample.time / (15 * MINUTE)
        return (samples.filter {
            it.time >= sample.time - 30 * DAY && it.time <= sample.time &&
                it.time / (15 * MINUTE) != bucket && it.odd.isFinite()
        } + sample).sortedBy { it.time }
    }
}
