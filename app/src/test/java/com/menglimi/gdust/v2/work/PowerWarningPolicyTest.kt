package com.menglimi.gdust.v2.work

import org.junit.Assert.assertEquals
import org.junit.Test

class PowerWarningPolicyTest {
    @Test fun lowPowerRemainsHigherPriorityThanDropRate() {
        val result = PowerWarningPolicy.evaluate(
            odd = 18.0, previousOdd = 30.0, elapsedMs = 3_600_000,
            lowThreshold = 20.0, dropLimitPerHour = 5.0,
            lowCooldownReady = true, dropCooldownReady = true, hasPreviousSample = true,
        )
        assertEquals(PowerWarningKind.LOW, result.kind)
    }

    @Test fun dropRateUsesSameMinimumWindowAsPreviousWorker() {
        val result = PowerWarningPolicy.evaluate(
            odd = 28.0, previousOdd = 30.0, elapsedMs = 5 * 60_000,
            lowThreshold = 20.0, dropLimitPerHour = 5.0,
            lowCooldownReady = true, dropCooldownReady = true, hasPreviousSample = true,
        )
        assertEquals(PowerWarningKind.DROP, result.kind)
        assertEquals(8.0, result.dropRatePerHour, 0.001)
    }

    @Test fun missingPreviousSampleOrCooldownSuppressesDropWarning() {
        val noPrevious = PowerWarningPolicy.evaluate(
            odd = 20.0, previousOdd = -1.0, elapsedMs = 3_600_000,
            lowThreshold = 10.0, dropLimitPerHour = 1.0,
            lowCooldownReady = false, dropCooldownReady = true, hasPreviousSample = false,
        )
        assertEquals(PowerWarningKind.NONE, noPrevious.kind)
        val cooledDown = PowerWarningPolicy.evaluate(
            odd = 10.0, previousOdd = 30.0, elapsedMs = 3_600_000,
            lowThreshold = 5.0, dropLimitPerHour = 1.0,
            lowCooldownReady = false, dropCooldownReady = false, hasPreviousSample = true,
        )
        assertEquals(PowerWarningKind.NONE, cooledDown.kind)
    }
}
