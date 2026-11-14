package com.menglimi.gdust.v2.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChxLogoutTest {
    @Test fun trustedLogoutPageNeedsExplicitCompletionText() {
        assertTrue(ChxSso.confirmsSchoolLogout(ChxSso.LOGOUT_URL, "统一认证\n注销成功\n返回", loadSucceeded = true, sentCasTicket = true))
        assertTrue(ChxSso.confirmsSchoolLogout(ChxSso.LOGOUT_URL,
            "You have successfully logged out of the Central Authentication Service.", loadSucceeded = true, sentCasTicket = true))
        assertTrue(ChxSso.confirmsSchoolLogout("https://cas.gdust.edu.cn/lyuapServer/logout", "您已成功退出系统。", loadSucceeded = true, sentCasTicket = true))
    }

    @Test fun reachingLogoutUrlOrReturningEarlyNeverConfirmsLogout() {
        listOf(null, "", "正在退出", "退出", "退出失败", "请点击退出成功后返回", "用户登录",
            "无法退出成功", "系统维护，请稍后重试").forEach {
            assertFalse(ChxSso.confirmsSchoolLogout(ChxSso.LOGOUT_URL, it, loadSucceeded = true, sentCasTicket = true))
        }
    }

    @Test fun failedLoadsCannotConfirmEvenWhenTextLooksSuccessful() {
        assertFalse(ChxSso.confirmsSchoolLogout(ChxSso.LOGOUT_URL, "注销成功", loadSucceeded = false, sentCasTicket = true))
    }

    @Test fun anAnonymousLogoutPageCannotConfirmRevocationOfTheOldSession() {
        assertFalse(ChxSso.confirmsSchoolLogout(ChxSso.LOGOUT_URL, "注销成功", loadSucceeded = true, sentCasTicket = false))
        assertFalse(ChxSso.hasCasTicket("JSESSIONID=anonymous; fakeTGC=value; TGC="))
        assertTrue(ChxSso.hasCasTicket("TGC=test-ticket; route=1"))
    }

    @Test fun onlyExactSecureSchoolLogoutEndpointsCanConfirm() {
        listOf("https://example.org/logout", "http://cas.gdust.edu.cn/cas/logout",
            "https://cas.gdust.edu.cn.evil.example/cas/logout", "https://cas.gdust.edu.cn/login?next=/logout",
            "https://cas.gdust.edu.cn/cas/logout-error", "https://cas.gdust.edu.cn:8443/cas/logout",
            "https://user@cas.gdust.edu.cn/cas/logout").forEach {
            assertFalse(it, ChxSso.confirmsSchoolLogout(it, "注销成功", loadSucceeded = true, sentCasTicket = true))
        }
    }

    @Test fun cleanupIncludesParentDomainsWithoutDeletingSharedSchoolSessionNames() {
        assertEquals(listOf(null, "gdkjxy.fysso.chaoxing.com", "fysso.chaoxing.com", "chaoxing.com"),
            ChxWebSession.cookieDomains("gdkjxy.fysso.chaoxing.com", "_uid"))
        assertEquals(listOf(null, "cas.gdust.edu.cn", "gdust.edu.cn"),
            ChxWebSession.cookieDomains("cas.gdust.edu.cn", "TGC"))
        assertEquals(listOf(null, "cas.gdust.edu.cn"),
            ChxWebSession.cookieDomains("cas.gdust.edu.cn", "JSESSIONID"))
        assertEquals(listOf(null), ChxWebSession.cookieDomains("i.chaoxing.com", "__Host-session"))
        assertTrue(ChxWebSession.cookieDomains("gkjx.gdust.edu.cn", "JSESSIONID").isEmpty())
    }

    @Test fun cleanupProbesCommonAndVisitedPathsWithoutCredentialsOrQueries() {
        val probes = ChxWebSession.probeUrls("https://user:secret@cas.gdust.edu.cn/lyuapServer/custom/page?ticket=secret#fragment")
        assertTrue(probes.contains("https://cas.gdust.edu.cn/lyuapServer"))
        assertTrue(probes.contains("https://cas.gdust.edu.cn/lyuapServer/custom/"))
        assertTrue(probes.contains("https://cas.gdust.edu.cn/cas/logout"))
        assertTrue(probes.contains("http://cas.gdust.edu.cn/"))
        assertTrue(probes.none { it.contains("secret") || it.contains('?') || it.contains('#') || it.contains('@') })
        assertTrue(ChxWebSession.probeUrls("https://gkjx.gdust.edu.cn/").isEmpty())
        assertTrue(ChxWebSession.probeUrls(ChxSso.HOME).contains(ChxSso.HOME))
    }

    @Test fun webStorageCleanupProtectsTeachingAndUnrelatedOrigins() {
        assertTrue(ChxWebSession.isSessionOrigin("https://cas.gdust.edu.cn"))
        assertTrue(ChxWebSession.isSessionOrigin("https://gdkj.mh.chaoxing.com"))
        assertTrue(ChxWebSession.isSessionOrigin("https://i.chaoxing.cn"))
        assertFalse(ChxWebSession.isSessionOrigin("https://gkjx.gdust.edu.cn"))
        assertFalse(ChxWebSession.isSessionOrigin("https://portal.gdust.edu.cn"))
        assertFalse(ChxWebSession.isSessionOrigin("https://notchaoxing.com"))
        assertFalse(ChxWebSession.isSessionOrigin("https://chaoxing.com.example.org"))
    }
}
