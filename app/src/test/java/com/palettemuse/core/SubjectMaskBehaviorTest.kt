package com.palettemuse.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure-Kotlin JVM tests for the capture path's subject-mask behavior.
 * Tests the mask → analyzePixels → CapturedColor flow at the pixel level
 * (no Bitmap dependency), covering the scenarios the stub provider would
 * produce at the integration level.
 *
 * These tests complement the `ColorAnalyzerPixelsTest` mask tests by
 * focusing on the **capture-path integration logic** — mask applied,
 * null-mask fallback, degenerate-mask fallback, and `maskCoverage`
 * propagation through `CapturedColor`.
 */
class SubjectMaskBehaviorTest {

    // -- CapturedColor.maskCoverage -- //

    @Test
    fun `capturedColor carries maskCoverage when set`() {
        val c = CapturedColor(
            rgb = 0xFF0000,
            populationShare = 0.5,
            topVsSecondRatio = 2.0,
            maskCoverage = 0.5
        )
        assertEquals(0.5, c.maskCoverage!!, 0.001)
    }

    @Test
    fun `capturedColor maskCoverage defaults to null`() {
        val c = CapturedColor(rgb = 0xFF0000, populationShare = 0.5, topVsSecondRatio = 2.0)
        assertNull(c.maskCoverage)
    }

    // -- Mask applied: simulate stub's center-crop -- //

    @Test
    fun `stub-like mask on two-color image extracts center region color`() {
        val total = 96 * 96
        // Red background, green center
        val pixels = IntArray(total) { 0xFFFF0000.toInt() }
        val centerStart = 24 * 96 + 24
        for (y in 24 until (96 - 24)) {
            for (x in 24 until (96 - 24)) {
                pixels[y * 96 + x] = 0xFF00FF00.toInt()
            }
        }
        // Simulate a stub-like center mask
        val mask = BooleanArray(total) { false }
        for (y in 24 until (96 - 24)) {
            for (x in 24 until (96 - 24)) {
                mask[y * 96 + x] = true
            }
        }
        val result = analyzePixels(pixels, mask = mask)
        // Center is green, so dominant should be green
        assertEquals("#00FF00", result.hex)
    }

    // -- Null-mask fallback -- //

    @Test
    fun `null mask falls back to whole-photo dominant`() {
        val total = 96 * 96
        val pixels = IntArray(total) { i ->
            if (i % 2 == 0) 0xFFFF0000.toInt() else 0xFF00FF00.toInt()
        }
        val result = analyzePixels(pixels, mask = null)
        // Without mask, whole-photo analysis runs
        assertNotNull(result)
        assertTrue(result.populationShare > 0)
        assertNull(result.maskCoverage) // analyzer doesn't set maskCoverage
    }

    // -- Degenerate-mask fallback: all-false mask -- //

    @Test
    fun `all-false mask falls back to gray`() {
        val total = 96 * 96
        val pixels = IntArray(total) { 0xFFFF0000.toInt() }
        // An all-false mask is degenerate (coverage = 0.0)
        val mask = BooleanArray(total) { false }
        val result = analyzePixels(pixels, mask = mask)
        // Empty mask → empty k-means → gray fallback
        assertEquals("#808080", result.hex)
        assertEquals(0.0, result.populationShare, 0.001)
        assertEquals(0.0, result.topVsSecondRatio, 0.001)
    }

    // -- Stub-like mask coverage computation -- //

    @Test
    fun `center-crop mask coverage is approximately 25 percent`() {
        val w = 96
        val h = 96
        val total = w * h
        val mask = BooleanArray(total) { false }
        val marginX = w / 4 // 24
        val marginY = h / 4 // 24
        for (y in marginY until (h - marginY)) {
            for (x in marginX until (w - marginX)) {
                mask[y * w + x] = true
            }
        }
        val coverage = mask.count { it }.toDouble() / total
        // 48 * 48 = 2304 masked pixels out of 9216 = 25%
        assertEquals(0.25, coverage, 0.01)
    }

    // -- filterByMask helper -- //

    @Test
    fun `filterByMask keeps only masked pixels`() {
        val pixels = intArrayOf(0xFF0000, 0x00FF00, 0x0000FF, 0xFFFF00)
        val mask = booleanArrayOf(true, false, true, false)
        val result = filterByMask(pixels, mask)
        assertEquals(2, result.size)
        assertEquals(0xFF0000, result[0])
        assertEquals(0x0000FF, result[1])
    }

    @Test
    fun `filterByMask with all-true mask keeps all pixels`() {
        val pixels = intArrayOf(0xFF0000, 0x00FF00)
        val mask = booleanArrayOf(true, true)
        val result = filterByMask(pixels, mask)
        assertEquals(2, result.size)
    }

    @Test
    fun `filterByMask with all-false mask produces empty array`() {
        val pixels = intArrayOf(0xFF0000, 0x00FF00)
        val mask = booleanArrayOf(false, false)
        val result = filterByMask(pixels, mask)
        assertEquals(0, result.size)
    }
}
