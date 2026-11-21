package com.menglimi.gdust.v2.ui

import com.menglimi.gdust.v2.data.SchoolService
import com.menglimi.gdust.v2.data.SchoolServiceStatus
import com.menglimi.gdust.v2.data.SchoolServicesState
import com.menglimi.gdust.v2.data.ServiceAuthStatus
import com.menglimi.gdust.v2.data.ServiceProblem
import org.junit.Assert.*
import org.junit.Test

class SchoolServiceNavigationTest {
    private val verified = SchoolServiceStatus(auth = ServiceAuthStatus.VERIFIED)
    private val saved = SchoolServiceStatus(auth = ServiceAuthStatus.SAVED)

    @Test
    fun portalReconnectIsNotRedirectedToOldTeachingCookies() {
        val state = SchoolServicesState(portal = verified, teaching = saved)
        assertEquals(SchoolService.PORTAL, selectScheduleService(SchoolService.PORTAL, state, true, true))
    }

    @Test
    fun learningOnlyResultEntersAppWithoutStartingScheduleSync() {
        val result = SchoolLoginResult("request", 1L, false, false, true, null)
        assertTrue(result.anyReady)
        assertNull(result.scheduleService)
        assertNull(selectScheduleService(SchoolService.CHX, SchoolServicesState(chx = verified), false, false))
    }

    @Test
    fun verifiedPortalBeatsUnverifiedTeachingOnManualRefresh() {
        val state = SchoolServicesState(portal = verified, teaching = saved)
        assertEquals(SchoolService.PORTAL, selectScheduleService(null, state, true, true))
    }

    @Test
    fun rejectedServiceIsNotRetriedUsingItsRemainingCookies() {
        val state = SchoolServicesState(portal = verified,
            teaching = SchoolServiceStatus(auth = ServiceAuthStatus.AUTH_REQUIRED))
        assertEquals(SchoolService.PORTAL, selectScheduleService(null, state, true, true))
        assertNull(selectScheduleService(SchoolService.TEACHING, state, true, true))
    }

    @Test
    fun temporaryReadFailureDoesNotBlockRetryOfVerifiedService() {
        val state = SchoolServicesState(teaching = verified.copy(problem = ServiceProblem.READ))
        assertEquals(SchoolService.TEACHING, selectScheduleService(null, state, false, true))
    }

    @Test
    fun onlyMatchingCurrentAccountLoginResultCanBeConsumed() {
        assertTrue(acceptsSchoolLoginResult("a", "a", 3L, 3L, 3L))
        assertFalse(acceptsSchoolLoginResult("a", "old", 3L, 3L, 3L))
        assertFalse(acceptsSchoolLoginResult("a", "a", 3L, 3L, 4L))
        assertFalse(acceptsSchoolLoginResult("a", "a", 3L, 4L, 4L))
        assertFalse(acceptsSchoolLoginResult(null, null, 3L, 3L, 3L))
    }
}
