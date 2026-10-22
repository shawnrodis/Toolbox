package com.shawnrodis.toolbox

import com.shawnrodis.toolbox.tools.gaussianBlurPixels
import com.shawnrodis.toolbox.tools.gaussianBoxSizes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v1.2.2 — Square Pic's blur background is a REAL gaussian
 * ([gaussianBlurPixels], three-pass box-blur construction), replacing
 * the v1.0.0-era downscale/upscale approximation whose tiny
 * intermediate bitmap left blocky "Minecraft" facets (Shawn,
 * comparing the Android fill against the web edition's smooth
 * ctx.filter blur). These tests pin the property that shortcut
 * violated: a hard edge must melt GRADUALLY — many intermediate
 * levels, no flat blocks, no big jumps — and fine detail must
 * average out to a smooth field rather than alias into chunks.
 * The production paths (preview + 2048 export in SquarePicScreen)
 * delegate all blurring to this one function, so a regression to a
 * sampling shortcut fails here.
 */
class V122SquareBlurTest {

    private fun gray(v: Int): Int =
        (0xFF shl 24) or (v shl 16) or (v shl 8) or v

    private fun green(p: Int): Int = (p ushr 8) and 0xFF

    @Test
    fun boxSizes_areOddPositiveAndGrowWithSigma() {
        for (sigma in listOf(0.5, 1.0, 3.0, 9.0, 18.0, 51.2, 113.8)) {
            val sizes = gaussianBoxSizes(sigma)
            assertEquals(3, sizes.size)
            for (s in sizes) {
                assertTrue("box size $s (sigma $sigma) must be odd", s % 2 == 1)
                assertTrue("box size $s (sigma $sigma) must be >= 1", s >= 1)
            }
        }
        val small = gaussianBoxSizes(2.0).max()
        val large = gaussianBoxSizes(40.0).max()
        assertTrue("larger sigma must use wider boxes ($small vs $large)", large > small)
    }

    @Test
    fun sigmaZero_leavesPixelsUnchanged() {
        val px = IntArray(64) { gray((it * 37) % 256) }
        val before = px.copyOf()
        gaussianBlurPixels(px, 8, 8, 0.0)
        assertTrue(before.contentEquals(px))
    }

    @Test
    fun uniformImage_isUnchangedByAnySigma() {
        val px = IntArray(50 * 50) { gray(173) }
        gaussianBlurPixels(px, 50, 50, 25.0)
        assertTrue(px.all { it == gray(173) })
    }

    @Test
    fun hardEdge_blursGradually_noBlocksNoJumps() {
        val w = 120
        val h = 24
        // Left half black, right half white; every row identical, so
        // the middle row shows the pure horizontal step response.
        val px = IntArray(w * h) { i -> gray(if (i % w < w / 2) 0 else 255) }
        gaussianBlurPixels(px, w, h, 6.0)
        val row = IntArray(w) { x -> green(px[(h / 2) * w + x]) }

        // Monotonic melt across the edge.
        for (x in 1 until w) {
            assertTrue("row must be non-decreasing at x=$x", row[x] >= row[x - 1])
        }
        // Many intermediate levels (a downscale/upscale shortcut
        // crosses the edge in a few coarse steps).
        val intermediates = row.filter { it in 9..246 }.distinct().size
        assertTrue("expected >= 12 distinct intermediate levels, got $intermediates", intermediates >= 12)
        // No big single-pixel jumps.
        var maxStep = 0
        for (x in 1 until w) maxStep = maxOf(maxStep, row[x] - row[x - 1])
        assertTrue("max single-step jump $maxStep must be <= 40", maxStep <= 40)
        // No flat blocks inside the transition band: runs of identical
        // values (the "Minecraft" facets) must stay tiny.
        var run = 1
        var maxRunInBand = 1
        for (x in 1 until w) {
            run = if (row[x] == row[x - 1]) run + 1 else 1
            if (row[x] in 16..239) maxRunInBand = maxOf(maxRunInBand, run)
        }
        assertTrue("longest flat run in the transition band: $maxRunInBand (must be <= 4)", maxRunInBand <= 4)
        // The edge centre lands near mid-grey, symmetric melt.
        assertTrue("value just left of edge ${row[59]} must be in 100..155", row[59] in 100..155)
        assertTrue("value just right of edge ${row[60]} must be in 100..155", row[60] in 100..155)
        // Far from the edge the originals survive.
        assertEquals(0, row[0])
        assertEquals(255, row[w - 1])
    }

    @Test
    fun checkerboard_averagesToNearUniform_noAliasingBlocks() {
        val w = 64
        val h = 64
        // 1-px checkerboard: the highest-frequency pattern there is.
        // A true gaussian melts it to flat mid-grey; a downscale
        // sampling shortcut aliases it into blocky moire.
        val px = IntArray(w * h) { i ->
            val x = i % w
            val y = i / w
            gray(if ((x + y) % 2 == 0) 0 else 255)
        }
        gaussianBlurPixels(px, w, h, 3.0)
        // Interior only: replicate-edge clamping feeds same-parity
        // copies into the outermost few pixels (a boundary effect of
        // any edge-extending blur), which says nothing about
        // aliasing; sampling shortcuts alias across the WHOLE field.
        var min = 255
        var max = 0
        for (y in 12 until h - 12) {
            for (x in 12 until w - 12) {
                val v = green(px[y * w + x])
                min = minOf(min, v)
                max = maxOf(max, v)
                assertTrue("interior pixel ($x,$y) must be near mid-grey, got $v", v in 118..138)
            }
        }
        assertTrue("residual ripple ${max - min} must be <= 16", max - min <= 16)
    }

    @Test
    fun alphaChannel_isPreserved() {
        val px = IntArray(30 * 30) { gray(90) }
        gaussianBlurPixels(px, 30, 30, 8.0)
        assertTrue(px.all { (it ushr 24) == 0xFF })
    }
}
