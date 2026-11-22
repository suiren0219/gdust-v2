package com.menglimi.gdust.v2.data

import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.MonthDay

class TeachingBirthdayApiTest {
    private val base = TeachingEndpoints.bases.first()
    private val landing = "$base/xtgl/index_initMenu.html"
    private val menu = """<a onclick="clickMenu('NDEMO','/xsxxxggl/xsgrxxwh_cxXsgrxx.html','个人信息查询','1');">个人信息查询</a>"""

    @Test fun followsOnlyTheReadOnlyProfileMenuAndUsesTargetSpecificRequests() = runBlocking {
        val calls = mutableListOf<Request>()
        val api = TeachingBirthdayApi { request ->
            calls += request
            SessionResponse(200, if (calls.size == 1) menu else
                "<input id='xh' value='demo'><label>出生日期：</label><span>2005-11-15</span>", null)
        }
        assertEquals(MonthDay.of(11, 15), api.load(landing, "demo", LocalDate.of(2026, 11, 15)))
        assertEquals(2, calls.size)
        assertTrue(calls.all { it.method == "GET" && it.header("Cookie") == null })
        assertEquals("NDEMO", calls.last().url.queryParameter("gnmkdm"))
        assertEquals("$base/xsxxxggl/xsgrxxwh_cxXsgrxx.html", calls.last().url.toString().substringBefore('?'))
    }

    @Test fun missingMenuAndLoginRedirectCannotInventABirthday() = runBlocking {
        assertNull(TeachingBirthdayApi { SessionResponse(302, "", "/xtgl/login_slogin.html") }.load(landing, "demo"))
        assertNull(TeachingBirthdayApi { SessionResponse(200, "<a>个人信息查询</a>", null) }.load(landing, "demo"))
        assertNull(TeachingBirthdayApi.findProfile(menu.replace("个人信息查询", "个人信息修改"), base))
        assertNull(TeachingBirthdayApi.findProfile(menu.replace("xsgrxxwh_cxXsgrxx", "xsgrxxwh_cxXsGrxxxgIndex"), base))
        assertNull(TeachingBirthdayApi.findProfile(menu.replace("clickMenu(", "fetch("), base))
        assertNotNull(TeachingBirthdayApi.findProfile(menu.replace("'1'", "''"), base))
    }

    @Test fun vpnPrefixIsPreservedAndOtherOriginsAreRejected() {
        val vpn = TeachingEndpoints.bases.last()
        val profile = TeachingBirthdayApi.findProfile(menu, vpn)!!
        assertTrue(profile.toString().startsWith(vpn + "/xsxxxggl/"))
        assertTrue(TeachingBirthdayApi.isAllowed(vpn, "$vpn/xtgl/index_initMenu.html".toHttpUrl(), profile))
        assertFalse(TeachingBirthdayApi.isAllowed(base, landing.toHttpUrl(), profile))
        assertFalse(TeachingBirthdayApi.isAllowed(base, landing.toHttpUrl(), "https://other.example/".toHttpUrl()))
    }
}
