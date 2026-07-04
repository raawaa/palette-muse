package com.palettemuse.core

import kotlin.math.sqrt

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

/**
 * Unpacks a 24-bit RGB-packed `Int` (`0xRRGGBB`, the form [ColorSwatch.rgb]
 * and k-means centroids carry) into an [Rgb]. The Int→[Rgb] counterpart of
 * [parseHexRgb]; kept here so the ΔE pipeline below never touches hex strings.
 */
internal fun rgbFromInt(rgb: Int): Rgb =
    Rgb((rgb shr 16) and 0xFF, (rgb shr 8) and 0xFF, rgb and 0xFF)

/**
 * sRGB → CIELAB with a D65 reference white. Pure — no Android framework calls
 * (ADR-0006). The single source of truth for the Lab transform; shared by
 * [deltaE] (perceptual color distance) and formerly inlined inside
 * `ColorMatcher`. Lifted to a top-level `internal` function so both color
 * matching and the ADR-0020 perceptual-family merge call one implementation
 * (mirrors ADR-0002's top-level-fn testability pattern).
 */
internal fun rgbToLab(rgb: Rgb): DoubleArray {
    val r = srgbLinearize(rgb.r / 255.0)
    val g = srgbLinearize(rgb.g / 255.0)
    val b = srgbLinearize(rgb.b / 255.0)

    // D65 参考白点
    val x = 0.4124564 * r + 0.3575761 * g + 0.1804375 * b
    val y = 0.2126729 * r + 0.7151522 * g + 0.0721750 * b
    val z = 0.0193339 * r + 0.1191920 * g + 0.9503041 * b

    val xn = x / 0.95047
    val yn = y / 1.0
    val zn = z / 1.08883

    return doubleArrayOf(
        116.0 * labF(yn) - 16,
        500.0 * (labF(xn) - labF(yn)),
        200.0 * (labF(yn) - labF(zn))
    )
}

private fun srgbLinearize(c: Double): Double =
    if (c <= 0.04045) c / 12.92
    else Math.pow((c + 0.055) / 1.055, 2.4)

private fun labF(t: Double): Double =
    if (t > 0.008856) Math.pow(t, 1.0 / 3.0)
    else (903.3 * t + 16) / 116.0

/**
 * CIELAB ΔE (Euclidean distance in Lab space) between two sRGB colors. The
 * single source of truth for perceptual color distance — shared by
 * [ColorMatcher.matchPercentage] (theme color matching) and the perceptual
 * family merge that backs the captured-color confidence signals (ADR-0020).
 * See ADR-0006 (pure-Kotlin core).
 */
internal fun deltaE(a: Rgb, b: Rgb): Double {
    val la = rgbToLab(a)
    val lb = rgbToLab(b)
    return sqrt(
        (la[0] - lb[0]).let { it * it } +
            (la[1] - lb[1]).let { it * it } +
            (la[2] - lb[2]).let { it * it }
    )
}
