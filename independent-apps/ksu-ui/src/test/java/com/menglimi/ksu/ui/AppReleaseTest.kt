package com.menglimi.ksu.ui

import org.junit.Assert.*
import org.junit.Test

class AppReleaseTest {
    private fun release(name: String, draft: Boolean = false, url: String =
        "${SuirenProject.HOME}/releases/download/standalone-0.1.2/$name"): String = """
        {"draft":$draft,"prerelease":true,
         "html_url":"${SuirenProject.HOME}/releases/tag/standalone-0.1.2",
         "assets":[{"name":"$name","browser_download_url":"$url","state":"uploaded","size":1024}]}
    """.trimIndent()

    @Test fun ignoresOtherAppAndMainProjectPackages() {
        val json = "[${release("gdust-v2-99.0.0.apk")},${release("learning-assistant-9.0.0-preview.apk")},${release("dorm-power-0.1.2-preview.apk")}]"
        val found = findAppRelease(json, "dorm-power")!!
        assertEquals("0.1.2", found.version)
        assertTrue(found.download.endsWith("/dorm-power-0.1.2-preview.apk"))
    }

    @Test fun comparesNumericVersionsInsteadOfTagsOrPublicationOrder() {
        val json = "[${release("dorm-power-0.1.9-preview.apk")},${release("dorm-power-0.1.10-preview.apk")}]"
        assertEquals("0.1.10", findAppRelease(json, "dorm-power")!!.version)
    }

    @Test fun rejectsDraftsUnsignedDebugAndForeignDownloads() {
        val json = "[${release("dorm-power-0.1.2-preview.apk", draft = true)}," +
            release("dorm-power-release-unsigned.apk") + "," + release("dorm-power-0.1.2-debug.apk") + "," +
            release("dorm-power-0.1.2-preview.apk", url = "https://example.test/dorm-power-0.1.2-preview.apk") + "]"
        assertNull(findAppRelease(json, "dorm-power"))
    }

    @Test fun rejectsMismatchedDownloadFilenameAndRepository() {
        assertNull(findAppRelease("[${release("learning-assistant-0.1.2-preview.apk", url = "${SuirenProject.HOME}/releases/download/v1/dorm-power-0.1.2-preview.apk")}]", "learning-assistant"))
        assertNull(findAppRelease("[${release("dorm-power-0.1.2-preview.apk", url = "https://github.com/other/repo/releases/download/v1/dorm-power-0.1.2-preview.apk")}]", "dorm-power"))
    }

    @Test fun doesNotOfferOlderOrEqualBuilds() {
        val candidate = AppRelease("0.1.1", "", "", true)
        assertFalse(newerAppRelease(candidate, "0.1.1"))
        assertFalse(newerAppRelease(candidate, "0.2.0"))
        assertTrue(newerAppRelease(candidate, "0.1.0"))
        assertNull(findAppRelease("[]", "learning-assistant"))
    }

    @Test fun desktopNameSupportsUnicodeAndRejectsEmptyOrControlCharacters() {
        assertNull(desktopNameError("穗稔的学习助手 🌾"))
        assertNull(desktopNameError("🌾".repeat(30)))
        assertNotNull(desktopNameError("🌾".repeat(31)))
        assertNotNull(desktopNameError("   "))
        assertNotNull(desktopNameError("学习\n助手"))
    }
}
