package com.menglimi.gdust.v2.data

import java.time.LocalDate
import java.time.MonthDay
import org.junit.Assert.*
import org.junit.Test

class BirthdayTest {
    private val today = LocalDate.of(2026, 11, 15)

    @Test fun birthdayOnlyUsesExplicitLabelOrField() {
        assertEquals(MonthDay.of(11, 15), Birthday.fromTeachingHtml(
            "<table><tr><td>出生日期：</td><td>2005-11-15</td></tr></table>", "", today))
        assertEquals(MonthDay.of(11, 15), Birthday.fromTeachingHtml(
            "<label for='birthday'>出生日期</label><input id='birthday' value='2005/11/15'>", "", today))
        assertNull(Birthday.fromTeachingHtml("<p>开学日期：2005-11-15</p>", "", today))
        assertNull(Birthday.fromTeachingHtml("<script>var birthday='2005-11-15'</script>", "", today))
    }

    @Test fun invalidFutureConflictingOrOtherAccountDataIsRejected() {
        assertNull(Birthday.parseDate("2005-02-30", today))
        assertNull(Birthday.parseDate("2027-01-01", today))
        assertNull(Birthday.parseDate("110105200511150000", today))
        assertNull(Birthday.fromTeachingHtml("<input id='csrq' value='2005-11-15'><input name='csrq' value='2005-11-16'>", "", today))
        assertNull(Birthday.fromTeachingHtml("<input id='xh' value='another-demo'><input id='csrq' value='2005-11-15'>", "demo", today))
        assertNull(Birthday.fromTeachingHtml("<input type='password'><input id='csrq' value='2005-11-15'>", "demo", today))
    }

    @Test fun birthdayDisplaysOnceOnTheMatchingDay() {
        assertTrue(Birthday.shouldShow("--11-15", "2025-11-15", today))
        assertFalse(Birthday.shouldShow("--11-15", "2026-11-15", today))
        assertFalse(Birthday.shouldShow("--11-16", "", today))
        assertFalse(Birthday.shouldShow("", "", today))
    }

    @Test fun leapBirthdayUsesLastDayOfFebruaryInNonLeapYears() {
        assertTrue(Birthday.isToday(MonthDay.of(2, 29), LocalDate.of(2025, 2, 28)))
        assertTrue(Birthday.isToday(MonthDay.of(2, 29), LocalDate.of(2024, 2, 29)))
        assertFalse(Birthday.isToday(MonthDay.of(2, 29), LocalDate.of(2024, 2, 28)))
    }
}
