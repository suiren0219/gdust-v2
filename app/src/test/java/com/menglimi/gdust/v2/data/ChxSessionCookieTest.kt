package com.menglimi.gdust.v2.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChxSessionCookieTest {
    @Test fun bareOrEmptyCookiesAreNotASession() {
        assertFalse(ChxSso.hasSessionCookie(null))
        assertFalse(ChxSso.hasSessionCookie(""))
        assertFalse(ChxSso.hasSessionCookie("  "))
        // 只有路由/机构标记不算登录
        assertFalse(ChxSso.hasSessionCookie("fid="))
    }

    @Test fun sessionMarkersAreRecognized() {
        assertTrue(ChxSso.hasSessionCookie("JSESSIONID=abc; route=1"))
        assertTrue(ChxSso.hasSessionCookie("_uid=1; fid=420"))
    }

    @Test fun onlyChaoxingHostsCountAsLearningApp() {
        assertTrue(ChxSso.isChaoxingHost("mooc2-ans.chaoxing.com"))
        assertTrue(ChxSso.isChaoxingHost("gdkjxy.fysso.chaoxing.com"))
        assertFalse(ChxSso.isChaoxingHost("cas.gdust.edu.cn"))
        assertFalse(ChxSso.isChaoxingHost("notchaoxing.com"))
        assertFalse(ChxSso.isChaoxingHost(null))
    }

    @Test fun ssoUrlsStayOnSchoolCas() {
        listOf(ChxSso.fyssoUrl, ChxSso.mhUrl, ChxSso.teachingWebUrl).forEach { url ->
            assertTrue(url, url.startsWith("https://cas.gdust.edu.cn/lyuapServer/login?service="))
        }
    }
}
