package com.palettemuse.core

import android.graphics.Bitmap
import androidx.palette.graphics.Palette
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ColorAnalyzer @Inject constructor() {

    /**
     * Returns the dominant color of [bitmap] as a `#RRGGBB` hex string (defaults to
     * gray when Palette finds no dominant swatch).
     *
     * This is the **single source of truth** for captured color — the whole-photo
     * dominant color used by both the viewfinder (`CaptureViewModel.onFrameAnalyzed`)
     * and the shutter path (`CaptureViewModel.capturePhoto`). Palette quantization
     * runs on the calling thread; each caller owns threading (see
     * `docs/adr/0001-captured-color-is-palette-dominant.md`).
     */
    fun extractDominantHex(bitmap: Bitmap): String {
        val palette = Palette.from(bitmap)
            .maximumColorCount(12)
            .clearFilters()
            .resizeBitmapArea(96 * 96)
            .generate()
        return palette.dominantSwatch?.rgb?.toHex() ?: "#808080"
    }

    private fun Int.toHex(): String {
        return "#%06X".format(this and 0xFFFFFF)
    }
}
