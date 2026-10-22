package com.shawnrodis.toolbox

import com.shawnrodis.toolbox.ui.sortedToolsForDisplay
import com.shawnrodis.toolbox.ui.wmDefaultRect
import com.shawnrodis.toolbox.ui.wmIconCell
import com.shawnrodis.toolbox.ui.wmScaleFor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v1.0.1 parity fixes:
 *  - defect #1: the shared display list (source sortedToolsForDisplay)
 *    excludes tab-settings + tab-home on EVERY display surface (home
 *    views, Quick Switcher, Start menu, desktop icons);
 *  - defect #3: wmApplyScale + defaultRect (Desktop Mode display scaling).
 */
class V101ParityTest {

    @Test
    fun displayList_excludesSettingsAndHome_evenWhenPinned() {
        val order = sortedToolsForDisplay(
            listOf("tab-settings", "tab-tip", "tab-curr")
        ).map { it.tabId }
        assertFalse(order.contains("tab-settings"))
        assertFalse(order.contains("tab-home"))
        assertFalse(order.contains("settings"))
        // 14 registry entries minus settings (home is not a registry tool) = 13.
        assertEquals(13, order.size)
        // Pins-first holds for real tools; the settings pin is inert.
        assertEquals("tab-tip", order[0])
        assertEquals("tab-curr", order[1])
        // The rest is A-Z by name.
        val names = order.drop(2).map { id ->
            com.shawnrodis.toolbox.ui.ALL_TOOLS.first { it.tabId == id }.name.lowercase()
        }
        assertEquals(names.sorted(), names)
    }

    @Test
    fun wmScale_matchesSourceFormula() {
        // 1600x900 = 1.0 reference resolution.
        assertEquals(1.0f, wmScaleFor(1600f, 900f), 0.001f)
        // Shawn's phone landscape (~780x360dp) hits the 0.55 floor.
        assertEquals(0.55f, wmScaleFor(780f, 360f), 0.001f)
        // Cap at 1.3.
        assertEquals(1.3f, wmScaleFor(3200f, 1800f), 0.001f)
        // Portrait phone also floors (width-driven).
        assertEquals(0.55f, wmScaleFor(360f, 780f), 0.001f)
        // 720p: min(1280/1600, 720/900) = 0.8.
        assertEquals(0.8f, wmScaleFor(1280f, 720f), 0.001f)
    }

    @Test
    fun wmIconCell_scalesCardPlusGap() {
        val (w1, h1) = wmIconCell(1.0f)
        assertEquals(114f, w1, 0.001f)
        assertEquals(106f, h1, 0.001f)
        val (w2, h2) = wmIconCell(0.55f)
        assertEquals(62.7f, w2, 0.01f)
        assertEquals(58.3f, h2, 0.01f)
    }

    @Test
    fun wmDefaultRect_desktopArea() {
        // 1600x846 area (900 - 54 taskbar): w clamps to 780, h to 600.
        val r = wmDefaultRect(1600f, 846f, 0)
        assertEquals(780f, r.w, 0.01f)
        assertEquals(600f, r.h, 0.01f)
        assertEquals(410f, r.x, 0.01f)
        assertEquals(99f, r.y, 0.01f)
        assertFalse(r.cascadeReset)
    }

    @Test
    fun wmDefaultRect_phoneLandscapeArea() {
        // 780 x ~330 area: h percentage underflows the 360 clamp, then the
        // area-40 min-clamp pulls it to ~290; y pins to the 10 margin.
        val r = wmDefaultRect(780f, 330.3f, 0)
        assertEquals(452f, r.w, 0.01f)
        assertEquals(290.3f, r.h, 0.05f)
        assertEquals(164f, r.x, 0.01f)
        assertEquals(10f, r.y, 0.01f)
    }

    @Test
    fun wmDefaultRect_tinyArea_usesMinWindowSize() {
        val r = wmDefaultRect(360f, 600f, 0)
        assertEquals(320f, r.w, 0.01f) // MIN_W
        assertEquals(432f, r.h, 0.01f)
        assertTrue(r.x >= 10f)
    }
}
