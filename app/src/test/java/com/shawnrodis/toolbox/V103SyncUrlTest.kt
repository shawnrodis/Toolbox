package com.shawnrodis.toolbox

import com.shawnrodis.toolbox.data.SYNC_KEYS
import com.shawnrodis.toolbox.data.SYNC_URL_KEY
import com.shawnrodis.toolbox.data.syncConfigured
import com.shawnrodis.toolbox.data.syncEndpoint
import com.shawnrodis.toolbox.data.syncGetUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v1.0.3: the sync server URL is a user-entered setting (stored under
 * SYNC_URL_KEY, default empty) — there is no built-in endpoint. Sync is
 * unconfigured until BOTH a URL and a token are present, and the stored
 * URL is the one used to build the endpoint. Fixtures use a placeholder
 * host only.
 */
class V103SyncUrlTest {

    private val placeholderUrl = "http://example.invalid:9000"

    @Test
    fun urlKey_isTheSettingsKey_andNeverSynced() {
        assertEquals("toolbox_sync_url", SYNC_URL_KEY)
        assertFalse(SYNC_KEYS.contains(SYNC_URL_KEY))
    }

    @Test
    fun blankUrl_meansUnconfigured_evenWithToken() {
        assertFalse(syncConfigured(null, "token"))
        assertFalse(syncConfigured("", "token"))
        assertFalse(syncConfigured("   ", "token"))
    }

    @Test
    fun urlWithoutToken_meansUnconfigured() {
        assertFalse(syncConfigured(placeholderUrl, null))
        assertFalse(syncConfigured(placeholderUrl, ""))
    }

    @Test
    fun storedUrlAndToken_meansConfigured() {
        assertTrue(syncConfigured(placeholderUrl, "token"))
    }

    @Test
    fun storedUrl_isNormalisedAndUsedForEndpoint() {
        assertEquals(placeholderUrl, syncGetUrl("  $placeholderUrl  "))
        assertEquals(placeholderUrl, syncGetUrl("$placeholderUrl/"))
        assertEquals("", syncGetUrl(null))
        assertEquals("", syncGetUrl("   "))
        assertEquals("$placeholderUrl/state", syncEndpoint(placeholderUrl))
        assertEquals("$placeholderUrl/state", syncEndpoint(" $placeholderUrl/ "))
    }
}
