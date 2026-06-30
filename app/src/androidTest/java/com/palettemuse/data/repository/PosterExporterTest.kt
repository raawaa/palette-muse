package com.palettemuse.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PosterExporterTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val exporter = BitmapStorage(context)

    @Test
    fun cacheForShare_writesPngAndReturnsContentUri() = runTest {
        val bmp = Bitmap.createBitmap(50, 50, Bitmap.Config.ARGB_8888)
        val uri = exporter.cacheForShare(bmp)
        val file = File(context.cacheDir, "poster_share.png")
        assertTrue(file.exists() && file.length() > 0)
        assertTrue(uri.toString().startsWith("content://"))
    }

    @Test
    fun saveToGallery_returnsUriOnQPlus() = runTest {
        val bmp = Bitmap.createBitmap(50, 50, Bitmap.Config.ARGB_8888)
        val uri = exporter.saveToGallery(bmp)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            assertNotNull(uri) // MediaStore insert succeeds on the Q+ emulator
        }
    }
}
