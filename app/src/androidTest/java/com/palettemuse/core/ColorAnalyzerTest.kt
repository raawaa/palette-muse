package com.palettemuse.core

import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ColorAnalyzerTest {
    private val analyzer = ColorAnalyzer()

    @Test
    fun extractDominantHexOfSolidColor() = runTest {
        val bmp = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.parseColor("#DCA8A6"))
        }
        // Palette quantizes the input; for a solid #DCA8A6 bitmap the dominant swatch
        // resolves deterministically to #D8A8A0. Assert the quantized dominant hex.
        assertEquals("#D8A8A0", analyzer.extractDominantHex(bmp))
    }
}
