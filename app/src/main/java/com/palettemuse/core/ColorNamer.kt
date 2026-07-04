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

    // ── Chinese hue ranges (same thresholds) ────────────────────────────
    private val hueRangesChinese = listOf(
        HueRange(0f, 15f, "红"),
        HueRange(15f, 45f, "橙"),
        HueRange(45f, 75f, "黄"),
        HueRange(75f, 165f, "绿"),
        HueRange(165f, 255f, "蓝"),
        HueRange(255f, 345f, "紫"),
        HueRange(345f, 360f, "红"),
    )

    private val moodWordsChinese = mapOf(
        "红" to listOf("暖", "深", "柔", "复古", "淡", "浓郁", "暗"),
        "橙" to listOf("温暖", "柔", "秋日", "夕阳", "琥珀", "浅", "明亮"),
        "黄" to listOf("淡雅", "暖", "柔和", "明亮", "奶油", "蜂蜜", "阳光"),
        "绿" to listOf("清新", "自然", "橄榄", "森林", "薄荷", "翠", "松"),
        "蓝" to listOf("静谧", "钢", "雾", "石板", "海洋", "牛仔", "天空"),
        "紫" to listOf("淡", "柔", "薰衣草", "紫罗兰", "梅子", "丁香", "石楠"),
    )

    private val baseNamesChinese = listOf(
        "沙", "雾", "石", "泥", "光", "影", "露", "烟", "云", "月", "霞", "霜", "尘"
    )

    // ── Public API ─────────────────────────────────────────────────────

    fun nameColor(rgb: Int, locale: String = "zh"): String {
        val r = (rgb shr 16) and 0xFF
        val g = (rgb shr 8) and 0xFF
        val b = rgb and 0xFF
        val hsv = hsvFromRgb(r, g, b)
        return if (locale == "zh") {
            nameColorChinese(hsv)
        } else {
            nameColorEnglish(hsv)
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

    private fun nameColorChinese(hsv: Hsv): String {
        val hueName = hueRangesChinese.first { hsv.hue in it.start..it.end }.name
        val moodList = moodWordsChinese[hueName] ?: moodWordsChinese.values.flatten()
        val mood = moodList[(hsv.hue.toInt() + hsv.saturation.toInt()) % moodList.size]

        val baseName = if (hsv.value < 0.3f || hsv.saturation < 0.15f) {
            baseNamesChinese[(hsv.hue.toInt() + (hsv.saturation * 10).toInt()) % baseNamesChinese.size]
        } else {
            hueName
        }

        return "$mood$baseName"
    }
}
