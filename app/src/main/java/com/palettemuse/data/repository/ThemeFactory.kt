package com.palettemuse.data.repository

import com.palettemuse.data.model.PhotoEntity
import com.palettemuse.data.model.ThemeEntity
import java.util.UUID
import javax.inject.Inject

class ThemeFactory @Inject constructor() {
    fun createSeed(
        name: String,
        dominantHex: String,
        imagePath: String,
        populationShare: Double? = null,
        topVsSecondRatio: Double? = null,
        isLowConfidence: Boolean? = null
    ): Pair<ThemeEntity, PhotoEntity> {
        val themeId = UUID.randomUUID().toString()
        val theme = ThemeEntity(
            id = themeId,
            name = name,
            representativeHex = dominantHex
        )
        val photo = PhotoEntity(
            id = UUID.randomUUID().toString(),
            themeId = themeId,
            imagePath = imagePath,
            dominantHex = dominantHex,
            isSeed = true,
            populationShare = populationShare,
            topVsSecondRatio = topVsSecondRatio,
            isLowConfidence = isLowConfidence
        )
        return Pair(theme, photo)
    }

    fun createCapture(
        themeId: String,
        dominantHex: String,
        imagePath: String,
        populationShare: Double? = null,
        topVsSecondRatio: Double? = null,
        isLowConfidence: Boolean? = null
    ): PhotoEntity {
        return PhotoEntity(
            id = UUID.randomUUID().toString(),
            themeId = themeId,
            imagePath = imagePath,
            dominantHex = dominantHex,
            isSeed = false,
            populationShare = populationShare,
            topVsSecondRatio = topVsSecondRatio,
            isLowConfidence = isLowConfidence
        )
    }
}
