package com.menglimi.gdust.v2.data

import okhttp3.HttpUrl.Companion.toHttpUrl
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

    @Test fun ssoServicesMatchPublicPortalLinksWithoutDoubleEncoding() {
        assertEquals("http://gdkjxy.fysso.chaoxing.com/sso/gdkjxy",
            ChxSso.fyssoUrl.toHttpUrl().queryParameter("service"))
        assertEquals("http://gkjx.gdust.edu.cn/sso/lyiotlogin",
            ChxSso.teachingWebUrl.toHttpUrl().queryParameter("service"))
        // 学校公开 index.js 的完整 collegeContentUrl，不能把 refer 的 % 再编码为 %25。
        assertEquals("https://cas.gdust.edu.cn/lyuapServer/login?service=" +
            "https%3A%2F%2Fgdkj.mh.chaoxing.com%2Fsso%2Flogin%2F3rd%3Fwfwfid%3D420%26refer%3Dhttps%3A%2F%2Fgdkj.mh.chaoxing.com",
            ChxSso.mhUrl)
        assertEquals("https://gdkj.mh.chaoxing.com/sso/login/3rd?wfwfid=420&refer=https://gdkj.mh.chaoxing.com",
            ChxSso.mhUrl.toHttpUrl().queryParameter("service"))
    }

    @Test fun teachingAndPortalCredentialsAreNeverCasWebSessionEvidence() {
        listOf("JSESSIONID=teaching", "TOKEN=portal", "ticket=GKCAS-test", "TGC=").forEach {
            assertFalse(it, ChxSso.hasCasTicket(it))
        }
        assertTrue(ChxSso.hasCasTicket("TGC=synthetic-test-ticket"))
        assertTrue(ChxSso.casCookieProbes.all {
            val url = it.toHttpUrl()
            url.scheme == "https" && url.host == "cas.gdust.edu.cn" && url.query == null
        })
        assertTrue(ChxSso.linkingHint(false).contains("教务或门户登录不代表已登录学习通"))
        assertTrue(ChxSso.linkingHint(true).contains("仍需验证"))
    }

    @Test fun directLoginRemainsAnIndependentHttpsWebFallback() {
        val direct = ChxSso.DIRECT_LOGIN.toHttpUrl()
        assertEquals("https", direct.scheme)
        assertEquals("passport2.chaoxing.com", direct.host)
        assertEquals("/login", direct.encodedPath)
        assertEquals(setOf("fid", "refer"), direct.queryParameterNames)
        assertTrue(ChxSso.isLoginUrl(ChxSso.DIRECT_LOGIN))
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
