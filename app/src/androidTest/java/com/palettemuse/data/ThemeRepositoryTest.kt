package com.palettemuse.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.palettemuse.data.local.AppDatabase
import com.palettemuse.data.repository.ThemeRepository
import com.palettemuse.core.ColorMatcher
import com.palettemuse.core.ColorNamer
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ThemeRepositoryTest {
    private lateinit var db: AppDatabase
    private lateinit var repo: ThemeRepository

    @Before
    fun setup() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(ctx, AppDatabase::class.java)
            .allowMainThreadQueries().build()
        repo = ThemeRepository(db.themeDao(), db.photoDao(), ColorMatcher(), ColorNamer())
    }

    @After
    fun close() = db.close()

    @Test
    fun createThemeAndSave_createsSeedPhoto() = runTest {
        val id = repo.createThemeAndSave("/seed.jpg", "#DCA8A6")
        val themes = db.themeDao().getAllThemes().first()
        assertEquals(1, themes.size)
        assertEquals(id, themes[0].id)
        val photos = db.photoDao().getPhotosForThemeOnce(id)
        assertEquals(1, photos.size)
        assertTrue(photos[0].isSeed)
    }

    @Test
    fun findMatchingTheme_returnsMatchAboveThreshold() = runTest {
        val id = repo.createThemeAndSave("/seed.jpg", "#DCA8A6")
        val matched = repo.findMatchingTheme("#DCB0A8") // 接近的枯玫瑰
        assertEquals(id, matched?.id)
    }

    @Test
    fun findMatchingTheme_returnsNullBelowThreshold() = runTest {
        repo.createThemeAndSave("/seed.jpg", "#DCA8A6")
        val matched = repo.findMatchingTheme("#00FF00") // 差异大的亮绿
        assertNull(matched)
    }

    @Test
    fun savePhotoToTheme_appendsPhoto() = runTest {
        val id = repo.createThemeAndSave("/seed.jpg", "#DCA8A6")
        repo.savePhotoToTheme(id, "/cap1.jpg", "#DCA8A6")
        repo.savePhotoToTheme(id, "/cap2.jpg", "#D5A09A")
        assertEquals(3, db.photoDao().getPhotosForThemeOnce(id).size)
    }

    @Test
    fun getAllThemesWithPhotos_buildsPalette() = runTest {
        val id = repo.createThemeAndSave("/seed.jpg", "#DCA8A6")
        repo.savePhotoToTheme(id, "/cap.jpg", "#C99A92")
        val list = repo.getAllThemesWithPhotos().first()
        assertEquals(1, list.size)
        val palette = list[0].palette
        assertEquals("#DCA8A6", palette.first()) // 代表色居首
        assertTrue(palette.size in 1..3)
    }
}
