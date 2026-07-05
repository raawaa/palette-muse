package com.palettemuse.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ColorAnalyzerPixelsTest {

    @Test
    fun `pure color input returns matching captured color`() {
        // A solid red image (0xFFFF0000 pixels)
        val pixels = IntArray(96 * 96) { 0xFFFF0000.toInt() }
        val result = analyzePixels(pixels)
        // The dominant should be red (#FF0000)
        assertEquals("#FF0000", result.hex)
        assertTrue(result.populationShare > 0.99)
        assertTrue(result.topVsSecondRatio.isInfinite())
    }

    @Test
    fun `two color input produces correct share and ratio`() {
        // 75% blue (0xFF0000FF), 25% green (0xFF00FF00)
        val total = 96 * 96
        val blueCount = (total * 0.75).toInt()
        val pixels = IntArray(total) { i ->
            if (i < blueCount) 0xFF0000FF.toInt() else 0xFF00FF00.toInt()
        }
        val result = analyzePixels(pixels)
        // Dominant should be blue
        assertEquals("#0000FF", result.hex)
        // Blue family should have ~75% share
        assertTrue(result.populationShare >= 0.70)
    }

    @Test
    fun `empty int array falls back to gray`() {
        val pixels = IntArray(0)
        val result = analyzePixels(pixels)
        assertEquals("#808080", result.hex)
        assertEquals(0.0, result.populationShare, 0.001)
        assertEquals(0.0, result.topVsSecondRatio, 0.001)
    }

    // -- mask parameter tests --

    @Test
    fun `null mask preserves whole-photo behavior`() {
        val pixels = IntArray(96 * 96) { 0xFFFF0000.toInt() }
        val withoutMask = analyzePixels(pixels)
        val withNullMask = analyzePixels(pixels, mask = null)
        assertEquals(withoutMask, withNullMask)
    }

    @Test
    fun `all-true mask produces same result as null mask`() {
        val total = 96 * 96
        val pixels = IntArray(total) { if (it % 2 == 0) 0xFFFF0000.toInt() else 0xFF00FF00.toInt() }
        val mask = BooleanArray(total) { true }
        val withNull = analyzePixels(pixels)
        val withAllTrue = analyzePixels(pixels, mask = mask)
        assertEquals(withNull.hex, withAllTrue.hex)
    }

    @Test
    fun `all-false mask falls back to gray`() {
        val total = 96 * 96
        val pixels = IntArray(total) { 0xFFFF0000.toInt() }
        val mask = BooleanArray(total) { false }
        val result = analyzePixels(pixels, mask = mask)
        assertEquals("#808080", result.hex)
        assertEquals(0.0, result.populationShare, 0.001)
        assertEquals(0.0, result.topVsSecondRatio, 0.001)
    }

    @Test
    fun `mask to one region of two-color image uses only that region`() {
        val total = 96 * 96
        val split = total / 2
        // First half red, second half green
        val pixels = IntArray(total) { i ->
            if (i < split) 0xFFFF0000.toInt() else 0xFF00FF00.toInt()
        }
        // Mask only the green region
        val mask = BooleanArray(total) { i -> i >= split }
        val result = analyzePixels(pixels, mask = mask)
        // Dominant should be green (only green pixels analyzed)
        assertEquals("#00FF00", result.hex)
        assertTrue(result.populationShare > 0.99)
    }

    @Test
    fun `mask with partial region extracts dominant from that region`() {
        val total = 96 * 96
        // 70% blue, 20% green, 10% red
        val blueEnd = (total * 0.7).toInt()
        val greenEnd = (total * 0.9).toInt()
        val pixels = IntArray(total) { i ->
            when {
                i < blueEnd -> 0xFF0000FF.toInt()
                i < greenEnd -> 0xFF00FF00.toInt()
                else -> 0xFFFF0000.toInt()
            }
        }
        // Mask to only the red pixels (the smallest region)
        val mask = BooleanArray(total) { i -> i >= greenEnd }
        val result = analyzePixels(pixels, mask = mask)
        // Dominant should be red within the masked region
        assertEquals("#FF0000", result.hex)
        assertTrue(result.populationShare > 0.99)
    }
}
