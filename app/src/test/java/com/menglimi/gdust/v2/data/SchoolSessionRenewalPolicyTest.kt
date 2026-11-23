package com.menglimi.gdust.v2.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SchoolSessionRenewalPolicyTest {
    @Test fun publishedSchoolEntriesAreAllowed() {
        assertTrue(SchoolSessionRenewalPolicy.allows(SchoolLoginWeb.portalLoginUrl))
        assertTrue(SchoolSessionRenewalPolicy.allows(ChxSso.teachingWebUrl))
        assertTrue(SchoolSessionRenewalPolicy.allows(ChxSso.campusTeachingWebUrl))
        assertTrue(SchoolSessionRenewalPolicy.allows(ChxSso.TEACHING_VPN_LOGIN))
        assertTrue(SchoolSessionRenewalPolicy.allows(SchoolLoginWeb.PORTAL_CALLBACK))
    }

    @Test fun onlyKnownTeachingBusinessOriginsCanReceiveNavigation() {
        assertTrue(SchoolSessionRenewalPolicy.allows("http://172.16.254.1/xtgl/index_initMenu.html"))
        assertTrue(SchoolSessionRenewalPolicy.allows(PUBLIC_TEACHING_ENDPOINT))
        assertTrue(SchoolSessionRenewalPolicy.allows(VPN_TEACHING_ENDPOINT))
        assertFalse(SchoolSessionRenewalPolicy.allows("http://172.16.1.232/index"))
        assertFalse(SchoolSessionRenewalPolicy.allows("https://example.com/?service=https://portal.gdust.edu.cn"))
        assertFalse(SchoolSessionRenewalPolicy.allows("https://cas.gdust.edu.cn.example.com/cas/"))
        assertFalse(SchoolSessionRenewalPolicy.allows("https://portal.gdust.edu.cn:444/"))
        assertFalse(SchoolSessionRenewalPolicy.allows("http://portal.gdust.edu.cn/"))
        assertFalse(SchoolSessionRenewalPolicy.allows("https://user@cas.gdust.edu.cn/cas/"))
    }

    @Test fun sessionMaintenanceDoesNotPermitLogoutOrLocalFiles() {
        assertFalse(SchoolSessionRenewalPolicy.allows(ChxSso.LOGOUT_URL))
        assertFalse(SchoolSessionRenewalPolicy.allows("https://cas.gdust.edu.cn/lyuapServer/logout"))
        assertFalse(SchoolSessionRenewalPolicy.allows("javascript:alert(1)"))
        assertFalse(SchoolSessionRenewalPolicy.allows("file:///data/local/tmp/page.html"))
        assertFalse(SchoolSessionRenewalPolicy.allows("dingtalk://auth"))
    }

    @Test fun renewalUsesTheBoundTeachingEntryNotAnArbitraryStoredUrl() {
        assertEquals(ChxSso.campusTeachingWebUrl,
            SchoolSessionRenewalPolicy.teachingEntry("http://172.16.254.1/xtgl/index_initMenu.html"))
        assertEquals(ChxSso.TEACHING_VPN_LOGIN,
            SchoolSessionRenewalPolicy.teachingEntry(VPN_TEACHING_ENDPOINT))
        assertEquals(ChxSso.teachingWebUrl,
            SchoolSessionRenewalPolicy.teachingEntry("https://example.com/xtgl/index_initMenu.html"))
    }
}
