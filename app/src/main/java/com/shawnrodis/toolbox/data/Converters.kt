package com.shawnrodis.toolbox.data

import androidx.compose.ui.graphics.Color

// ---------------------------------------------------------------------------
// Unit Converter data — UC_DEFS from the source HTML, factors verbatim.
// Base units: weight=kg, length=m, volume=L, area=m2, speed=m/s.
// Temperature is special-cased exactly like the source (base = Celsius).
// ---------------------------------------------------------------------------

data class UcUnit(val id: String, val name: String, val short: String, val factor: Double = 1.0)
data class UcCategory(val id: String, val label: String, val special: String? = null, val units: List<UcUnit>)

val UC_DEFS: List<UcCategory> = listOf(
    UcCategory("weight", "Weight", units = listOf(
        UcUnit("mg", "Milligram", "mg", 1e-6),
        UcUnit("g", "Gram", "g", 1e-3),
        UcUnit("kg", "Kilogram", "kg", 1.0),
        UcUnit("oz", "Ounce", "oz", 0.028349523125),
        UcUnit("lb", "Pound", "lb", 0.45359237),
        UcUnit("t", "Metric ton", "t", 1000.0)
    )),
    UcCategory("temperature", "Temperature", special = "temp", units = listOf(
        UcUnit("c", "Celsius", "°C"),
        UcUnit("f", "Fahrenheit", "°F"),
        UcUnit("k", "Kelvin", "K")
    )),
    UcCategory("length", "Length", units = listOf(
        UcUnit("mm", "Millimeter", "mm", 0.001),
        UcUnit("cm", "Centimeter", "cm", 0.01),
        UcUnit("m", "Meter", "m", 1.0),
        UcUnit("km", "Kilometer", "km", 1000.0),
        UcUnit("in", "Inch", "in", 0.0254),
        UcUnit("ft", "Foot", "ft", 0.3048),
        UcUnit("yd", "Yard", "yd", 0.9144),
        UcUnit("mi", "Mile", "mi", 1609.344)
    )),
    UcCategory("volume", "Volume", units = listOf(
        UcUnit("ml", "Milliliter", "ml", 0.001),
        UcUnit("l", "Liter", "L", 1.0),
        UcUnit("tsp", "Teaspoon (US)", "tsp", 0.00492892159375),
        UcUnit("tbsp", "Tablespoon (US)", "tbsp", 0.01478676478125),
        UcUnit("floz", "Fluid ounce (US)", "fl oz", 0.0295735295625),
        UcUnit("cup", "Cup (US)", "cup", 0.2365882365),
        UcUnit("pt", "Pint (US)", "pt", 0.473176473),
        UcUnit("qt", "Quart (US)", "qt", 0.946352946),
        UcUnit("gal", "Gallon (US)", "gal", 3.785411784)
    )),
    UcCategory("area", "Area", units = listOf(
        UcUnit("cm2", "Sq centimeter", "cm²", 0.0001),
        UcUnit("m2", "Sq meter", "m²", 1.0),
        UcUnit("ha", "Hectare", "ha", 10000.0),
        UcUnit("km2", "Sq kilometer", "km²", 1e6),
        UcUnit("in2", "Sq inch", "in²", 0.00064516),
        UcUnit("ft2", "Sq foot", "ft²", 0.09290304),
        UcUnit("yd2", "Sq yard", "yd²", 0.83612736),
        UcUnit("ac", "Acre", "ac", 4046.8564224),
        UcUnit("mi2", "Sq mile", "mi²", 2589988.110336)
    )),
    UcCategory("speed", "Speed", units = listOf(
        UcUnit("ms", "Meters/sec", "m/s", 1.0),
        UcUnit("kmh", "Kilometers/hr", "km/h", 1 / 3.6),
        UcUnit("mph", "Miles/hr", "mph", 0.44704),
        UcUnit("fts", "Feet/sec", "ft/s", 0.3048),
        UcUnit("kt", "Knots", "kt", 0.514444)
    ))
)

/** Source: ucToBase() — temperature converts to Celsius, others value * factor. */
fun ucToBase(cat: UcCategory, unitId: String, v: Double): Double {
    if (cat.special == "temp") {
        return when (unitId) {
            "f" -> (v - 32) * 5 / 9
            "k" -> v - 273.15
            else -> v
        }
    }
    val u = cat.units.find { it.id == unitId }
    return v * (u?.factor ?: 1.0)
}

/** Source: ucFromBase() — inverse of ucToBase(). */
fun ucFromBase(cat: UcCategory, unitId: String, b: Double): Double {
    if (cat.special == "temp") {
        return when (unitId) {
            "f" -> b * 9 / 5 + 32
            "k" -> b + 273.15
            else -> b
        }
    }
    val u = cat.units.find { it.id == unitId }
    return b / (u?.factor ?: 1.0)
}

/**
 * Source: ucFmt() — blank/invalid -> "–", 0 -> "0", otherwise
 * Number(v.toPrecision(8)).toLocaleString(max 8 fraction digits).
 * Native equivalent: round to 8 significant digits, then format trimming
 * trailing zeros with up to 8 fraction digits, with thousands separators.
 */
fun ucFmt(v: Double?): String {
    if (v == null || v.isNaN() || v.isInfinite()) return "–"
    if (v == 0.0) return "0"
    // toPrecision(8): round to 8 significant figures
    val rounded = java.math.BigDecimal(v).round(java.math.MathContext(8)).toDouble()
    val fmt = java.text.DecimalFormat("#,##0.########", java.text.DecimalFormatSymbols(java.util.Locale.US))
    return fmt.format(rounded)
}

// ---------------------------------------------------------------------------
// Shopping Assistant unit conversions — UNIT_CONVERSIONS / UNIT_OPTIONS
// from the source HTML, factors verbatim. base = display unit for "per" price.
// ---------------------------------------------------------------------------

data class ShopUnitInfo(val dim: String, val factor: Double, val base: String)

val UNIT_CONVERSIONS: Map<String, ShopUnitInfo> = linkedMapOf(
    "oz" to ShopUnitInfo("weight", 1.0, "oz"),
    "lb" to ShopUnitInfo("weight", 16.0, "oz"),
    "g" to ShopUnitInfo("weight", 0.0352739619, "oz"),
    "kg" to ShopUnitInfo("weight", 35.2739619, "oz"),
    "fl oz" to ShopUnitInfo("volume", 1.0, "fl oz"),
    "ml" to ShopUnitInfo("volume", 0.0338140227, "fl oz"),
    "L" to ShopUnitInfo("volume", 33.8140227, "fl oz"),
    "tsp" to ShopUnitInfo("volume", 1.0 / 6, "fl oz"),
    "tbsp" to ShopUnitInfo("volume", 0.5, "fl oz"),
    "cup" to ShopUnitInfo("volume", 8.0, "fl oz"),
    "pt" to ShopUnitInfo("volume", 16.0, "fl oz"),
    "qt" to ShopUnitInfo("volume", 32.0, "fl oz"),
    "gal" to ShopUnitInfo("volume", 128.0, "fl oz"),
    "ct" to ShopUnitInfo("count", 1.0, "ct"),
    "item" to ShopUnitInfo("count", 1.0, "ct"),
    "dozen" to ShopUnitInfo("count", 12.0, "ct"),
    "pack" to ShopUnitInfo("count", 1.0, "ct"),
    "bunch" to ShopUnitInfo("count", 1.0, "ct"),
    "head" to ShopUnitInfo("count", 1.0, "ct")
)

val UNIT_OPTIONS: List<String> = listOf(
    "oz", "lb", "g", "kg", "ml", "L", "fl oz", "pt", "qt", "gal",
    "cup", "tbsp", "tsp", "item", "pack", "ct", "bunch", "head", "dozen"
)

// ---------------------------------------------------------------------------
// Accent themes — the 12 presets from the source's [data-accent] CSS
// (--accent-primary values verbatim), plus "custom" (user-picked hex).
// ---------------------------------------------------------------------------

data class AccentInfo(val id: String, val label: String, val primary: Color)

val ACCENTS: List<AccentInfo> = listOf(
    AccentInfo("indigo", "Indigo", Color(0xFF6366F1)),
    AccentInfo("blue", "Blue", Color(0xFF3B82F6)),
    AccentInfo("sky", "Sky", Color(0xFF0EA5E9)),
    AccentInfo("cyan", "Cyan", Color(0xFF06B6D4)),
    AccentInfo("teal", "Teal", Color(0xFF14B8A6)),
    AccentInfo("emerald", "Emerald", Color(0xFF10B981)),
    AccentInfo("lime", "Lime", Color(0xFF84CC16)),
    AccentInfo("violet", "Violet", Color(0xFF8B5CF6)),
    AccentInfo("pink", "Pink", Color(0xFFEC4899)),
    AccentInfo("rose", "Rose", Color(0xFFF43F5E)),
    AccentInfo("orange", "Orange", Color(0xFFF97316)),
    AccentInfo("amber", "Amber", Color(0xFFF59E0B))
)

fun accentColor(settings: AppSettings): Color {
    if (settings.accent == "custom") {
        return try {
            Color(android.graphics.Color.parseColor(settings.customAccent))
        } catch (e: Exception) {
            ACCENTS.first().primary
        }
    }
    return ACCENTS.find { it.id == settings.accent }?.primary ?: ACCENTS.first().primary
}
