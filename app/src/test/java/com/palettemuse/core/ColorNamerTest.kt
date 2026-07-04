package com.palettemuse.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ColorNamerTest {

    private val namer = ColorNamer()
    private val knownZhongguoseNames = zhongguoseColors.map { it.name }

    // ── English tests ─────────────────────────────────────────────────

    @Test
    fun returns_two_words_en() {
        val parts = namer.nameColor(0xFF0000, "en").split(" ")
        assertEquals("Expected \"<mood> <base>\"", 2, parts.size)
    }

    @Test
    fun red_hue_classified_en() {
        assertTrue(namer.nameColor(0xFF0000, "en").endsWith("Red"))
    }

    @Test
    fun orange_hue_classified_en() {
        assertTrue(namer.nameColor(0xFF7F00, "en").endsWith("Orange"))
    }

    @Test
    fun yellow_hue_classified_en() {
        assertTrue(namer.nameColor(0xFFD700, "en").endsWith("Yellow"))
    }

    @Test
    fun green_hue_classified_en() {
        assertTrue(namer.nameColor(0x00FF00, "en").endsWith("Green"))
    }

    @Test
    fun blue_hue_classified_en() {
        assertTrue(namer.nameColor(0x0000FF, "en").endsWith("Blue"))
    }

    @Test
    fun purple_hue_classified_en() {
        assertTrue(namer.nameColor(0x800080, "en").endsWith("Purple"))
    }

    @Test
    fun pink_hue_classified_en() {
        assertTrue(namer.nameColor(0x90295F, "en").endsWith("Pink"))
    }

    @Test
    fun red_orange_boundary_first_range_wins_en() {
        assertTrue(namer.nameColor(0xFF3F00, "en").endsWith("Red"))
    }

    @Test
    fun pink_red_boundary_first_range_wins_en() {
        // Pink 315..345 comes before Red 345..360; hue ~345 → Pink
        assertTrue(namer.nameColor(0xFF0040, "en").endsWith("Pink"))
    }

    @Test
    fun lowValue_only_uses_baseName_en() {
        val parts = namer.nameColor(0x3C0A0A, "en").split(" ")
        assertNotEquals("Red", parts[1])
    }

    @Test
    fun lowSaturation_uses_baseName_en() {
        val parts = namer.nameColor(0x808080, "en").split(" ")
        assertNotEquals("Red", parts[1])
    }

    @Test
    fun saturated_bright_uses_hue_name_as_base_en() {
        val parts = namer.nameColor(0xFF0000, "en").split(" ")
        assertEquals("Red", parts[1])
    }

    // ── Chinese (zhongguose) tests ────────────────────────────────────
    //
    // For "zh" locale, ColorNamer returns the nearest traditional Chinese
    // colour name from the 526-colour zhongguose.com dataset via CIELAB ΔE,
    // completely bypassing the mood+baseName system.

    @Test
    fun default_locale_is_zh_and_returns_zhongguose_name() {
        val name = namer.nameColor(0xFF0000)
        // Default is Chinese, result should be a known zhongguose name
        assertTrue("'$name' should be a zhongguose colour", knownZhongguoseNames.contains(name))
        // Chinese zhongguose names never contain spaces
        assertFalse("'$name' should not contain spaces", name.contains(" "))
    }

    @Test
    fun red_rgb_returns_zhongguose_red_name() {
        // Pure red #FF0000 should map to a red-toned zhongguose colour
        val name = namer.nameColor(0xFF0000, "zh")
        assertTrue("'$name' should be a zhongguose colour", knownZhongguoseNames.contains(name))
    }

    @Test
    fun orange_rgb_returns_zhongguose_name() {
        val name = namer.nameColor(0xFF7F00, "zh")
        assertTrue("'$name' should be a zhongguose colour", knownZhongguoseNames.contains(name))
    }

    @Test
    fun yellow_rgb_returns_zhongguose_name() {
        val name = namer.nameColor(0xFFD700, "zh")
        assertTrue("'$name' should be a zhongguose colour", knownZhongguoseNames.contains(name))
    }

    @Test
    fun green_rgb_returns_zhongguose_name() {
        val name = namer.nameColor(0x00FF00, "zh")
        assertTrue("'$name' should be a zhongguose colour", knownZhongguoseNames.contains(name))
    }

    @Test
    fun blue_rgb_returns_zhongguose_name() {
        val name = namer.nameColor(0x0000FF, "zh")
        assertTrue("'$name' should be a zhongguose colour", knownZhongguoseNames.contains(name))
    }

    @Test
    fun purple_rgb_returns_zhongguose_name() {
        val name = namer.nameColor(0x800080, "zh")
        assertTrue("'$name' should be a zhongguose colour", knownZhongguoseNames.contains(name))
    }

    @Test
    fun chinese_zhongguose_name_has_no_spaces() {
        val name = namer.nameColor(0xFF0000, "zh")
        assertFalse("Chinese name '$name' should not contain spaces", name.contains(" "))
    }

    @Test
    fun lowSaturation_returns_zhongguose_grey_name() {
        // Grey (#808080) should map to a neutral zhongguose colour
        val name = namer.nameColor(0x808080, "zh")
        assertTrue("'$name' should be a zhongguose colour", knownZhongguoseNames.contains(name))
    }

    @Test
    fun pink_hue_returns_nearest_zhongguose_name() {
        // #90295F (H=329) — traditionally "苋菜紫" in the dataset
        val name = namer.nameColor(0x90295F, "zh")
        assertTrue("'$name' should be a zhongguose colour", knownZhongguoseNames.contains(name))
        assertFalse("Zhongguose name should not contain spaces", name.contains(" "))
    }

    @Test
    fun pink_hue_multiple_samples() {
        val samples = listOf(0x90295F, 0x9D285F, 0x8E295F, 0x782B5F)
        for (rgb in samples) {
            val name = namer.nameColor(rgb, "zh")
            assertTrue("$name (from 0x${rgb.toString(16)}) should be a zhongguose colour",
                knownZhongguoseNames.contains(name))
        }
    }

    @Test
    fun distant_non_pink_range_still_uses_mood_base_en() {
        assertTrue(namer.nameColor(0x0000FF, "en").endsWith("Blue"))
    }
}
