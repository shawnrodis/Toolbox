package com.shawnrodis.toolbox.tools

import kotlin.math.max
import kotlin.math.min

/**
 * Square Pic geometry — a direct port of the source's sq* maths.
 * All pan offsets live in "preview space": the source canvas is
 * SQ_PREVIEW = 720 units square regardless of output size, and every draw
 * scales by k = S / SQ_PREVIEW for the target size S (720 preview, 2048
 * export). Pure Kotlin (no Android imports) so it is JVM-unit-testable.
 */
const val SQ_PREVIEW = 720
const val SQ_EXPORT = 2048

/** An axis-aligned rect in target-canvas pixels. */
data class SqRect(val left: Double, val top: Double, val width: Double, val height: Double)

/** Source sqExportFilename(): base name (last extension stripped) + "_square.png". */
fun sqExportFilename(name: String?): String {
    val n = (name ?: "").trim().ifEmpty { "image" }
    val i = n.lastIndexOf('.')
    val base = if (i > 0) n.substring(0, i) else n
    return base + "_square.png"
}

/**
 * Source sqClampPan(S): pan (in preview space) is clamped so the drawn
 * photo never leaves an uncovered gap: max offset per axis is
 * max(0, (drawn - S) / 2), converted back into preview space.
 * Returns the clamped (ox, oy) in preview space.
 */
fun sqClampPan(
    iw: Int, ih: Int, s: Int, fit: Boolean, zoom: Double, ox: Double, oy: Double
): Pair<Double, Double> {
    if (iw <= 0 || ih <= 0) return ox to oy
    val base = if (fit) min(s.toDouble() / iw, s.toDouble() / ih)
    else max(s.toDouble() / iw, s.toDouble() / ih)
    val dw = iw * base * zoom
    val dh = ih * base * zoom
    val mx = max(0.0, (dw - s) / 2)
    val my = max(0.0, (dh - s) / 2)
    val k = s.toDouble() / SQ_PREVIEW
    val cox = min(mx, max(-mx, ox * k)) / k
    val coy = min(my, max(-my, oy * k)) / k
    return cox to coy
}

/**
 * Source sqDraw() step 1 (blur mode): the background is the photo drawn
 * cover-fit (scale = max(S/iw, S/ih)), centred. Blur is applied on top.
 */
fun sqBackgroundCoverRect(iw: Int, ih: Int, s: Int): SqRect {
    val sc = max(s.toDouble() / iw, s.toDouble() / ih)
    val dw = iw * sc
    val dh = ih * sc
    return SqRect((s - dw) / 2, (s - dh) / 2, dw, dh)
}

/**
 * Source sqDraw() step 2: the foreground photo rect for the current
 * fit/fill, zoom and (already clamped) pan. Offsets arrive in preview
 * space and are scaled by k = S / SQ_PREVIEW exactly like the source.
 */
fun sqForegroundRect(
    iw: Int, ih: Int, s: Int, fit: Boolean, zoom: Double, ox: Double, oy: Double
): SqRect {
    val k = s.toDouble() / SQ_PREVIEW
    val base = if (fit) min(s.toDouble() / iw, s.toDouble() / ih)
    else max(s.toDouble() / iw, s.toDouble() / ih)
    val dw = iw * base * zoom
    val dh = ih * base * zoom
    return SqRect((s - dw) / 2 + ox * k, (s - dh) / 2 + oy * k, dw, dh)
}

/** Source sqDraw() step 3: inner border width at target size (bw = sqBorderW * k). */
fun sqBorderWidthPx(borderWidthPreview: Double, s: Int): Double =
    borderWidthPreview * (s.toDouble() / SQ_PREVIEW)

/** Source sqSetZoom(): zoom is clamped to 1..3. */
fun sqClampZoom(v: Double): Double = min(3.0, max(1.0, if (v.isNaN()) 1.0 else v))
