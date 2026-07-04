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
}
