package com.shawnrodis.toolbox.tools

import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * A REAL gaussian blur over a packed-ARGB pixel array — pure Kotlin
 * (no Android imports) so it is JVM-unit-testable, same pattern as
 * [SquareGeometry].
 *
 * Why this exists (v1.2.2): Square Pic's blur background was, through
 * v1.2.1, a cover-fit downscale/upscale pass — the tiny intermediate
 * bitmap left visible blocky ("Minecraft") facets that the web
 * edition's smooth `ctx.filter` gaussian never had (Shawn, comparing
 * the two editions side by side). This replaces that shortcut in both
 * the preview and the 2048×2048 export.
 *
 * Algorithm: the standard three-pass box-blur approximation of a
 * gaussian (the construction the SVG feGaussianBlur spec itself
 * suggests). Three box blurs whose widths are derived from the target
 * σ converge to a kernel that is visually indistinguishable from a
 * true gaussian, and each box pass is a sliding-window sum — O(1) per
 * pixel regardless of radius, so a 2048×2048 export at σ≈114 (slider
 * 40) costs the same per pixel as σ=1. All four channels are blurred
 * independently; edges replicate (clamp), which is what a blurred
 * cover background should do — the source photo overflows the square
 * on the overflowing axis, and on the fitted axis its edge pixels
 * simply extend.
 *
 * σ semantics: [sigma] is the gaussian standard deviation in TARGET
 * bitmap pixels, matching the web's canvas filter — the source does
 * `ctx.filter = 'blur(' + (sqBlur * k) + 'px)'` with k = S/720, and
 * the CSS blur length IS the gaussian σ. Callers scale σ by
 * target/720 exactly like the source (see SquarePicScreen).
 */

/**
 * The three box widths (each odd, ≥ 1) whose successive box blurs
 * approximate a gaussian of the given [sigma]. Standard derivation:
 * wIdeal = sqrt(12σ²/n + 1); the first m boxes use the odd width at
 * or below wIdeal, the rest use wIdeal + 2.
 */
fun gaussianBoxSizes(sigma: Double, passes: Int = 3): IntArray {
    if (passes <= 0) return IntArray(0)
    if (sigma.isNaN() || sigma <= 0.0) return IntArray(passes) { 1 }
    val wIdeal = sqrt((12.0 * sigma * sigma / passes) + 1.0)
    var wl = floor(wIdeal).toInt()
    if (wl % 2 == 0) wl -= 1
    if (wl < 1) wl = 1
    val wu = wl + 2
    val mIdeal = (12.0 * sigma * sigma - passes * wl * wl -
        4.0 * passes * wl - 3.0 * passes) / (-4.0 * wl - 4.0)
    val m = mIdeal.roundToInt().coerceIn(0, passes)
    return IntArray(passes) { i -> if (i < m) wl else wu }
}

/**
 * Blurs [pixels] (packed ARGB, row-major, [width] × [height]) in
 * place with a gaussian of the given [sigma] (in pixels). Sigma
 * below ~0.5 resolves to box widths of 1 and leaves the image
 * effectively unchanged; sigma ≤ 0 or NaN is a no-op.
 *
 * Cost: 3 boxes × (horizontal + vertical) sliding-window passes,
 * each O(width × height) with a small constant; scratch memory is a
 * single line buffer of max(width, height) ints.
 */
fun gaussianBlurPixels(pixels: IntArray, width: Int, height: Int, sigma: Double) {
    if (width <= 0 || height <= 0) return
    require(pixels.size >= width * height) { "pixel array smaller than width*height" }
    if (sigma.isNaN() || sigma <= 0.0) return
    val scratch = IntArray(maxOf(width, height))
    for (box in gaussianBoxSizes(sigma)) {
        if (box <= 1) continue
        boxBlurHorizontal(pixels, width, height, box, scratch)
        boxBlurVertical(pixels, width, height, box, scratch)
    }
}

/** One horizontal box-blur pass of odd width [box], rows independent. */
private fun boxBlurHorizontal(px: IntArray, w: Int, h: Int, box: Int, line: IntArray) {
    val r = box / 2
    val half = box / 2 // rounding bias: (sum + box/2) / box
    for (y in 0 until h) {
        val rowOff = y * w
        System.arraycopy(px, rowOff, line, 0, w)
        var sa = 0; var sr = 0; var sg = 0; var sb = 0
        // Initial window: source indices -r..r, clamped to the row.
        for (i in -r..r) {
            val p = line[i.coerceIn(0, w - 1)]
            sa += (p ushr 24) and 0xFF; sr += (p ushr 16) and 0xFF
            sg += (p ushr 8) and 0xFF; sb += p and 0xFF
        }
        for (x in 0 until w) {
            px[rowOff + x] = (((sa + half) / box) shl 24) or (((sr + half) / box) shl 16) or
                (((sg + half) / box) shl 8) or ((sb + half) / box)
            // Slide: drop index x-r, add index x+r+1 (both clamped).
            val outP = line[(x - r).coerceIn(0, w - 1)]
            val inP = line[(x + r + 1).coerceIn(0, w - 1)]
            sa += ((inP ushr 24) and 0xFF) - ((outP ushr 24) and 0xFF)
            sr += ((inP ushr 16) and 0xFF) - ((outP ushr 16) and 0xFF)
            sg += ((inP ushr 8) and 0xFF) - ((outP ushr 8) and 0xFF)
            sb += (inP and 0xFF) - (outP and 0xFF)
        }
    }
}

/** One vertical box-blur pass of odd width [box], columns independent. */
private fun boxBlurVertical(px: IntArray, w: Int, h: Int, box: Int, line: IntArray) {
    val r = box / 2
    val half = box / 2
    for (x in 0 until w) {
        for (j in 0 until h) line[j] = px[j * w + x]
        var sa = 0; var sr = 0; var sg = 0; var sb = 0
        for (i in -r..r) {
            val p = line[i.coerceIn(0, h - 1)]
            sa += (p ushr 24) and 0xFF; sr += (p ushr 16) and 0xFF
            sg += (p ushr 8) and 0xFF; sb += p and 0xFF
        }
        for (y in 0 until h) {
            px[y * w + x] = (((sa + half) / box) shl 24) or (((sr + half) / box) shl 16) or
                (((sg + half) / box) shl 8) or ((sb + half) / box)
            val outP = line[(y - r).coerceIn(0, h - 1)]
            val inP = line[(y + r + 1).coerceIn(0, h - 1)]
            sa += ((inP ushr 24) and 0xFF) - ((outP ushr 24) and 0xFF)
            sr += ((inP ushr 16) and 0xFF) - ((outP ushr 16) and 0xFF)
            sg += ((inP ushr 8) and 0xFF) - ((outP ushr 8) and 0xFF)
            sb += (inP and 0xFF) - (outP and 0xFF)
        }
    }
}
