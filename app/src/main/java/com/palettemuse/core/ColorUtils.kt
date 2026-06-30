package com.palettemuse.core

/**
 * 8-bit sRGB channels, 0–255 each. The pure-Kotlin replacement for
 * [android.graphics.Color]'s internal representation; see ADR-0006.
 */
data class Rgb(val r: Int, val g: Int, val b: Int)

/**
 * Hue–saturation–value derived from [Rgb] via a standard sRGB→HSV formula,
 * produced by [hsvFromRgb]. All float fields are 0–based (hue 0–360,
 * saturation/value 0–1).
 */
data class Hsv(val hue: Float, val saturation: Float, val value: Float)

/**
 * Parses `"#RRGGBB"` (or `"RRGGBB"`) into [Rgb]. Rejects anything other
 * than exactly six hex digits after the optional `#`.
 */
fun parseHexRgb(hex: String): Rgb {
    val s = if (hex.startsWith("#")) hex.drop(1) else hex
    require(s.length == 6) { "Hex must be #RRGGBB, was: $hex" }
    val v = s.toInt(16)
    return Rgb((v shr 16) and 0xFF, (v shr 8) and 0xFF, v and 0xFF)
}

/**
 * Converts 8-bit sRGB channels to HSV. The pure-Kotlin replacement for
 * [android.graphics.Color.colorToHSV]; behaviour matches the Android
 * implementation for all 6-digit hex inputs.
 */
fun hsvFromRgb(r: Int, g: Int, b: Int): Hsv {
    val rf = r / 255f
    val gf = g / 255f
    val bf = b / 255f

    val max = maxOf(rf, gf, bf)
    val min = minOf(rf, gf, bf)
    val delta = max - min

    val hue = when {
        delta == 0f -> 0f
        max == rf -> 60f * (((gf - bf) / delta) % 6f)
        max == gf -> 60f * (((bf - rf) / delta) + 2f)
        else -> 60f * (((rf - gf) / delta) + 4f)
    }.let { if (it < 0f) it + 360f else if (it >= 360f) it - 360f else it }

    val saturation = if (max == 0f) 0f else delta / max
    val value = max

    return Hsv(hue, saturation, value)
}
