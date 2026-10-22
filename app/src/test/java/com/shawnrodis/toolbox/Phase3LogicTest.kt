package com.shawnrodis.toolbox

import com.shawnrodis.toolbox.data.SYNC_KEYS
import com.shawnrodis.toolbox.data.assemblePush
import com.shawnrodis.toolbox.data.computePull
import com.shawnrodis.toolbox.data.syncGetInterval
import com.shawnrodis.toolbox.data.syncIntervalLabel
import com.shawnrodis.toolbox.tools.PageRanges
import com.shawnrodis.toolbox.tools.sanitizeOcrText
import com.shawnrodis.toolbox.tools.sqBackgroundCoverRect
import com.shawnrodis.toolbox.tools.sqClampPan
import com.shawnrodis.toolbox.tools.sqClampZoom
import com.shawnrodis.toolbox.tools.sqExportFilename
import com.shawnrodis.toolbox.tools.sqForegroundRect
import com.shawnrodis.toolbox.ui.stampedFilename
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

/**
 * Phase 3 pure-logic verification: sync payload/pull maths, split page
 * ranges, Square Pic geometry + filename, export stamped filenames, and
 * the OCR sanitizeText rule — all against the source JS semantics.
 */
class Phase3LogicTest {

    // ---- Sync -------------------------------------------------------------

    @Test
    fun syncKeys_matchSourceList() {
        assertEquals(26, SYNC_KEYS.size)
        assertTrue(SYNC_KEYS.contains("toolbox_notes"))
        assertTrue(SYNC_KEYS.contains("pdf_suite_preview_scale"))
        assertTrue(SYNC_KEYS.contains("skytrack_mobile_state"))
        // In the source's Reset list but NOT its SYNC_KEYS; owner directed
        // 2026-10-22 that shopping items must sync (v1.0.4 deviation).
        assertTrue(SYNC_KEYS.contains("toolbox_shop_items"))
        // Per-device secrets/config are never synced.
        assertFalse(SYNC_KEYS.contains("toolbox_sync_token"))
        assertFalse(SYNC_KEYS.contains("toolbox_sync_url"))
    }

    @Test
    fun syncIntervalLabel_matchesSource() {
        assertEquals("0ms", syncIntervalLabel(0))
        assertEquals("999ms", syncIntervalLabel(999))
        assertEquals("1 second", syncIntervalLabel(1000))
        assertEquals("5 seconds", syncIntervalLabel(5000))
        assertEquals("1 minute", syncIntervalLabel(60000))
        assertEquals("5 minutes", syncIntervalLabel(300000))
    }

    @Test
    fun syncGetInterval_badInputsBecomeOff() {
        assertEquals(0L, syncGetInterval(null))
        assertEquals(0L, syncGetInterval("abc"))
        assertEquals(0L, syncGetInterval("-5"))
        assertEquals(5000L, syncGetInterval("5000"))
    }

    @Test
    fun assemblePush_filtersNonSyncKeysAndSplitsDeleted() {
        val pending = mapOf("toolbox_notes" to 1, "toolbox_tally_items" to 1, "not_a_sync_key" to 1)
        val values = mapOf<String, String?>(
            "toolbox_notes" to "[{\"id\":\"n1\"}]",
            "toolbox_tally_items" to null // deleted locally
        )
        val payload = assemblePush("TEST_TOKEN", pending, values)
        assertEquals(listOf("toolbox_notes", "toolbox_tally_items"), payload.keys)
        assertEquals("[{\"id\":\"n1\"}]", payload.updates["toolbox_notes"])
        assertEquals(listOf("toolbox_tally_items"), payload.deleted)
        // Body shape: {token, updates:{...}, deleted:[...]} exactly as source.
        assertEquals("TEST_TOKEN", payload.body.getString("token"))
        assertEquals(
            "[{\"id\":\"n1\"}]",
            payload.body.getJSONObject("updates").getString("toolbox_notes")
        )
        assertEquals("toolbox_tally_items", payload.body.getJSONArray("deleted").getString(0))
    }

    @Test
    fun computePull_appliesNewerSkipsPendingAndDeletesMissing() {
        val server = JSONObject()
            .put(
                "toolbox_notes",
                JSONObject().put("value", "server-notes").put("updated_at", "2026-10-22T02:00:00Z")
            )
            .put(
                "toolbox_accent",
                JSONObject().put("value", "indigo").put("updated_at", "2026-10-22T01:00:00Z")
            )
        val meta = mapOf(
            "toolbox_notes" to "2026-10-22T01:30:00Z", // server newer -> apply
            "toolbox_dark" to "2026-10-22T01:00:00Z"   // absent on server -> delete
        )
        val pending = mapOf("toolbox_accent" to 1)     // locally dirty -> skip
        val plan = computePull(meta, pending, server)
        assertEquals("server-notes", plan.changes["toolbox_notes"])
        assertTrue(plan.changes.containsKey("toolbox_dark"))
        assertNull(plan.changes["toolbox_dark"])
        assertFalse(plan.changes.containsKey("toolbox_accent"))
        assertEquals("2026-10-22T02:00:00Z", plan.newMeta["toolbox_notes"])
        assertFalse(plan.newMeta.containsKey("toolbox_dark"))
        assertEquals(2, plan.applied)
    }

    @Test
    fun computePull_olderServerValueIgnored() {
        val server = JSONObject()
            .put(
                "toolbox_notes",
                JSONObject().put("value", "old").put("updated_at", "2026-10-22T01:00:00Z")
            )
        val meta = mapOf("toolbox_notes" to "2026-10-22T02:00:00Z")
        val plan = computePull(meta, emptyMap(), server)
        assertEquals(0, plan.applied)
        assertTrue(plan.changes.isEmpty())
    }

    // ---- Split page ranges --------------------------------------------------

    @Test
    fun pageRanges_parseOrderClampAndGarbage() {
        assertEquals(
            listOf(1, 2, 3, 5),
            PageRanges.parseRangeInput("1-3, 5", 10).toList()
        )
        // Insertion (Set) order is kept, duplicates collapse.
        assertEquals(listOf(3, 1), PageRanges.parseRangeInput("3, 1, 3", 10).toList())
        // Out-of-range clamped away; unparsable parts ignored.
        assertEquals(listOf(2), PageRanges.parseRangeInput("0, 2, 99, x, -", 5).toList())
        assertTrue(PageRanges.parseRangeInput("", 10).isEmpty())
        assertTrue(PageRanges.parseRangeInput("1-3", 0).isEmpty())
    }

    @Test
    fun pageRanges_toggleFieldTextAndSplitIndices() {
        val sel = linkedSetOf<Int>()
        PageRanges.toggle(sel, 4)
        PageRanges.toggle(sel, 2)
        assertEquals("2, 4", PageRanges.rangeFieldText(sel))
        PageRanges.toggle(sel, 4)
        assertEquals(listOf(2), sel.toList())
        // processSplit: empty selection = all pages; otherwise Set order, 0-based.
        assertEquals(listOf(0, 1, 2), PageRanges.splitIndices(emptySet(), 3))
        assertEquals(listOf(2, 0), PageRanges.splitIndices(linkedSetOf(3, 1), 5))
    }

    // ---- Square Pic ---------------------------------------------------------

    @Test
    fun squareFilename_matchesSource() {
        assertEquals("photo_square.png", sqExportFilename("photo.jpg"))
        assertEquals("a.b_square.png", sqExportFilename("a.b.png"))
        assertEquals("image_square.png", sqExportFilename(""))
        assertEquals("image_square.png", sqExportFilename(null))
        assertEquals("noext_square.png", sqExportFilename("noext"))
    }

    @Test
    fun squareGeometry_fitFillAndCover() {
        // Landscape 2000x1000 into 720: fit -> 720x360 centred (top=180).
        val fit = sqForegroundRect(2000, 1000, 720, true, 1.0, 0.0, 0.0)
        assertEquals(720.0, fit.width, 0.001)
        assertEquals(360.0, fit.height, 0.001)
        assertEquals(0.0, fit.left, 0.001)
        assertEquals(180.0, fit.top, 0.001)
        // Fill -> scale to height: 1440x720, left=-360.
        val fill = sqForegroundRect(2000, 1000, 720, false, 1.0, 0.0, 0.0)
        assertEquals(1440.0, fill.width, 0.001)
        assertEquals(-360.0, fill.left, 0.001)
        // Background cover uses the fill maths.
        val cover = sqBackgroundCoverRect(2000, 1000, 720)
        assertEquals(fill, cover)
    }

    @Test
    fun squareClampPan_limitsToOverflow() {
        // Fit with zoom 1 cannot pan at all.
        val c1 = sqClampPan(2000, 1000, 720, true, 1.0, 500.0, -500.0)
        assertEquals(0.0, c1.first, 0.001)
        assertEquals(0.0, c1.second, 0.001)
        // Fill: max |ox| = (1440-720)/2 = 360 (already in 720 space).
        val c2 = sqClampPan(2000, 1000, 720, false, 1.0, 9999.0, 0.0)
        assertEquals(360.0, c2.first, 0.001)
        // Zoom clamps 1..3.
        assertEquals(1.0, sqClampZoom(0.5), 0.001)
        assertEquals(3.0, sqClampZoom(9.0), 0.001)
        assertEquals(2.0, sqClampZoom(2.0), 0.001)
    }

    // ---- Exports --------------------------------------------------------------

    @Test
    fun stampedFilename_insertsTimestampBeforeExtension() {
        val cal = Calendar.getInstance().apply {
            set(2026, Calendar.OCTOBER, 22, 13, 45, 7)
        }
        val d = cal.time
        assertEquals("tally-export_2026-10-22_13-45-07.pdf", stampedFilename("tally-export.pdf", d))
        assertEquals("notes_2026-10-22_13-45-07", stampedFilename("notes", d))
        assertEquals("a.b_2026-10-22_13-45-07.jpg", stampedFilename("a.b.jpg", d))
    }

    // ---- OCR --------------------------------------------------------------------

    @Test
    fun sanitizeText_matchesSourceRegex() {
        assertEquals("\"hi\" 'x'", sanitizeOcrText("“hi” ‘x’"))
        assertEquals("caf\u00E9", sanitizeOcrText("caf\u00E9")) // A0-FF range kept
        assertEquals("ab", sanitizeOcrText("a\uD83D\uDE00b"))   // emoji stripped
        assertEquals("", sanitizeOcrText(null))
    }
}
