package com.palettemuse.core

import android.graphics.Bitmap
import androidx.palette.graphics.Palette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ColorAnalyzer @Inject constructor() {

    /**
     * Returns the dominant color of [bitmap] as a `#RRGGBB` hex string (defaults to
     * gray when Palette finds no dominant swatch). Palette quantization runs off the
     * calling thread.
     */
    suspend fun extractDominantHex(bitmap: Bitmap): String = withContext(Dispatchers.Default) {
        val palette = Palette.from(bitmap)
            .maximumColorCount(12)
            .clearFilters()
            .resizeBitmapArea(96 * 96)
            .generate()
        palette.dominantSwatch?.rgb?.toHex() ?: "#808080"
    }

    private fun Int.toHex(): String {
        return "#%06X".format(this and 0xFFFFFF)
    }
}
