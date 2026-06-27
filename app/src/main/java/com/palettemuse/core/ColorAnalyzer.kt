package com.palettemuse.core

import android.graphics.Bitmap
import androidx.palette.graphics.Palette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ColorAnalyzer @Inject constructor() {

    data class AnalysisResult(
        val primaryHex: String?,
        val secondaryHex: String?,
        val accentHex: String?,
        val primarySwatch: Palette.Swatch?,
        val secondarySwatch: Palette.Swatch?,
        val accentSwatch: Palette.Swatch?
    )

    suspend fun analyze(bitmap: Bitmap): AnalysisResult = withContext(Dispatchers.Default) {
        val palette = Palette.from(bitmap)
            .maximumColorCount(12)
            .clearFilters()
            .resizeBitmapArea(96 * 96)
            .generate()

        AnalysisResult(
            primaryHex = palette.vibrantSwatch?.rgb?.toHex(),
            secondaryHex = palette.lightVibrantSwatch?.rgb?.toHex(),
            accentHex = palette.mutedSwatch?.rgb?.toHex(),
            primarySwatch = palette.vibrantSwatch,
            secondarySwatch = palette.lightVibrantSwatch,
            accentSwatch = palette.mutedSwatch
        )
    }

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
