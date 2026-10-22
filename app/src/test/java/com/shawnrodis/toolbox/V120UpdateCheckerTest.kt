package com.shawnrodis.toolbox

import com.shawnrodis.toolbox.data.UPDATE_CHECK_INTERVAL_MS
import com.shawnrodis.toolbox.data.compareVersions
import com.shawnrodis.toolbox.data.isUpdateAvailable
import com.shawnrodis.toolbox.data.parseLatestRelease
import com.shawnrodis.toolbox.data.shouldAutoCheck
import com.shawnrodis.toolbox.data.updateBannerVisible
import com.shawnrodis.toolbox.data.versionParts
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v1.2.0 — the update checker's pure logic (UpdateChecker.kt):
 * version comparison, GitHub release parsing + asset selection,
 * the 24 h auto-check throttle, and the Home banner's per-version
 * dismissal. The network and the installer hand-off are platform
 * plumbing around these functions and are not unit-tested here.
 */
class V120UpdateCheckerTest {

    // ---------------------------------------------------------- versions

    @Test
    fun compare_equalVersions() {
        assertEquals(0, compareVersions("1.2.0", "1.2.0"))
        assertEquals(0, compareVersions("0.0.0", "0.0.0"))
    }

    @Test
    fun compare_vPrefixIsIgnored() {
        assertEquals(0, compareVersions("v1.2.0", "1.2.0"))
        assertEquals(0, compareVersions("1.2.0", "V1.2.0"))
    }

    @Test
    fun compare_olderAndNewer() {
        assertTrue(compareVersions("1.1.1", "1.2.0") < 0)
        assertTrue(compareVersions("1.2.0", "1.1.1") > 0)
        assertTrue(compareVersions("1.2.0", "1.2.1") < 0)
        assertTrue(compareVersions("2.0.0", "1.9.9") > 0)
    }

    @Test
    fun compare_numericNotLexicographic() {
        // "10" > "9" numerically, even though "1.10.0" < "1.9.0" as text.
        assertTrue(compareVersions("1.10.0", "1.9.0") > 0)
        assertTrue(compareVersions("1.9.0", "1.10.0") < 0)
    }

    @Test
    fun compare_partialVersionsPadWithZero() {
        assertEquals(0, compareVersions("1.2", "1.2.0"))
        assertEquals(0, compareVersions("1", "1.0.0"))
        assertTrue(compareVersions("2", "1.9.9") > 0)
        assertTrue(compareVersions("1.2.1", "1.2") > 0)
    }

    @Test
    fun compare_prereleaseAndGarbageDoNotCrash() {
        // Suffixes are dropped: a prerelease of the same version counts
        // as that version (Toolbox releases are plain X.Y.Z tags).
        assertEquals(0, compareVersions("1.2.0-beta", "1.2.0"))
        assertEquals(0, compareVersions("1.2.0+42", "1.2.0"))
        assertEquals(listOf(0), versionParts(""))
        assertEquals(listOf(0), versionParts("abc"))
        assertEquals(listOf(1, 0, 3), versionParts("1.x.3"))
    }

    @Test
    fun isUpdateAvailable_onlyWhenStrictlyNewer() {
        assertTrue(isUpdateAvailable("1.1.1", "1.2.0"))
        assertFalse(isUpdateAvailable("1.2.0", "1.2.0"))
        assertFalse(isUpdateAvailable("1.2.0", "1.1.1"))
        assertFalse(isUpdateAvailable("1.2.0", null))
        assertFalse(isUpdateAvailable("1.2.0", ""))
        assertFalse(isUpdateAvailable("1.2.0", "  "))
    }

    // ------------------------------------------------------- API parsing

    private fun releaseJson(
        tag: String = "v1.2.0",
        assets: String = """
            [
              {"name": "Other_v9.9.9.apk", "browser_download_url": "https://example.com/other.apk"},
              {"name": "Toolbox_v1.2.0.apk", "browser_download_url": "https://example.com/Toolbox_v1.2.0.apk"},
              {"name": "Toolbox_v1.2.0.html", "browser_download_url": "https://example.com/Toolbox_v1.2.0.html"},
              {"name": "checksums.txt", "browser_download_url": "https://example.com/checksums.txt"},
              {"name": "Toolbox_v1.2.0.apk.sha256", "browser_download_url": "https://example.com/sum"}
            ]
        """.trimIndent()
    ) = JSONObject(
        """{"tag_name": "$tag", "html_url": "https://github.com/shawnrodis/toolbox/releases/tag/$tag", "assets": $assets}"""
    )

    @Test
    fun parse_tagAndReleasePage() {
        val rel = parseLatestRelease(releaseJson())!!
        assertEquals("1.2.0", rel.version)
        assertEquals(
            "https://github.com/shawnrodis/toolbox/releases/tag/v1.2.0",
            rel.releasePageUrl
        )
    }

    @Test
    fun parse_picksToolboxAssetsByNameNotPosition() {
        val rel = parseLatestRelease(releaseJson())!!
        // The unrelated Other_v9.9.9.apk comes FIRST in the fixture and
        // the .apk.sha256 decoy is present: only exact Toolbox names win.
        assertEquals("https://example.com/Toolbox_v1.2.0.apk", rel.apkUrl)
        assertEquals("https://example.com/Toolbox_v1.2.0.html", rel.htmlUrl)
    }

    @Test
    fun parse_missingAssetsYieldNullUrls() {
        val rel = parseLatestRelease(releaseJson(assets = "[]"))!!
        assertEquals("1.2.0", rel.version)
        assertNull(rel.apkUrl)
        assertNull(rel.htmlUrl)
    }

    @Test
    fun parse_tagWithoutVAndMissingTag() {
        assertEquals("1.3.0", parseLatestRelease(releaseJson(tag = "1.3.0"))!!.version)
        assertNull(parseLatestRelease(JSONObject("""{"assets": []}""")))
        assertNull(parseLatestRelease(JSONObject("""{"tag_name": ""}""")))
    }

    // ---------------------------------------------------------- throttle

    @Test
    fun throttle_neverCheckedAlwaysChecks() {
        assertTrue(shouldAutoCheck(0L, 1_000_000L))
        assertTrue(shouldAutoCheck(-5L, 1_000_000L))
    }

    @Test
    fun throttle_withinAndBeyondInterval() {
        val now = 100L * UPDATE_CHECK_INTERVAL_MS
        assertFalse(shouldAutoCheck(now - 60_000L, now))                    // 1 min ago
        assertFalse(shouldAutoCheck(now - UPDATE_CHECK_INTERVAL_MS + 1, now))
        assertTrue(shouldAutoCheck(now - UPDATE_CHECK_INTERVAL_MS, now))   // exactly 24 h
        assertTrue(shouldAutoCheck(now - 2 * UPDATE_CHECK_INTERVAL_MS, now))
    }

    // --------------------------------------------------------- dismissal

    @Test
    fun banner_showsForNewUndismissedVersionOnly() {
        assertTrue(updateBannerVisible("1.1.1", "1.2.0", null))
        assertTrue(updateBannerVisible("1.1.1", "1.2.0", ""))
        assertTrue(updateBannerVisible("1.1.1", "1.2.0", "1.1.9"))  // an older dismissal
        assertFalse(updateBannerVisible("1.1.1", "1.2.0", "1.2.0")) // this version dismissed
        assertFalse(updateBannerVisible("1.2.0", "1.2.0", null))      // already current
        assertFalse(updateBannerVisible("1.2.0", null, null))         // nothing known
    }
}
