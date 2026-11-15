package com.menglimi.gdust.v2.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class DateWheelSelectionTest {
    @Test
    fun movingFromLongMonthKeepsLastValidDay() {
        assertEquals(LocalDate.of(2026, 4, 30), DateWheelSelection.changeMonth(LocalDate.of(2026, 3, 31), month = 4))
        assertEquals(LocalDate.of(2026, 2, 28), DateWheelSelection.changeMonth(LocalDate.of(2026, 1, 31), month = 2))
    }

    @Test
    fun leapDayIsClampedWhenChangingYear() {
        assertEquals(LocalDate.of(2025, 2, 28), DateWheelSelection.changeMonth(LocalDate.of(2024, 2, 29), year = 2025))
        assertEquals(LocalDate.of(2024, 2, 29), DateWheelSelection.changeMonth(LocalDate.of(2024, 1, 31), month = 2))
    }

    @Test
    fun ordinaryDayIsPreservedWhenChangingMonth() {
        assertEquals(LocalDate.of(2027, 8, 16), DateWheelSelection.changeMonth(LocalDate.of(2026, 11, 16), year = 2027, month = 8))
    }

    @Test
    fun mondayShortcutCrossesMonthAndYearBoundaries() {
        assertEquals(LocalDate.of(2025, 12, 29), DateWheelSelection.monday(LocalDate.of(2026, 1, 1)))
        assertEquals(LocalDate.of(2026, 11, 16), DateWheelSelection.monday(LocalDate.of(2026, 11, 16)))
    }

    @Test
    fun termStartAllowsCalendarFallbackOrMondayOnly() {
        assertTrue(DateWheelSelection.validTermStart(""))
        assertTrue(DateWheelSelection.validTermStart("2026-08-31"))
        assertFalse(DateWheelSelection.validTermStart("2026-09-01"))
        assertFalse(DateWheelSelection.validTermStart("2026-02-30"))
        assertNull(DateWheelSelection.parse("not-a-date"))
    }
}
