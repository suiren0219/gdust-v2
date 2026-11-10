package com.menglimi.gdust.v2.data

import java.io.IOException
import java.net.SocketTimeoutException
import org.junit.Assert.*
import org.junit.Test

class SessionPolicyTest {
    @Test fun networkAndMaintenanceDoNotInvalidateSavedLogin() {
        assertTrue(shouldKeepSession(true, SocketTimeoutException("timeout")))
        assertTrue(shouldKeepSession(true, IOException("offline")))
        assertTrue(shouldKeepSession(true, IllegalStateException("maintenance")))
        assertNull(schoolAuthFailure(500, "服务器维护"))
        assertNull(schoolAuthFailure(503, ""))
        assertNull(schoolAuthFailure(403, "网关拒绝访问"))
    }

    @Test fun explicitPortalExpiryIsRecognizedEvenInsideHttp500() {
        assertEquals(117, schoolAuthFailure(500, """{"code":117,"msg":"自动离线"}""")?.code)
        assertEquals(116, schoolAuthFailure(200, """{"code":116}""")?.code)
        assertEquals(401, schoolAuthFailure(401, "")?.code)
        assertFalse(shouldKeepSession(true, AuthFailed(117, "expired")))
    }

    @Test fun successfulResponseAndNullCodeAreNotExpiry() {
        assertNull(schoolAuthFailure(200, """{"code":0,"data":{}}"""))
        assertNull(schoolAuthFailure(200, """{"code":null}"""))
        assertFalse(shouldKeepSession(false, null))
    }

    @Test fun savedTeachingCookiesOnlyGoToExactSchoolEndpoints() {
        assertTrue(isTeachingEndpoint(VPN_TEACHING_ENDPOINT))
        assertTrue(isTeachingEndpoint(TEACHING_ENDPOINT))
        assertFalse(isTeachingEndpoint("https://example.org"))
        assertFalse(isTeachingEndpoint(VPN_TEACHING_ENDPOINT + "&redirect=https://example.org"))
    }
}
