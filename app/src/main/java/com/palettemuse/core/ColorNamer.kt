package com.palettemuse.core

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ColorNamer @Inject constructor() {

    private data class HueRange(val start: Float, val end: Float, val name: String)

    // ── English hue ranges ──────────────────────────────────────────────
    private val hueRanges = listOf(
        HueRange(0f, 15f, "Red"),
        HueRange(15f, 45f, "Orange"),
        HueRange(45f, 75f, "Yellow"),
        HueRange(75f, 165f, "Green"),
        HueRange(165f, 255f, "Blue"),
        HueRange(255f, 315f, "Purple"),
        HueRange(315f, 345f, "Pink"),
        HueRange(345f, 360f, "Red"),
    )

    private val moodWords = mapOf(
        "Red" to listOf("Dusty", "Crimson", "Warm", "Ruby", "Faded", "Deep", "Vintage"),
        "Orange" to listOf("Golden", "Warm", "Amber", "Sunset", "Terracotta", "Copper", "Sandy"),
        "Yellow" to listOf("Pale", "Warm", "Golden", "Soft", "Butter", "Honey", "Sunlit"),
        "Green" to listOf("Sage", "Moss", "Olive", "Forest", "Mint", "Emerald", "Pine"),
        "Blue" to listOf("Urban", "Steel", "Misty", "Slate", "Ocean", "Denim", "Sky"),
        "Purple" to listOf("Dusty", "Soft", "Lavender", "Mauve", "Plum", "Lilac", "Heather"),
        "Pink" to listOf("Rose", "Blush", "Coral", "Flamingo", "Fuchsia", "Cotton", "Petal"),
    )

    private val baseNames = listOf(
        "Sand", "Mist", "Stone", "Clay", "Dawn", "Dusk", "Fog",
        "Shadow", "Bloom", "Petal", "Ash", "Smoke", "Cloud", "Slate"
    )

    // ── Public API ─────────────────────────────────────────────────────

    /**
     * @param rgb 24-bit RGB colour (0xRRGGBB)
     * @param locale `"zh"` returns the nearest traditional Chinese colour name
     *               (from the 526-colour zhongguose.com dataset via CIELAB ΔE);
     *               `"en"` uses the mood+baseName system.
     */
    fun nameColor(rgb: Int, locale: String = "zh"): String {
        return if (locale == "zh") {
            nearestZhongguoseName(rgb)
        } else {
            val r = (rgb shr 16) and 0xFF
            val g = (rgb shr 8) and 0xFF
            val b = rgb and 0xFF
            nameColorEnglish(hsvFromRgb(r, g, b))
        }
    }

    private fun nameColorEnglish(hsv: Hsv): String {
        val hueName = hueRanges.first { hsv.hue in it.start..it.end }.name
        val moodList = moodWords[hueName] ?: moodWords.values.flatten()
        val mood = moodList[(hsv.hue.toInt() + hsv.saturation.toInt()) % moodList.size]

        val baseName = if (hsv.value < 0.3f || hsv.saturation < 0.15f) {
            baseNames[(hsv.hue.toInt() + (hsv.saturation * 10).toInt()) % baseNames.size]
        } else {
            hueName
        }

        return "$mood $baseName"
    }

    // Finds the closest colour from the zhongguose.com dataset using CIELAB ΔE
    // (perceptual colour distance) and returns its traditional Chinese name.
    private fun nearestZhongguoseName(rgb: Int): String {
        val inputRgb = rgbFromInt(rgb)
        val inputLab = rgbToLab(inputRgb)
        return zhongguoseColors
            .minBy { it.deltaE(inputLab[0], inputLab[1], inputLab[2]) }
            .name
    }
}
