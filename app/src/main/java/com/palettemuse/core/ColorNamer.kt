package com.palettemuse.core

import android.graphics.Color
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ColorNamer @Inject constructor() {

    private data class HueRange(val start: Float, val end: Float, val name: String)
    private data class MoodWord(val name: String)

    private val hueRanges = listOf(
        HueRange(0f, 15f, "Red"),
        HueRange(15f, 45f, "Orange"),
        HueRange(45f, 75f, "Yellow"),
        HueRange(75f, 165f, "Green"),
        HueRange(165f, 255f, "Blue"),
        HueRange(255f, 345f, "Purple"),
        HueRange(345f, 360f, "Red"),
    )

    private val moodWords = mapOf(
        "Red" to listOf("Dusty", "Crimson", "Warm", "Ruby", "Faded", "Deep", "Vintage"),
        "Orange" to listOf("Golden", "Warm", "Amber", "Sunset", "Terracotta", "Copper", "Sandy"),
        "Yellow" to listOf("Pale", "Warm", "Golden", "Soft", "Butter", "Honey", "Sunlit"),
        "Green" to listOf("Sage", "Moss", "Olive", "Forest", "Mint", "Emerald", "Pine"),
        "Blue" to listOf("Urban", "Steel", "Misty", "Slate", "Ocean", "Denim", "Sky"),
        "Purple" to listOf("Dusty", "Soft", "Lavender", "Mauve", "Plum", "Lilac", "Heather"),
    )

    private val baseNames = listOf(
        "Sand", "Mist", "Stone", "Clay", "Dawn", "Dusk", "Fog",
        "Shadow", "Bloom", "Petal", "Ash", "Smoke", "Cloud", "Slate"
    )

    fun nameColor(hexColor: String): String {
        val rgb = Color.parseColor(hexColor)
        val hue = FloatArray(3).also { Color.colorToHSV(rgb, it) }[0]
        val saturation = FloatArray(3).also { Color.colorToHSV(rgb, it) }[1]
        val value = FloatArray(3).also { Color.colorToHSV(rgb, it) }[2]

        val hueName = hueRanges.first { hue in it.start..it.end }.name
        val moodList = moodWords[hueName] ?: moodWords.values.flatten()
        val mood = moodList[(hue.toInt() + saturation.toInt()) % moodList.size]

        val baseName = if (value < 0.3f || saturation < 0.15f) {
            baseNames[(hue.toInt() + (saturation * 10).toInt()) % baseNames.size]
        } else {
            hueName
        }

        return "$mood $baseName"
    }
}
