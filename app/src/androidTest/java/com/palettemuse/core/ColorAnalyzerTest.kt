package com.palettemuse.core

import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ColorAnalyzerTest {
    private val analyzer = ColorAnalyzer()

    @Test
    fun extractDominantHexOfSolidColor() {
        val bmp = solidBitmap("#DCA8A6")
        // Palette quantizes the input; for a solid #DCA8A6 bitmap the dominant swatch
        // resolves deterministically to #D8A8A0. Assert the quantized dominant hex.
        assertEquals("#D8A8A0", analyzer.extractDominantHex(bmp))
    }

    @Test
    fun extractDominantHexOfSolidRedIsApproximatelyRed() {
        val hex = analyzer.extractDominantHex(solidBitmap("#FF0000"))
        assertDominantCloseTo(hex, Color.parseColor("#FF0000"))
    }

    @Test
    fun extractDominantHexOfSolidGreenIsApproximatelyGreen() {
        val hex = analyzer.extractDominantHex(solidBitmap("#00FF00"))
        assertDominantCloseTo(hex, Color.parseColor("#00FF00"))
    }

    @Test
    fun extractDominantHexOfSolidGrayIsApproximatelyGray() {
        val hex = analyzer.extractDominantHex(solidBitmap("#808080"))
        assertDominantCloseTo(hex, Color.parseColor("#808080"))
    }

    private fun solidBitmap(hex: String): Bitmap =
        Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.parseColor(hex))
        }

    /**
     * Palette snaps a solid color to its quantization grid (e.g. #DCA8A6 → #D8A8A0),
     * so the exact snapped value is not stable across inputs. Assert the dominant is
     * within a small per-channel tolerance of the input instead.
     */
    private fun assertDominantCloseTo(dominantHex: String, expectedRgb: Int, tolerance: Int = 16) {
        val actual = Color.parseColor(dominantHex)
        assertTrue(
            "R out of tolerance: ${Color.red(actual)} vs ${Color.red(expectedRgb)}",
            abs(Color.red(actual) - Color.red(expectedRgb)) <= tolerance
        )
        assertTrue(
            "G out of tolerance: ${Color.green(actual)} vs ${Color.green(expectedRgb)}",
            abs(Color.green(actual) - Color.green(expectedRgb)) <= tolerance
        )
        assertTrue(
            "B out of tolerance: ${Color.blue(actual)} vs ${Color.blue(expectedRgb)}",
            abs(Color.blue(actual) - Color.blue(expectedRgb)) <= tolerance
        )
    }
}
