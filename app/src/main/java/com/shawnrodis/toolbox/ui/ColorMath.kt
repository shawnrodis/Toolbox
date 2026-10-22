package com.shawnrodis.toolbox.ui

import androidx.compose.ui.graphics.Color
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Parity pass — verbatim ports of the source's colour helpers used by the
 * custom accent picker: __rgbToHsv, __hsvToRgb, __rgbToHex, __hexToRgb.
 * h in degrees 0..360, s/v in 0..1, rgb channels 0..255.
 */
data class Hsv(val h: Float, val s: Float, val v: Float)
data class Rgb(val r: Int, val g: Int, val b: Int)

fun rgbToHsv(r: Int, g: Int, b: Int): Hsv {
    val rf = r / 255f; val gf = g / 255f; val bf = b / 255f
    val mx = max(max(rf, gf), bf); val mn = min(min(rf, gf), bf); val d = mx - mn
    var h = 0f
    if (d != 0f) {
        h = when (mx) {
            rf -> 60f * (((gf - bf) / d) % 6f)
            gf -> 60f * ((bf - rf) / d + 2f)
            else -> 60f * ((rf - gf) / d + 4f)
        }
        if (h < 0) h += 360f
    }
    val s = if (mx == 0f) 0f else d / mx
    return Hsv(h, s, mx)
}

fun hsvToRgb(h: Float, s: Float, v: Float): Rgb {
    val c = v * s
    val x = c * (1 - abs(((h / 60f) % 2f) - 1))
    val m = v - c
    val (rp, gp, bp) = when {
        h < 60 -> Triple(c, x, 0f)
        h < 120 -> Triple(x, c, 0f)
        h < 180 -> Triple(0f, c, x)
        h < 240 -> Triple(0f, x, c)
        h < 300 -> Triple(x, 0f, c)
        else -> Triple(c, 0f, x)
    }
    return Rgb(
        ((rp + m) * 255).roundToInt(),
        ((gp + m) * 255).roundToInt(),
        ((bp + m) * 255).roundToInt()
    )
}

fun rgbToHex(r: Int, g: Int, b: Int): String =
    "#%02x%02x%02x".format(r.coerceIn(0, 255), g.coerceIn(0, 255), b.coerceIn(0, 255))

fun hexToRgb(hex: String): Rgb? {
    val cleaned = hex.trim().removePrefix("#")
    if (cleaned.length != 6) return null
    val value = cleaned.toIntOrNull(16) ?: return null
    return Rgb((value shr 16) and 0xFF, (value shr 8) and 0xFF, value and 0xFF)
}

fun Color.toToolboxRgb(): Rgb = Rgb(
    (red * 255).roundToInt(), (green * 255).roundToInt(), (blue * 255).roundToInt()
)

fun Rgb.toColor(): Color = Color(r, g, b)
