package com.palettemuse.camera

import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.nio.ByteBuffer
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CameraManagerTest {
    private val manager = CameraManager()

    @Test
    fun rgbaBufferToBitmap_roundTripsSolidColor() {
        val w = 8
        val h = 8
        // Build the buffer the way Bitmap emits it (copyPixelsToBuffer) — that is the
        // exact byte layout copyPixelsFromBuffer consumes — then read it back through
        // rgbaBufferToBitmap. Pinning the round trip pins the byte order independently
        // of CameraX internals (see ADR-0001).
        val source = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.parseColor("#DCA8A6"))
        }
        val buffer = ByteBuffer.allocate(w * h * 4)
        source.copyPixelsToBuffer(buffer)
        buffer.rewind()

        val out = manager.rgbaBufferToBitmap(buffer, w, h)
        assertEquals(source.getPixel(0, 0), out.getPixel(0, 0))
        assertEquals(source.getPixel(w - 1, h - 1), out.getPixel(w - 1, h - 1))
    }

    @Test
    fun rgbaBufferToBitmap_roundTripsDistinctPixels() {
        val w = 2
        val h = 1
        val source = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.RED)
            setPixel(1, 0, Color.BLUE)
        }
        val buffer = ByteBuffer.allocate(w * h * 4)
        source.copyPixelsToBuffer(buffer)
        buffer.rewind()

        val out = manager.rgbaBufferToBitmap(buffer, w, h)
        assertEquals(Color.RED, out.getPixel(0, 0))
        assertEquals(Color.BLUE, out.getPixel(1, 0))
    }
}
