package com.palettemuse.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ColorNamerTest {

    private val namer = ColorNamer()

    @Test
    fun returns_two_words() {
        val parts = namer.nameColor(0xFF0000).split(" ")
        assertEquals("Expected \"<mood> <base>\"", 2, parts.size)
    }

    @Test
    fun red_hue_classified() {
        assertTrue(namer.nameColor(0xFF0000).endsWith("Red"))
    }

    @Test
    fun orange_hue_classified() {
        assertTrue(namer.nameColor(0xFF7F00).endsWith("Orange"))
    }

    @Test
    fun yellow_hue_classified() {
        assertTrue(namer.nameColor(0xFFD700).endsWith("Yellow"))
    }

    @Test
    fun green_hue_classified() {
        assertTrue(namer.nameColor(0x00FF00).endsWith("Green"))
    }

    @Test
    fun blue_hue_classified() {
        assertTrue(namer.nameColor(0x0000FF).endsWith("Blue"))
    }

    @Test
    fun purple_hue_classified() {
        assertTrue(namer.nameColor(0x800080).endsWith("Purple"))
    }

    @Test
    fun red_orange_boundary_first_range_wins() {
        // hueRanges: Red 0..15 (comes first), Orange 15..45
        // At hue=15 both ranges match; .first wins → "Red"
        assertTrue(namer.nameColor(0xFF3F00).endsWith("Red"))
    }

    @Test
    fun purple_red_boundary_first_range_wins() {
        // hueRanges: Purple 255..345 (comes first), Red 345..360
        // At hue~345 both ranges match; .first wins → "Purple"
        assertTrue(namer.nameColor(0xFF0040).endsWith("Purple"))
    }

    @Test
    fun lowValue_only_uses_baseName() {
        // #3C0A0A: R=60,G=10,B=10 → value≈0.235 (<0.3), saturation≈0.83 (≥0.15)
        // Only the low-value branch fires, not low-saturation.
        val parts = namer.nameColor(0x3C0A0A).split(" ")
        assertNotEquals("Red", parts[1])
    }

    @Test
    fun lowSaturation_uses_baseName() {
        val parts = namer.nameColor(0x808080).split(" ")
        assertNotEquals("Red", parts[1])
    }

    @Test
    fun saturated_bright_uses_hue_name_as_base() {
        val parts = namer.nameColor(0xFF0000).split(" ")
        assertEquals("Red", parts[1])
    }
}
