package com.palettemuse.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ColorNamerTest {

    private val namer = ColorNamer()

    // ── English tests ─────────────────────────────────────────────────

    @Test
    fun returns_two_words_en() {
        val parts = namer.nameColor("#FF0000", "en").split(" ")
        assertEquals("Expected \"<mood> <base>\"", 2, parts.size)
    }

    @Test
    fun red_hue_classified_en() {
        assertTrue(namer.nameColor("#FF0000", "en").endsWith("Red"))
    }

    @Test
    fun orange_hue_classified_en() {
        assertTrue(namer.nameColor("#FF7F00", "en").endsWith("Orange"))
    }

    @Test
    fun yellow_hue_classified_en() {
        assertTrue(namer.nameColor("#FFD700", "en").endsWith("Yellow"))
    }

    @Test
    fun green_hue_classified_en() {
        assertTrue(namer.nameColor("#00FF00", "en").endsWith("Green"))
    }

    @Test
    fun blue_hue_classified_en() {
        assertTrue(namer.nameColor("#0000FF", "en").endsWith("Blue"))
    }

    @Test
    fun purple_hue_classified_en() {
        assertTrue(namer.nameColor("#800080", "en").endsWith("Purple"))
    }

    @Test
    fun red_orange_boundary_first_range_wins_en() {
        // hueRanges: Red 0..15 (comes first), Orange 15..45
        // At hue=15 both ranges match; .first wins → "Red"
        assertTrue(namer.nameColor("#FF3F00", "en").endsWith("Red"))
    }

    @Test
    fun purple_red_boundary_first_range_wins_en() {
        // hueRanges: Purple 255..345 (comes first), Red 345..360
        // At hue~345 both ranges match; .first wins → "Purple"
        assertTrue(namer.nameColor("#FF0040", "en").endsWith("Purple"))
    }

    @Test
    fun lowValue_only_uses_baseName_en() {
        // #3C0A0A: R=60,G=10,B=10 → value≈0.235 (<0.3), saturation≈0.83 (≥0.15)
        // Only the low-value branch fires, not low-saturation.
        val parts = namer.nameColor("#3C0A0A", "en").split(" ")
        assertNotEquals("Red", parts[1])
    }

    @Test
    fun lowSaturation_uses_baseName_en() {
        val parts = namer.nameColor("#808080", "en").split(" ")
        assertNotEquals("Red", parts[1])
    }

    @Test
    fun saturated_bright_uses_hue_name_as_base_en() {
        val parts = namer.nameColor("#FF0000", "en").split(" ")
        assertEquals("Red", parts[1])
    }

    // ── Chinese tests ─────────────────────────────────────────────────

    @Test
    fun default_locale_is_zh() {
        val name = namer.nameColor("#FF0000")
        // Default is Chinese, so result should not contain English "Red"
        assertFalse(name.contains("Red"))
        // Should contain a Chinese result (no space between mood and base)
        assertFalse(name.contains(" "))
    }

    @Test
    fun red_hue_classified_zh() {
        // Pure red → hue name "红"
        val name = namer.nameColor("#FF0000", "zh")
        assertTrue("Expected Chinese for red, got: $name", name.endsWith("红"))
    }

    @Test
    fun orange_hue_classified_zh() {
        val name = namer.nameColor("#FF7F00", "zh")
        assertTrue("Expected Chinese for orange, got: $name", name.endsWith("橙"))
    }

    @Test
    fun yellow_hue_classified_zh() {
        val name = namer.nameColor("#FFD700", "zh")
        assertTrue("Expected Chinese for yellow, got: $name", name.endsWith("黄"))
    }

    @Test
    fun green_hue_classified_zh() {
        val name = namer.nameColor("#00FF00", "zh")
        assertTrue("Expected Chinese for green, got: $name", name.endsWith("绿"))
    }

    @Test
    fun blue_hue_classified_zh() {
        val name = namer.nameColor("#0000FF", "zh")
        assertTrue("Expected Chinese for blue, got: $name", name.endsWith("蓝"))
    }

    @Test
    fun purple_hue_classified_zh() {
        val name = namer.nameColor("#800080", "zh")
        assertTrue("Expected Chinese for purple, got: $name", name.endsWith("紫"))
    }

    @Test
    fun chinese_name_has_no_space() {
        // Chinese names concatenate mood + base without space
        val name = namer.nameColor("#FF0000", "zh")
        assertFalse("Chinese name should not contain spaces: $name", name.contains(" "))
    }

    @Test
    fun lowSaturation_uses_baseName_zh() {
        val name = namer.nameColor("#808080", "zh")
        // Low-saturation grey should use a base name (one of the Chinese base names)
        val baseNames = listOf("沙", "雾", "石", "泥", "光", "影", "露", "烟", "云", "月", "霞", "霜", "尘")
        val anyMatch = baseNames.any { name.endsWith(it) }
        assertTrue("Expected base name suffix, got: $name", anyMatch)
    }
}
