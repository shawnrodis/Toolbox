package com.shawnrodis.toolbox

import com.shawnrodis.toolbox.data.RESET_KEYS_PRIMARY
import com.shawnrodis.toolbox.data.RESET_KEYS_SYNC
import com.shawnrodis.toolbox.ui.hexToRgb
import com.shawnrodis.toolbox.ui.hsvToRgb
import com.shawnrodis.toolbox.ui.parsePins
import com.shawnrodis.toolbox.ui.pinsToJson
import com.shawnrodis.toolbox.ui.rgbToHex
import com.shawnrodis.toolbox.ui.rgbToHsv
import com.shawnrodis.toolbox.ui.sortedToolsForDisplay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * Parity-pass logic tests: verbatim ports checked against the source JS
 * semantics (colour math from __hsvToRgb/__rgbToHsv, pin ordering from
 * sortedToolsForDisplay, reset key lists from resetSettings).
 */
class ParityLogicTest {

    @Test
    fun hsvToRgb_matchesSourcePrimaries() {
        // __hsvToRgb sector math at full saturation/value.
        assertEquals(Triple(255, 0, 0), hsvToRgb(0f, 1f, 1f).let { Triple(it.r, it.g, it.b) })
        assertEquals(Triple(0, 255, 0), hsvToRgb(120f, 1f, 1f).let { Triple(it.r, it.g, it.b) })
        assertEquals(Triple(0, 0, 255), hsvToRgb(240f, 1f, 1f).let { Triple(it.r, it.g, it.b) })
        // Default Toolbox accent indigo #6366f1 survives an HSV round trip.
        val indigo = hexToRgb("#6366f1")!!
        val hsv = rgbToHsv(indigo.r, indigo.g, indigo.b)
        val back = hsvToRgb(hsv.h, hsv.s, hsv.v)
        assertTrue(abs(back.r - indigo.r) <= 1)
        assertTrue(abs(back.g - indigo.g) <= 1)
        assertTrue(abs(back.b - indigo.b) <= 1)
        assertEquals("#6366f1", rgbToHex(indigo.r, indigo.g, indigo.b))
    }

    @Test
    fun pins_parseAndRoundTrip() {
        assertEquals(listOf("tab-curr", "tab-tip"), parsePins("[\"tab-curr\",\"tab-tip\"]"))
        assertEquals(emptyList<String>(), parsePins("not json"))
        assertEquals(emptyList<String>(), parsePins("[]"))
        assertEquals(
            listOf("tab-notes"),
            parsePins(pinsToJson(listOf("tab-notes")))
        )
    }

    @Test
    fun sortedToolsForDisplay_pinnedFirstThenAlphabetical() {
        val order = sortedToolsForDisplay(listOf("tab-tip", "tab-curr")).map { it.tabId }
        assertEquals("tab-tip", order[0])
        assertEquals("tab-curr", order[1])
        val rest = order.drop(2)
        val names = rest.map { id ->
            com.shawnrodis.toolbox.ui.ALL_TOOLS.first { it.tabId == id }.name.lowercase()
        }
        assertEquals(names.sorted(), names)
        // v1.0.1: the source's sortedToolsForDisplay excludes tab-settings
        // (+ tab-home) — 13 display tools, not 14. This assertion previously
        // encoded the defect (Settings rendered as a home tile).
        assertEquals(13, order.size)
    }

    @Test
    fun resetKeyLists_matchSourceResetSettings() {
        // Source resetSettings() primary list (30 keys) and sync list (5).
        assertEquals(30, RESET_KEYS_PRIMARY.size)
        assertTrue(RESET_KEYS_PRIMARY.contains("toolbox_notes"))
        assertTrue(RESET_KEYS_PRIMARY.contains("toolbox_taskbar_pins"))
        assertTrue(RESET_KEYS_PRIMARY.contains("toolbox_desktop_icon_pos"))
        assertTrue(RESET_KEYS_PRIMARY.contains("skytrack_mobile_state"))
        assertTrue(RESET_KEYS_PRIMARY.contains("toolbox_gemini_key_2"))
        assertEquals(
            listOf(
                "toolbox_sync_url", "toolbox_sync_token", "toolbox_sync_interval",
                "toolbox_sync_meta", "toolbox_sync_pending"
            ),
            RESET_KEYS_SYNC
        )
    }
}
