package com.menglimi.gdust.v2.work

import com.google.gson.Gson
import com.menglimi.gdust.v2.data.CourseBlock
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LessonNotificationLifetimeTest {
    private val zone = ZoneId.of("Asia/Shanghai")
    private val date = LocalDate.of(2026, 11, 9)
    private val block = CourseBlock(1, 1, 1, 2, "线性代数", "A101", "教师", date.toString())
    private fun at(hour: Int, minute: Int) = date.atTime(hour, minute).atZone(zone).toInstant().toEpochMilli()
    private fun event(kind: ReminderKind = ReminderKind.BEFORE_CLASS) = ReminderEvent(
        "lesson", at(8, 10), kind, date, block = block, startText = "08:30", endText = "10:05",
    )
    private fun lease(kind: ReminderKind? = ReminderKind.IN_CLASS, island: Boolean = true) =
        LessonNotificationLease("current", at(10, 5), kind, listOf(block), island)
    private fun keep(
        lease: LessonNotificationLease = lease(),
        now: Long = at(9, 55),
        notifications: Boolean = true,
        reminders: Boolean = true,
        inClass: Boolean = true,
        nextDay: Boolean = true,
        island: Boolean = true,
        blocks: List<CourseBlock> = listOf(block),
    ) = keepLessonNotification(lease, now, notifications, reminders, inClass, nextDay, island, blocks)

    @Test fun beforeAndInClassNotificationsEndAtRealLessonEnd() {
        assertEquals(at(10, 5), lessonNotificationDeadline(event(), zone))
        assertEquals(at(10, 5), lessonNotificationDeadline(event(ReminderKind.IN_CLASS), zone))
        assertEquals(10 * 60_000L, lessonNotificationTimeout(at(10, 5), at(9, 55)))
        assertEquals(600, lessonIslandTimeoutSeconds(at(10, 5), at(9, 55)))
    }

    @Test fun delayedInClassDeliveryNeverAddsFiveMinutesAfterClass() {
        val deadline = lessonNotificationDeadline(event(ReminderKind.IN_CLASS), zone)!!
        assertEquals(60_000L, lessonNotificationTimeout(deadline, at(10, 4)))
        assertEquals(0L, lessonNotificationTimeout(deadline, at(10, 5)))
        assertEquals(0L, lessonNotificationTimeout(deadline, at(10, 9)))
        assertFalse(keep(now = deadline))
    }

    @Test fun timeoutUsesMillisecondsAndIslandRoundsUpOnlyOneSecond() {
        val end = at(10, 5)
        assertEquals(1L, lessonNotificationTimeout(end, end - 1))
        assertEquals(1, lessonIslandTimeoutSeconds(end, end - 1))
        assertEquals(2, lessonIslandTimeoutSeconds(end, end - 1001))
        assertEquals(0, lessonIslandTimeoutSeconds(end, end))
    }

    @Test fun invalidOrReversedLessonTimesCannotCreatePersistentNotification() {
        assertNull(lessonNotificationDeadline(event().copy(endText = "unknown"), zone))
        assertNull(lessonNotificationDeadline(event().copy(endText = "08:00"), zone))
        assertNull(lessonNotificationDeadline(event().copy(endText = "08:30"), zone))
    }

    @Test fun notificationDeadlineFollowsEventDateAndZoneNotDeliveryDate() {
        val nextDay = event().copy(date = date.plusDays(1))
        assertEquals(at(10, 5) + 24 * 60 * 60_000L, lessonNotificationDeadline(nextDay, zone))
        assertEquals(at(10, 5) + 8 * 60 * 60_000L, lessonNotificationDeadline(event(), ZoneId.of("UTC")))
    }

    @Test fun nextDaySummaryHasFiniteLifetimeAnchoredToScheduledSend() {
        val summary = event(ReminderKind.NEXT_DAY).copy(atMillis = at(23, 0), date = date.plusDays(1), block = null,
            nextDayBlocks = listOf(block), startText = "", endText = "")
        val deadline = lessonNotificationDeadline(summary, zone)!!
        assertEquals(at(23, 0) + 12 * 60 * 60_000L, deadline)
        assertEquals(11 * 60 * 60_000L, lessonNotificationTimeout(deadline, at(23, 0) + 60 * 60_000L))
    }

    @Test fun oldExpiryCannotCancelReplacementEvenAfterOldEnd() {
        val replacement = lease().copy(token = "new", expiresAtMillis = at(12, 0))
        assertFalse(canExpireLessonNotification(replacement, "current", "new", at(10, 5)))
        assertFalse(canExpireLessonNotification(replacement, "current", "new", at(12, 0)))
        assertFalse(canExpireLessonNotification(lease(), "current", "new", at(10, 5)))
        assertTrue(canExpireLessonNotification(replacement, "new", "new", at(12, 0)))
    }

    @Test fun earlyDuplicateOrMissingExpiryDoesNotCancelOtherNotification() {
        val lease = lease()
        assertFalse(canExpireLessonNotification(lease, lease.token, lease.token, lease.expiresAtMillis - 1))
        assertTrue(canExpireLessonNotification(lease, lease.token, lease.token, lease.expiresAtMillis))
        assertFalse(canExpireLessonNotification(null, lease.token, lease.token, lease.expiresAtMillis))
        assertFalse(canExpireLessonNotification(lease, lease.token, null, lease.expiresAtMillis))
    }

    @Test fun shuttingOffRemindersOrNotificationPermissionClearsExistingLesson() {
        assertTrue(keep())
        assertFalse(keep(reminders = false))
        assertFalse(keep(notifications = false))
    }

    @Test fun categorySwitchOnlyClearsMatchingCategory() {
        assertFalse(keep(inClass = false))
        assertFalse(keep(lease(ReminderKind.NEXT_DAY), nextDay = false))
        assertTrue(keep(lease(ReminderKind.BEFORE_CLASS), inClass = false, nextDay = false))
        assertTrue(keep(lease(ReminderKind.NEXT_DAY), inClass = false))
    }

    @Test fun islandSwitchClearsIslandButKeepsPlainNotification() {
        assertFalse(keep(island = false))
        assertTrue(keep(lease(island = false), island = false))
    }

    @Test fun clearingOrReplacingScheduleInvalidatesOldLesson() {
        assertFalse(keep(blocks = emptyList()))
        assertFalse(keep(blocks = listOf(block.copy(date = date.plusDays(1).toString()))))
        assertTrue(keep(blocks = listOf(block, block.copy(name = "新课程"))))
    }

    @Test fun explicitTestNotificationWorksWithRemindersOffButStillExpires() {
        val test = lease(kind = null)
        assertTrue(keep(test, reminders = false, blocks = emptyList()))
        assertFalse(keep(test, now = test.expiresAtMillis, reminders = false))
        assertFalse(keep(test, notifications = false, reminders = false))
        assertFalse(keep(test, island = false, reminders = false))
    }

    @Test fun persistedLeaseKeepsTokenBoundaryAndCourseAcrossProcessRestart() {
        val original = lease()
        val stored = encodeLessonNotificationLease(original, Gson())
        val restored = decodeLessonNotificationLease(stored, Gson())
        assertEquals(original, restored)
        assertTrue(keep(restored!!))
        assertTrue(canExpireLessonNotification(restored, original.token, original.token, at(10, 5)))
        assertFalse(canExpireLessonNotification(restored.copy(token = "replacement"), original.token, "replacement", at(12, 0)))
    }

    @Test fun testLeaseWithNoCategorySurvivesSerialization() {
        val test = lease(kind = null).copy(blocks = emptyList())
        assertEquals(test, decodeLessonNotificationLease(encodeLessonNotificationLease(test, Gson()), Gson()))
    }

    @Test fun corruptOrUnknownPersistedLeaseIsRejected() {
        val gson = Gson()
        assertNull(decodeLessonNotificationLease(null, gson))
        assertNull(decodeLessonNotificationLease("not-json", gson))
        assertNull(decodeLessonNotificationLease("{}", gson))
        val valid = encodeLessonNotificationLease(lease(), gson)
        assertNull(decodeLessonNotificationLease(valid.replace("IN_CLASS", "UNKNOWN"), gson))
        assertNull(decodeLessonNotificationLease(encodeLessonNotificationLease(lease().copy(token = ""), gson), gson))
    }
}
