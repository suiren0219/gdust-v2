package com.menglimi.chaoxing

import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class ChxReadDiagnosticsTest {
    private val base = "https://mooc1.chaoxing.com/visit/stucoursemiddle?courseid=201&enc=private".toHttpUrl()

    @Test fun routeKeepsKnownHostAndStaticPathButDropsQueryAndFragment() {
        val summary = ChxReadDiagnostics.route(base,
            "http://mooc2-ans.chaoxing.com/mooc2-ans/mycourse/stu?enc=private&cpi=301#private")
        assertEquals("http://mooc2-ans.chaoxing.com/mooc2-ans/mycourse/stu", summary)
        assertFalse(summary.contains("private"))
        assertFalse(summary.contains("301"))
    }

    @Test fun failuresGroupDifferentStagesInsteadOfShowingOnlyTheFirstCourse() {
        assertEquals("40 门：缺少 workEnc\n14 门：课程页跳转未支持",
            ChxReadDiagnostics.failures(linkedMapOf("课程页跳转未支持" to 14, "缺少 workEnc" to 40)))
    }

    @Test fun unknownHostAndDynamicPathNeverExposeAccountOrSignature() {
        assertEquals("其他站点", ChxReadDiagnostics.route(base, "https://private.example/account-name?enc=private"))
        assertEquals("其他超星站点", ChxReadDiagnostics.route(base, "https://custom.chaoxing.com/account-name"))
        assertEquals("https://mooc1.chaoxing.com/…/…", ChxReadDiagnostics.route(base, "/account-name/0123456789abcdef?enc=private"))
        assertEquals("无跳转地址", ChxReadDiagnostics.route(base, null))
    }
}
