package com.shawnrodis.toolbox

import com.shawnrodis.toolbox.ui.headerSingleRow
import com.shawnrodis.toolbox.ui.snapPanelWidthDp
import com.shawnrodis.toolbox.ui.snapTilesPerRow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v1.0.2 fixes:
 *  - defect A: the ToolCardHeader arrangement decision — single row only
 *    when the title/description column keeps its minimum width, else the
 *    button group wraps onto its own row (kills the text sliver);
 *  - defect D: snap-panel sizing — content-sized width clamped to the
 *    desktop area, tiles wrap to a second row instead of clipping.
 */
class V102FixTest {

    @Test
    fun header_wrapsOnPortraitPhoneWithFiveButtons() {
        // Shawn's portrait phone: content width 360 - 2*16 ToolScreen
        // padding = 328dp; Currency's button group (Clear + Refresh +
        // Share/JPG/PDF) measures ~257dp. One row would leave the text
        // column ~21dp — the v1.0.1 sliver. Must wrap.
        assertFalse(headerSingleRow(328f, 257f))
        // Time's group (trio + "Current") is wider still.
        assertFalse(headerSingleRow(328f, 268f))
        // Even a trio-only header (~193dp) would squeeze text below the
        // 120dp minimum on this width, so it wraps too.
        assertFalse(headerSingleRow(328f, 193f))
    }

    @Test
    fun header_singleRowWhenTextKeepsMinimum() {
        // Boundary: buttons 150 + gap 8 + icon block 50 + min text 120
        // = 328 exactly fits a 328dp content width.
        assertTrue(headerSingleRow(328f, 150f))
        assertFalse(headerSingleRow(328f, 151f))
        // A wide desktop-mode window: everything fits with room to spare.
        assertTrue(headerSingleRow(732f, 268f))
        // A 412dp phone (content 380): trio-only fits on one row.
        assertTrue(headerSingleRow(380f, 193f))
        // A minimal 320dp window (content 288): five buttons wrap.
        assertFalse(headerSingleRow(288f, 257f))
        // No buttons at all (PDF Tools / Square Pic / Settings): one row.
        assertTrue(headerSingleRow(328f, 0f))
    }

    @Test
    fun snapPanel_widthIsContentSizedAndClamped() {
        // Content: 2*12 pad + 6*66 tiles + 5*8 gaps = 460 (unscaled).
        // Shawn's landscape phone (s = 0.55, area 780): 253dp, fits whole.
        assertEquals(253f, snapPanelWidthDp(0.55f, 6, 780f), 0.01f)
        // s = 1.0 on a wide desktop: the full 460.
        assertEquals(460f, snapPanelWidthDp(1.0f, 6, 1600f), 0.01f)
        // s = 1.3: 460 * 1.3 = 598 on a wide area.
        assertEquals(598f, snapPanelWidthDp(1.3f, 6, 2000f), 0.01f)
        // Narrow area (360dp at s = 1.0): clamped to area - 2*8 margin.
        assertEquals(344f, snapPanelWidthDp(1.0f, 6, 360f), 0.01f)
        // v1.0.1 comparison: the old fixed width at s = 0.55 was only
        // 320 * 0.55 = 176dp for ~240dp of tiles — the clip being fixed.
        assertTrue(snapPanelWidthDp(0.55f, 6, 780f) > 176f)
    }

    @Test
    fun snapPanel_tilesWrapInsteadOfClipping() {
        // Full-width panels hold all six tiles in one row.
        assertEquals(6, snapTilesPerRow(0.55f, 6, snapPanelWidthDp(0.55f, 6, 780f)))
        assertEquals(6, snapTilesPerRow(1.0f, 6, snapPanelWidthDp(1.0f, 6, 1600f)))
        // The clamped 344dp panel at s = 1.0 wraps to 4 + 2.
        assertEquals(4, snapTilesPerRow(1.0f, 6, snapPanelWidthDp(1.0f, 6, 360f)))
        // Never below 1, never above the tile count.
        assertEquals(1, snapTilesPerRow(1.0f, 6, 40f))
        assertEquals(3, snapTilesPerRow(1.0f, 3, 460f))
    }
}
