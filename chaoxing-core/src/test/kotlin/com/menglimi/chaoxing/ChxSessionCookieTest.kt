package com.menglimi.chaoxing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChxSessionCookieTest {
    @Test fun anonymousAndEmptyCookiesAreNotCandidates() {
        listOf(null, "", "  ", "fid=999", "JSESSIONID=anonymous; route=1", "_uid=; UID=   ",
            "_uid=\"\"; UID=\" \"", "_uid", "UID", "_uid=bad\nvalue").forEach {
            assertFalse(it.orEmpty(), ChxSso.hasSessionCookie(it))
        }
    }

    @Test fun fullCaseSensitiveCookieNamesAreRequired() {
        listOf("fake_uid=1", "MYUID=1", "prefix_UID=1", "uid=1", "_UID=1", "route=_uid=1", "route=UID=2").forEach {
            assertFalse(it, ChxSso.hasSessionCookie(it))
        }
    }

    @Test fun nonEmptyIdentityCookiesAreOnlyCandidatesForVerification() {
        assertTrue(ChxSso.hasSessionCookie(" _uid = 1 ; fid=999"))
        assertTrue(ChxSso.hasSessionCookie("UID=2; JSESSIONID=abc"))
        assertTrue(ChxSso.hasSessionCookie("UID=\"2\""))
        assertTrue(ChxSso.hasSessionCookie("_uid=; _uid=1"))
        assertTrue(ChxSso.hasSessionCookie("_uid=1; _uid="))
    }

    @Test fun parsingPreservesEqualsInValuesAndRejectsInvalidNames() {
        assertEquals(listOf("token" to "a=b==", "_uid" to "1"),
            ChxSso.cookiePairs("broken; bad name=1; token=a=b==; _uid=1"))
    }

    @Test fun onlyChaoxingHostsCountAsLearningApp() {
        assertTrue(ChxSso.isChaoxingHost("mooc2-ans.chaoxing.com"))
        assertTrue(ChxSso.isChaoxingHost("synthetic.fysso.chaoxing.com"))
        assertTrue(ChxSso.isChaoxingHost("I.CHAOXING.CN"))
        assertFalse(ChxSso.isChaoxingHost("login.example.org"))
        assertFalse(ChxSso.isChaoxingHost("notchaoxing.com"))
        assertFalse(ChxSso.isChaoxingHost("chaoxing.com.example.org"))
        assertFalse(ChxSso.isChaoxingHost(null))
    }

    @Test fun savedCookieIsReadForTheExactProbeTarget() {
        assertEquals(listOf(ChxSso.HOME), ChxSso.cookieProbes)
    }

    @Test fun loginRecognitionIsGenericAndDoesNotInspectQueryText() {
        listOf("https://passport2.chaoxing.com/login", "https://passport.chaoxing.com/",
            "https://passport2.chaoxing.com/mlogin", "https://mooc1.chaoxing.com/login/index/").forEach {
            assertTrue(it, ChxSso.isLoginUrl(it))
        }
        listOf("https://login.example.org/cas/login", "https://passport2.chaoxing.com/privacy?refer=login",
            "http://passport2.chaoxing.com/login", "https://passport2.chaoxing.com:8443/login",
            "https://user@passport2.chaoxing.com/login", "https://notchaoxing.com/login").forEach {
            assertFalse(it, ChxSso.isLoginUrl(it))
        }
    }
}
