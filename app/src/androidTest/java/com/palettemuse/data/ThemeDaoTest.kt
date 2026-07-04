package com.palettemuse.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.palettemuse.data.local.AppDatabase
import com.palettemuse.data.model.PhotoEntity
import com.palettemuse.data.model.ThemeEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class ThemeDaoTest {
    private lateinit var db: AppDatabase

    @Before
    fun setup() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(ctx, AppDatabase::class.java)
            .allowMainThreadQueries().build()
    }

    @After
    fun close() = db.close()

    @Test
    fun insertAndQueryTheme() = runTest {
        db.themeDao().insert(ThemeEntity(id = "t1", name = "Dusty Rose", representativeRgb = 0xDCA8A6))
        val themes = db.themeDao().getAllThemes().first()
        assertEquals(1, themes.size)
        assertEquals("Dusty Rose", themes[0].name)
    }

    @Test
    fun cascadeDeleteRemovesPhotos() = runTest {
        db.themeDao().insert(ThemeEntity(id = "t1", name = "T", representativeRgb = 0x000000))
        db.photoDao().insert(PhotoEntity(id = "p1", themeId = "t1", imagePath = "/x", dominantRgb = 0x000000))
        db.themeDao().deleteById("t1")
        assertEquals(0, db.photoDao().getPhotosForThemeOnce("t1").size)
    }
}
