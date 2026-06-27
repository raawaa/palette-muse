package com.palettemuse.data.repository

import android.content.Context
import android.graphics.Bitmap
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

interface PhotoStorage {
    suspend fun save(bitmap: Bitmap): String
}

@Singleton
class InternalPhotoStorage @Inject constructor(
    @ApplicationContext private val context: Context
) : PhotoStorage {
    override suspend fun save(bitmap: Bitmap): String {
        val dir = File(context.filesDir, "captures").apply { mkdirs() }
        val file = File(dir, "capture_${System.currentTimeMillis()}.jpg")
        FileOutputStream(file).use { fos ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, fos)
        }
        return file.absolutePath
    }
}
