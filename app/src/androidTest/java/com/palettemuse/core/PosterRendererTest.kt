package com.palettemuse.core

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PosterRendererTest {
    private lateinit var renderer: PosterRenderer

    @Before
    fun setup() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        renderer = PosterRenderer(ctx)
    }

    // ... existing tests remain the same

    private fun stubPhoto(color: Int, size: Int = 100): Bitmap =
        Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888).apply { eraseColor(color) }

    @Test fun render_grid_2x2_bento_produces1080x1920() {
        val photos = List(4) { stubPhoto(Color.RED) }
        val config = PosterRenderer.PosterConfig(
            title = "Test Grid",
            baseColor = Color.RED,
            template = PosterRenderer.TemplateType.GRID,
            photos = photos
        )
        val result = renderer.render(config)
        assertNotNull(result)
        assertEquals(1080, result.width)
        assertEquals(1920, result.height)
    }

    @Test fun render_film_horizontalStrip() {
        val photos = List(4) { stubPhoto(Color.BLUE) }
        val config = PosterRenderer.PosterConfig(
            title = "Test Film",
            template = PosterRenderer.TemplateType.FILM,
            photos = photos
        )
        val result = renderer.render(config)
        assertNotNull(result)
        assertEquals(1080, result.width)
    }

    @Test fun render_journal_scrapbook() {
        val photos = List(3) { stubPhoto(Color.GREEN) }
        val config = PosterRenderer.PosterConfig(
            title = "Test Journal",
            template = PosterRenderer.TemplateType.JOURNAL,
            photos = photos
        )
        val result = renderer.render(config)
        assertNotNull(result)
    }

    @Test fun render_minimal_heroPlusAccent() {
        val photos = List(2) { stubPhoto(Color.YELLOW) }
        val config = PosterRenderer.PosterConfig(
            title = "Test Minimal",
            template = PosterRenderer.TemplateType.MINIMAL,
            photos = photos
        )
        val result = renderer.render(config)
        assertNotNull(result)
    }

    @Test fun renderFilm_hasBorderStyle() {
        val photos = List(4) { stubPhoto(Color.RED) }
        val config = PosterRenderer.PosterConfig(
            title = "Test Film",
            template = PosterRenderer.TemplateType.FILM,
            photos = photos
        )
        val result = renderer.render(config)
        assertNotNull(result)
        val pixel = result.getPixel(result.width / 2, (result.height * 0.9f).toInt())
        assertEquals(0xFFF5F5F0.toInt(), pixel)
    }

    @Test fun renderJournal_backgroundIsWarmWhite() {
        val photos = List(3) { stubPhoto(Color.GREEN) }
        val config = PosterRenderer.PosterConfig(
            title = "Test Journal",
            template = PosterRenderer.TemplateType.JOURNAL,
            photos = photos
        )
        val result = renderer.render(config)
        assertNotNull(result)
        val pixel = result.getPixel(result.width / 2, (result.height * 0.9f).toInt())
        assertEquals(0xFFFAF8F5.toInt(), pixel)
    }

    @Test fun renderMinimal_backgroundIsCleanWhite() {
        val photos = List(2) { stubPhoto(Color.YELLOW) }
        val config = PosterRenderer.PosterConfig(
            title = "Test Minimal",
            template = PosterRenderer.TemplateType.MINIMAL,
            photos = photos
        )
        val result = renderer.render(config)
        assertNotNull(result)
        val pixel = result.getPixel(result.width / 2, (result.height * 0.9f).toInt())
        assertEquals(android.graphics.Color.WHITE, pixel)
    }

    @Test fun renderGrid_backgroundIsWhite() {
        val photos = List(4) { stubPhoto(Color.RED) }
        val config = PosterRenderer.PosterConfig(
            title = "Test Grid",
            template = PosterRenderer.TemplateType.GRID,
            photos = photos
        )
        val result = renderer.render(config)
        assertNotNull(result)
        val pixel = result.getPixel(result.width / 2, (result.height * 0.9f).toInt())
        assertEquals(android.graphics.Color.WHITE, pixel)
    }
}
