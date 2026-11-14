package com.menglimi.gdust.v2.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChxSessionCookieTest {
    @Test fun anonymousAndEmptyCookiesAreNotCandidates() {
        listOf(null, "", "  ", "fid=420", "JSESSIONID=anonymous; route=1", "_uid=; UID=   ",
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
        assertTrue(ChxSso.hasSessionCookie(" _uid = 1 ; fid=420"))
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
        assertTrue(ChxSso.isChaoxingHost("gdkjxy.fysso.chaoxing.com"))
        assertTrue(ChxSso.isChaoxingHost("I.CHAOXING.CN"))
        assertFalse(ChxSso.isChaoxingHost("cas.gdust.edu.cn"))
        assertFalse(ChxSso.isChaoxingHost("notchaoxing.com"))
        assertFalse(ChxSso.isChaoxingHost("chaoxing.com.example.org"))
        assertFalse(ChxSso.isChaoxingHost(null))
    }

    @Test fun savedCookieIsReadForTheExactProbeTarget() {
        assertEquals(listOf(ChxSso.HOME), ChxSso.cookieProbes)
    }

    @Test fun ssoUrlsStayOnSchoolCas() {
        listOf(ChxSso.fyssoUrl, ChxSso.mhUrl, ChxSso.teachingWebUrl).forEach { url ->
            assertTrue(url, url.startsWith("https://cas.gdust.edu.cn/lyuapServer/login?service="))
        }
    }

    @Test fun teachingWebEntryMatchesSaveWebImportWhitelist() {
        listOf(
            "https://gkjx.gdust.edu.cn/xsdt",
            "http://gkjx.gdust.edu.cn/xsdt", // 教务公网入口实测只有 HTTP
            "https://webvpn.gdust.edu.cn/http/portal",
            "http://172.16.254.1/",
            "https://portal.gdust.edu.cn/index",
        ).forEach { assertTrue(it, ChxSso.isTeachingWebEntry(it)) }
        listOf(
            "https://chaoxing.com",
            "https://evil.org/?url=gdust.edu.cn",
            "ftp://172.16.254.1",
            "",
        ).forEach { assertFalse(it, ChxSso.isTeachingWebEntry(it)) }
    }
}
