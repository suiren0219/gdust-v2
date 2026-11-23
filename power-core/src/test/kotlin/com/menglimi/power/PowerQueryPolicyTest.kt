package com.menglimi.power

import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PowerQueryPolicyTest {
    private val zone = ZoneId.of("Asia/Shanghai")
    private val day = LocalDate.of(2026, 11, 15)
    private val start = LocalTime.of(7, 30)
    private val end = LocalTime.of(22, 5)
    private val hour = 3_600_000L
    private fun at(h: Int, m: Int = 0, days: Long = 0) = day.plusDays(days).atTime(h, m).atZone(zone).toInstant().toEpochMilli()
    private fun sample(h: Int, odd: Double, days: Long = 0) = PowerSample(at(h, days = days), odd)
    private fun plan(preset: PowerQueryPreset, samples: List<PowerSample> = emptyList(), now: Long = at(12)) =
        PowerQueryPolicy.plan(preset, 60, samples, now, lowThreshold = 20.0, dropRateThreshold = 5.0)
    private fun next(now: Long, last: Long = 0, attempt: Long = 0, interval: Int? = 120) =
        PowerQueryPolicy.nextSampleAt(now, last, attempt, interval, start, end, zone)

    @Test fun unknownPresetFallsBackToManualInsteadOfEnablingBackground() {
        assertEquals(PowerQueryPreset.MANUAL, PowerQueryPreset.fromKey(null))
        assertEquals(PowerQueryPreset.MANUAL, PowerQueryPreset.fromKey("unsupported"))
        assertEquals(PowerQueryPreset.CUSTOM, PowerQueryPreset.fromKey("custom"))
    }

    @Test fun enoughStableHistoryProducesTransparentDailyAverage() {
        val estimate = PowerQueryPolicy.estimate(listOf(sample(12, 120.0, -2), sample(12, 100.0, -1), sample(12, 80.0)), at(12))
        assertEquals(PowerEstimateState.AVAILABLE, estimate.state)
        assertEquals(20.0, estimate.dailyUse!!, 0.00001)
        assertEquals(4.0, estimate.daysLeft!!, 0.00001)
    }

    @Test fun rapidRefreshesCannotInventAnEstimateFromShortHistory() {
        val estimate = PowerQueryPolicy.estimate((0..12).map { PowerSample(at(12) - it * 60_000L, 20.0 + it) }, at(12))
        assertEquals(PowerEstimateState.INSUFFICIENT, estimate.state)
        assertNull(estimate.daysLeft)
    }

    @Test fun twoReadingsAreNotEnoughEvenWithDayLongCoverage() {
        assertEquals(PowerEstimateState.INSUFFICIENT,
            PowerQueryPolicy.estimate(listOf(sample(12, 50.0, -1), sample(12, 40.0)), at(12)).state)
    }

    @Test fun rechargeInvalidatesOldTrendUntilEnoughNewHistoryAccumulates() {
        val before = listOf(sample(12, 35.0, -2), sample(12, 25.0, -1))
        val estimate = PowerQueryPolicy.estimate(before + sample(12, 100.0), at(12))
        assertEquals(PowerEstimateState.RECHARGED, estimate.state)
        assertNull(estimate.daysLeft)
        val after = PowerQueryPolicy.estimate(before + listOf(sample(12, 100.0), sample(0, 90.0, 1), sample(12, 80.0, 1)), at(12, days = 1))
        assertEquals(PowerEstimateState.AVAILABLE, after.state)
        assertEquals(20.0, after.dailyUse!!, 0.00001)
        assertEquals(4.0, after.daysLeft!!, 0.00001)
    }

    @Test fun flatBalancesDoNotClaimInfiniteDays() {
        val estimate = PowerQueryPolicy.estimate(listOf(sample(12, 40.0, -2), sample(12, 40.0, -1), sample(12, 40.0)), at(12))
        assertEquals(PowerEstimateState.NO_USAGE, estimate.state)
        assertNull(estimate.daysLeft)
    }

    @Test fun oldCurrentSampleHasExplicitUnknownState() {
        val estimate = PowerQueryPolicy.estimate(listOf(sample(10, 40.0, -3), sample(10, 35.0, -2), sample(10, 30.0, -1)), at(12))
        assertEquals(PowerEstimateState.STALE, estimate.state)
        assertNull(estimate.daysLeft)
    }

    @Test fun emptyNonfiniteAndDepletedSamplesDoNotYieldPrediction() {
        assertEquals(PowerEstimateState.INSUFFICIENT, PowerQueryPolicy.estimate(emptyList(), at(12)).state)
        assertEquals(PowerEstimateState.INVALID, PowerQueryPolicy.estimate(listOf(sample(12, Double.NaN)), at(12)).state)
        assertEquals(PowerEstimateState.INVALID, PowerQueryPolicy.estimate(listOf(sample(12, Double.POSITIVE_INFINITY)), at(12)).state)
        for (balance in listOf(0.0, -1.0)) {
            assertEquals(PowerEstimateState.NO_BALANCE, PowerQueryPolicy.estimate(listOf(sample(12, balance)), at(12)).state)
        }
    }

    @Test fun longMissingPeriodRestartsTrendRatherThanAveragingAcrossGap() {
        val estimate = PowerQueryPolicy.estimate(listOf(sample(12, 100.0, -6), sample(12, 90.0, -5), sample(11, 60.0), sample(12, 59.0)), at(12))
        assertEquals(PowerEstimateState.INSUFFICIENT, estimate.state)
    }

    @Test fun futurePointsDoNotAlterCurrentEstimate() {
        val stable = listOf(sample(12, 120.0, -2), sample(12, 100.0, -1), sample(12, 80.0))
        assertEquals(PowerQueryPolicy.estimate(stable, at(12)),
            PowerQueryPolicy.estimate((stable + sample(13, 999.0)).reversed(), at(12)))
    }

    @Test fun manualPresetNeverSchedulesEvenWithLowBalance() {
        val result = plan(PowerQueryPreset.MANUAL, listOf(sample(12, 1.0)))
        assertNull(result.intervalMinutes)
        assertNull(next(at(12), interval = null))
        assertFalse(PowerQueryPolicy.queryDue(at(12), 0, 0, null))
    }

    @Test fun balanceAndAttentionPresetsHaveDifferentNormalAndLowCadences() {
        val normal = listOf(sample(12, 100.0))
        val low = listOf(sample(12, 20.0))
        assertEquals(120, plan(PowerQueryPreset.BALANCED, normal).intervalMinutes)
        assertEquals(60, plan(PowerQueryPreset.BALANCED, low).intervalMinutes)
        assertEquals(60, plan(PowerQueryPreset.LOW_POWER, normal).intervalMinutes)
        assertEquals(30, plan(PowerQueryPreset.LOW_POWER, low).intervalMinutes)
        assertTrue(plan(PowerQueryPreset.LOW_POWER, low).reason.contains("阈值"))
    }

    @Test fun supportedTrendCanIncreaseAttentionAboveLowThreshold() {
        val samples = listOf(sample(12, 80.0, -1), sample(0, 60.0), sample(12, 40.0))
        val result = plan(PowerQueryPreset.BALANCED, samples)
        assertEquals(60, result.intervalMinutes)
        assertTrue(result.reason.contains("粗估"))
    }

    @Test fun fastRecentDropCanIncreaseAttentionWithoutFullDayHistory() {
        val result = plan(PowerQueryPreset.LOW_POWER, listOf(sample(11, 100.0), sample(12, 90.0)))
        assertEquals(30, result.intervalMinutes)
        assertTrue(result.reason.contains("掉电"))
    }

    @Test fun unavailableTrendDoesNotInventUrgency() {
        assertEquals(120, plan(PowerQueryPreset.BALANCED).intervalMinutes)
        val recharged = listOf(sample(12, 30.0, -1), sample(11, 25.0), sample(12, 100.0))
        assertEquals(120, plan(PowerQueryPreset.BALANCED, recharged).intervalMinutes)
    }

    @Test fun customIntervalKeepsUserChoiceAndClampsInvalidValues() {
        fun custom(interval: Int) = PowerQueryPolicy.plan(PowerQueryPreset.CUSTOM, interval, listOf(sample(12, 1.0)), at(12), 20.0, 5.0)
        assertEquals(120, custom(120).intervalMinutes)
        assertEquals(15, custom(0).intervalMinutes)
        assertEquals(120, custom(999).intervalMinutes)
    }

    @Test fun queryIsDueAtBoundaryAndFailedAttemptAlsoThrottles() {
        assertFalse(PowerQueryPolicy.queryDue(at(12), at(11, 1), 0, 60))
        assertTrue(PowerQueryPolicy.queryDue(at(12), at(11), 0, 60))
        assertFalse(PowerQueryPolicy.queryDue(at(12), at(9), at(11, 30), 60))
        assertTrue(PowerQueryPolicy.queryDue(at(12), 0, 0, 60))
    }

    @Test fun restartKeepsOriginalDueTimeInsteadOfDelayingAgain() {
        val first = next(at(10, 15), last = at(10))
        assertEquals(at(12), first)
        assertEquals(first, next(at(11, 45), last = at(10)))
        assertEquals(at(13), next(at(13), last = at(10)))
    }

    @Test fun failedNetworkAttemptAnchorsNextQuery() {
        assertEquals(at(14), next(at(12, 15), last = at(8), attempt = at(12)))
    }

    @Test fun intervalCrossingWindowEndWaitsUntilNextMorning() {
        assertEquals(at(7, 30, 1), next(at(21), last = at(21)))
        assertEquals(at(7, 30, 1), next(at(22, 5), last = at(20)))
        assertEquals(at(7, 30), next(at(1), last = at(21, days = -1)))
        assertEquals(at(7, 30), next(at(7), last = at(21, days = -1)))
    }

    @Test fun executionDelayedBySystemMustStillRespectWindow() {
        assertFalse(PowerQueryPolicy.inWindow(at(7, 29), start, end, zone))
        assertTrue(PowerQueryPolicy.inWindow(at(7, 30), start, end, zone))
        assertTrue(PowerQueryPolicy.inWindow(at(22, 4), start, end, zone))
        assertFalse(PowerQueryPolicy.inWindow(at(22, 5), start, end, zone))
    }

    @Test fun incompleteBindingAndManualModeCancelChainAndHeartbeat() {
        assertEquals(PowerScheduleAction.CANCEL, PowerQueryPolicy.scheduleAction(false, PowerQueryPreset.BALANCED, false))
        assertEquals(PowerScheduleAction.CANCEL, PowerQueryPolicy.scheduleAction(false, PowerQueryPreset.LOW_POWER, true))
        assertEquals(PowerScheduleAction.CANCEL, PowerQueryPolicy.scheduleAction(true, PowerQueryPreset.MANUAL, true))
    }

    @Test fun identicalConfigurationKeepsExistingWorkAndChangesReplaceIt() {
        assertEquals(PowerScheduleAction.KEEP, PowerQueryPolicy.scheduleAction(true, PowerQueryPreset.BALANCED, false))
        assertEquals(PowerScheduleAction.REPLACE, PowerQueryPolicy.scheduleAction(true, PowerQueryPreset.LOW_POWER, true))
    }

    @Test fun futureClockAnchorsCannotStarveQueries() {
        assertTrue(PowerQueryPolicy.queryDue(at(12), at(13), at(13), 60))
        assertEquals(at(14), next(at(12), last = at(13), attempt = at(13)))
    }

    @Test fun historyDeduplicatesOnlyCurrentQuarterHourAndKeepsOlderSamples() {
        val old = PowerSample(at(12, days = -31), 200.0)
        val earlier = PowerSample(at(11, 45), 52.0)
        val currentBucket = PowerSample(at(12, 1), 51.0)
        val latest = PowerSample(at(12, 14), 50.0)
        val result = PowerQueryPolicy.appendSample(listOf(old, earlier, currentBucket), latest)
        assertEquals(listOf(old, earlier, latest), result)
        val nextBucket = PowerSample(at(12, 15), 49.0)
        assertEquals(listOf(old, earlier, latest, nextBucket), PowerQueryPolicy.appendSample(result, nextBucket))
    }

    @Test fun clockRollbackPreservesOtherBucketsAndNewerSampleInCurrentBucket() {
        val latestInBucket = PowerSample(at(12, 14), 50.0)
        val laterBucket = PowerSample(at(12, 15), 49.0)
        val samples = listOf(latestInBucket, laterBucket)
        assertEquals(samples, PowerQueryPolicy.appendSample(samples, PowerSample(at(12, 1), 51.0)))
        val earlier = PowerSample(at(11, 45), 52.0)
        assertEquals(listOf(earlier, latestInBucket, laterBucket), PowerQueryPolicy.appendSample(samples, earlier))
    }

    @Test fun invalidReadingCannotReplaceUsefulHistoryButZeroIsPreserved() {
        val history = listOf(sample(12, 1.0))
        assertEquals(history, PowerQueryPolicy.appendSample(history, PowerSample(at(12, 1), Double.NaN)))
        assertEquals(listOf(PowerSample(at(12, 1), 0.0)), PowerQueryPolicy.appendSample(history, PowerSample(at(12, 1), 0.0)))
    }

    @Test fun dropRateIgnoresRechargeAndShortRefreshIntervals() {
        val previous = sample(11, 100.0)
        assertEquals(10.0, PowerQueryPolicy.dropRate(previous, sample(12, 90.0))!!, 0.00001)
        assertNull(PowerQueryPolicy.dropRate(previous, sample(12, 150.0)))
        assertNull(PowerQueryPolicy.dropRate(previous, PowerSample(previous.time + 60_000, 99.0)))
        assertNull(PowerQueryPolicy.dropRate(null, sample(12, 90.0)))
        assertNull(PowerQueryPolicy.dropRate(previous, sample(12, Double.NaN)))
    }
}
