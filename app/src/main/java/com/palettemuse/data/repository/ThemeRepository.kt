package com.palettemuse.data.repository

import com.palettemuse.core.ColorNamer
import com.palettemuse.data.local.PhotoDao
import com.palettemuse.data.local.ThemeDao
import com.palettemuse.data.model.PhotoEntity
import com.palettemuse.data.model.ThemeEntity
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.mapLatest

data class ThemeWithPhotos(
    val theme: ThemeEntity,
    val photos: List<PhotoEntity>
)

@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class ThemeRepository @Inject constructor(
    private val themeDao: ThemeDao,
    private val photoDao: PhotoDao,
    private val colorNamer: ColorNamer,
    private val themeMatcher: ThemeMatcher,
    private val themeFactory: ThemeFactory,
    private val bitmapStorage: BitmapStorage
) {
    suspend fun findMatchingTheme(rgb: Int): ThemeEntity? =
        themeMatcher.bestMatch(themeDao.getAllThemes().first(), rgb)?.theme

    suspend fun savePhotoToTheme(
        themeId: String,
        imagePath: String,
        dominantRgb: Int,
        populationShare: Double? = null,
        topVsSecondRatio: Double? = null,
        isLowConfidence: Boolean? = null,
        maskCoverage: Double? = null
    ) {
        photoDao.insert(
            themeFactory.createCapture(
                themeId, dominantRgb, imagePath,
                populationShare, topVsSecondRatio, isLowConfidence, maskCoverage
            )
        )
        touchTheme(themeId)
    }

    suspend fun createThemeAndSave(
        imagePath: String,
        dominantRgb: Int,
        populationShare: Double? = null,
        topVsSecondRatio: Double? = null,
        isLowConfidence: Boolean? = null,
        maskCoverage: Double? = null
    ): String {
        val name = colorNamer.nameColor(dominantRgb)
        val (theme, photo) = themeFactory.createSeed(
            name, dominantRgb, imagePath,
            populationShare, topVsSecondRatio, isLowConfidence, maskCoverage
        )
        themeDao.insert(theme)
        photoDao.insert(photo)
        return theme.id
    }

    fun getAllThemesWithPhotos(): Flow<List<ThemeWithPhotos>> =
        themeDao.getAllThemes().mapLatest { themes ->
            themes.map { theme ->
                val photos = photoDao.getPhotosForThemeOnce(theme.id)
                ThemeWithPhotos(theme, photos)
            }
        }

    /**
     * Streams the raw theme entities (without photos) as they change.
     * Used by CaptureViewModel to pick the closest-matching theme per camera frame
     * without forcing a DB read on the analyzer thread.
     */
    fun getAllThemes(): Flow<List<ThemeEntity>> = themeDao.getAllThemes()

    /**
     * Loads a single theme together with its photos.
     * Used by ThemeDetailScreen to render the color dot header + Hero + masonry grid.
     * Returns null when the theme id does not exist.
     */
    suspend fun getThemeWithPhotos(id: String): ThemeWithPhotos? {
        val theme = themeDao.getTheme(id) ?: return null
        val photos = photoDao.getPhotosForThemeOnce(id)
        return ThemeWithPhotos(theme, photos)
    }

    suspend fun renameTheme(id: String, name: String) {
        themeDao.rename(id, name, System.currentTimeMillis())
    }

    suspend fun updateThemeColor(id: String, newRgb: Int) {
        themeDao.updateColor(id, newRgb, System.currentTimeMillis())
    }

    suspend fun deleteTheme(id: String) = themeDao.deleteById(id)

    suspend fun deletePhoto(photoId: String) {
        // Load the photo first to get its file path
        val photo = photoDao.getPhotoById(photoId) ?: return
        photoDao.deleteById(photoId)
        bitmapStorage.deleteCapture(photo.imagePath)
    }

    private suspend fun touchTheme(themeId: String) {
        themeDao.getTheme(themeId)?.let {
            themeDao.update(it.copy(updatedAt = System.currentTimeMillis()))
        }
    }
}
