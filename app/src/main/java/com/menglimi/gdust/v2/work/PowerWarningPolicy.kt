package com.menglimi.gdust.v2.work

internal enum class PowerWarningKind { NONE, LOW, DROP }

internal data class PowerWarningDecision(
    val kind: PowerWarningKind,
    val dropRatePerHour: Double = 0.0,
)

/** 保持原告警优先级：低电量优先于掉电速率；速率按至少 15 分钟窗口折算。 */
internal object PowerWarningPolicy {
    fun evaluate(
        odd: Double,
        previousOdd: Double,
        elapsedMs: Long,
        lowThreshold: Double,
        dropLimitPerHour: Double,
        lowCooldownReady: Boolean,
        dropCooldownReady: Boolean,
        hasPreviousSample: Boolean,
    ): PowerWarningDecision {
        if (lowCooldownReady && odd <= lowThreshold) return PowerWarningDecision(PowerWarningKind.LOW)
        if (!dropCooldownReady || previousOdd < 0.0 || !hasPreviousSample) {
            return PowerWarningDecision(PowerWarningKind.NONE)
        }
        val hours = (elapsedMs / 3_600_000.0).coerceAtLeast(0.25)
        val rate = (previousOdd - odd) / hours
        return if (rate >= dropLimitPerHour) PowerWarningDecision(PowerWarningKind.DROP, rate)
        else PowerWarningDecision(PowerWarningKind.NONE, rate)
    }
}
