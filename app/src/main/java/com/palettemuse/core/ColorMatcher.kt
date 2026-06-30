package com.palettemuse.core

import kotlin.math.sqrt
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ColorMatcher @Inject constructor() {

    /**
     * Computes the CIELAB ΔE between [targetHex] and [sampleHex] and returns a
     * 0–100 match percentage. Pure — no Android framework calls; see ADR 0006.
     */
    fun matchPercentage(targetHex: String, sampleHex: String): Int {
        val targetRgb = parseHexRgb(targetHex)
        val sampleRgb = parseHexRgb(sampleHex)

        val targetLab = rgbToLab(targetRgb)
        val sampleLab = rgbToLab(sampleRgb)

        val deltaE = sqrt(
            (targetLab[0] - sampleLab[0]).let { it * it } +
            (targetLab[1] - sampleLab[1]).let { it * it } +
            (targetLab[2] - sampleLab[2]).let { it * it }
        )

        return (100.0 - deltaE * 2.5).toInt().coerceIn(0, 100)
    }

    private fun rgbToLab(rgb: Rgb): DoubleArray {
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

    private fun srgbLinearize(c: Double): Double {
        return if (c <= 0.04045) c / 12.92
        else Math.pow((c + 0.055) / 1.055, 2.4)
    }

    private fun labF(t: Double): Double {
        return if (t > 0.008856) Math.pow(t, 1.0 / 3.0)
        else (903.3 * t + 16) / 116.0
    }
}
