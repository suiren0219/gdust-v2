package com.menglimi.gdust.v2.data

import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ScheduleSyncPolicyTest {
    private val now = 1_800_000_000_000L
    private val hour = 3_600_000L
    private val minute = 60_000L

    @Test fun intervalIsFourHoursAndFailureBackoffIsFifteenMinutes() {
        assertEquals(4 * hour, ScheduleSyncPolicy.INTERVAL_MILLIS)
        assertEquals(15 * minute, ScheduleSyncPolicy.RETRY_MILLIS)
    }

    @Test fun noSuccessfulSyncIsDueImmediately() {
        assertEquals(now, ScheduleSyncPolicy.nextAt(now, 0L, 0L))
    }

    @Test fun successAnchorsFourHourDueTime() {
        assertEquals(now + hour, ScheduleSyncPolicy.nextAt(now, now - 3 * hour, now - 3 * hour))
        assertEquals(now, ScheduleSyncPolicy.nextAt(now, now - 4 * hour, 0L))
        assertEquals(now, ScheduleSyncPolicy.nextAt(now, now - 5 * hour, 0L))
    }

    @Test fun reopeningDoesNotPostponeAnExistingDueTime() {
        val success = now - hour
        val due = ScheduleSyncPolicy.nextAt(now, success, 0L)
        assertEquals(now + 3 * hour, due)
        assertEquals(due, ScheduleSyncPolicy.nextAt(now + 2 * hour, success, 0L))
        assertEquals(due, ScheduleSyncPolicy.nextAt(due, success, 0L))
    }

    @Test fun recentFailureDelaysOnlyUntilRetryBoundary() {
        val attempt = now - 5 * minute
        assertEquals(now + 10 * minute, ScheduleSyncPolicy.nextAt(now, now - 5 * hour, attempt))
        assertEquals(now + 10 * minute, ScheduleSyncPolicy.nextAt(now + minute, now - 5 * hour, attempt))
        assertEquals(now, ScheduleSyncPolicy.nextAt(now, now - 5 * hour, now - 15 * minute))
    }

    @Test fun failureBeforeFirstSuccessAlsoUsesBackoff() {
        assertEquals(now + 15 * minute, ScheduleSyncPolicy.nextAt(now, 0L, now))
    }

    @Test fun successfulSyncSupersedesEarlierFailedAttempt() {
        assertEquals(now + 4 * hour, ScheduleSyncPolicy.nextAt(now, now, now - minute))
        assertEquals(now + 4 * hour, ScheduleSyncPolicy.nextAt(now, now, now))
    }

    @Test fun aRecentSuccessCannotBeShortenedToRetryInterval() {
        assertEquals(now + 3 * hour, ScheduleSyncPolicy.nextAt(now, now - hour, now - minute))
    }

    @Test fun futureClockAnchorsCannotStarveSync() {
        assertEquals(now, ScheduleSyncPolicy.nextAt(now, now + hour, now + hour))
        assertEquals(now, ScheduleSyncPolicy.nextAt(now, 0L, now + hour))
        assertEquals(now + 3 * hour, ScheduleSyncPolicy.nextAt(now, now - hour, now + hour))
        assertEquals(now + 10 * minute, ScheduleSyncPolicy.nextAt(now, now + hour, now - 5 * minute))
    }

    @Test fun sharedChainSelectsEarlierDeadlineAndMergesOverlappingWakeup() {
        assertEquals(now + hour, ScheduleSyncPolicy.nextWakeAt(now + hour, now + 2 * hour))
        assertEquals(now + hour, ScheduleSyncPolicy.nextWakeAt(now + 2 * hour, now + hour))
        assertEquals(now + hour, ScheduleSyncPolicy.nextWakeAt(now + hour, now + hour))
    }

    @Test fun disablingSchoolSyncPreservesPowerAndManualPowerPreservesSchoolFallback() {
        assertEquals(now + hour, ScheduleSyncPolicy.nextWakeAt(now + hour, null))
        assertEquals(now + 4 * hour, ScheduleSyncPolicy.nextWakeAt(null, now + 4 * hour))
        assertNull(ScheduleSyncPolicy.nextWakeAt(null, null))
    }

    @Test fun schoolMaintenanceCanWakeSharedChainDuringPowerNightPause() {
        val zone = ZoneId.of("Asia/Shanghai")
        val day = LocalDate.of(2026, 11, 22)
        fun at(hour: Int) = day.atTime(hour, 0).atZone(zone).toInstant().toEpochMilli()
        val midnight = at(0)
        val powerAt = PowerQueryPolicy.nextSampleAt(midnight, midnight - 3 * hour, 0L, 120,
            LocalTime.of(7, 30), LocalTime.of(22, 5), zone)
        val syncAt = ScheduleSyncPolicy.nextAt(midnight, midnight - 3 * hour, 0L)
        assertEquals(day.atTime(7, 30).atZone(zone).toInstant().toEpochMilli(), powerAt)
        assertEquals(at(1), ScheduleSyncPolicy.nextWakeAt(powerAt, syncAt))
    }
}
