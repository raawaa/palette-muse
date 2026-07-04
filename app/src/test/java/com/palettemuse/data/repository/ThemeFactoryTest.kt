package com.palettemuse.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeFactoryTest {

    private val factory = ThemeFactory()

    @Test
    fun createSeed_returnsThemeWithGeneratedId() {
        val (theme, photo) = factory.createSeed("Warm Red", 0xDCA8A6, "/seed.jpg")
        assertTrue(theme.id.isNotEmpty())
        assertEquals("Warm Red", theme.name)
        assertEquals("#DCA8A6", theme.representativeHex)
    }

    @Test
    fun createSeed_returnsPhotoWithGeneratedId() {
        val (theme, photo) = factory.createSeed("Warm Red", 0xDCA8A6, "/seed.jpg")
        assertTrue(photo.id.isNotEmpty())
        assertEquals(theme.id, photo.themeId)
        assertEquals("/seed.jpg", photo.imagePath)
        assertEquals("#DCA8A6", photo.dominantHex)
    }

    @Test
    fun createSeed_photoIsSeed() {
        val (_, photo) = factory.createSeed("Warm Red", 0xDCA8A6, "/seed.jpg")
        assertTrue(photo.isSeed)
    }

    @Test
    fun createSeed_generatesDistinctUuids() {
        val (theme, photo) = factory.createSeed("Warm Red", 0xDCA8A6, "/seed.jpg")
        assertNotEquals(theme.id, photo.id)
    }

    @Test
    fun createCapture_returnsPhotoWithGeneratedId() {
        val photo = factory.createCapture("theme-1", 0xDCA8A6, "/cap.jpg")
        assertTrue(photo.id.isNotEmpty())
        assertEquals("theme-1", photo.themeId)
        assertEquals("/cap.jpg", photo.imagePath)
        assertEquals("#DCA8A6", photo.dominantHex)
    }

    @Test
    fun createCapture_photoIsNotSeed() {
        val photo = factory.createCapture("theme-1", 0xDCA8A6, "/cap.jpg")
        assertFalse(photo.isSeed)
    }

    @Test
    fun createCapture_generatesUniqueIds() {
        val a = factory.createCapture("theme-1", 0xDCA8A6, "/cap1.jpg")
        val b = factory.createCapture("theme-1", 0xDCA8A6, "/cap2.jpg")
        assertNotEquals(a.id, b.id)
    }
}
