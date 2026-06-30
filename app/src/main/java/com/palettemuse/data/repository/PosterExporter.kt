package com.palettemuse.data.repository

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Owns poster output: turns a rendered Bitmap into a durable or shareable Uri.
 * Aggregates PNG encode + disk/Uri 写入 (Q+ MediaStore; Android-9 FileProvider 回退).
 *
 * Both methods are suspending and offload the heavy PNG encode to [Dispatchers.IO];
 * save used to run the encode on the Main dispatcher (fixed here).
 *
 * The caller owns building the share Intent and launching the chooser — those need
 * an Activity context, which this module does not hold.
 */
@Singleton
class PosterExporter @Inject constructor(
    @ApplicationContext private val context: Context
) {

    /**
     * Writes [bitmap] to the gallery (PaletteMuse/ under Pictures); returns its Uri,
     * or null if MediaStore refused the insert.
     *
     * Preserves the Android 9- external-file fallback verbatim. NOTE: that branch's
     * FileProvider root (external Pictures dir) is not declared in `file_paths.xml`,
     * so it would throw on pre-Q — a latent bug tracked separately, not fixed here.
     */
    suspend fun saveToGallery(bitmap: Bitmap): Uri? = withContext(Dispatchers.IO) {
        val filename = "PaletteMuse_${System.currentTimeMillis()}.png"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val contentValues = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, filename)
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/PaletteMuse")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }

            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
                ?: return@withContext null

            resolver.openOutputStream(uri)?.use { outputStream ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
            }

            contentValues.clear()
            contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, contentValues, null, null)

            uri
        } else {
            // Android 9-: 写入外部存储
            val picturesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
            val paletteDir = File(picturesDir, "PaletteMuse").apply { mkdirs() }
            val file = File(paletteDir, filename)
            FileOutputStream(file).use { fos ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos)
            }
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        }
    }

    /**
     * Encodes [bitmap] to a cache PNG and returns a FileProvider Uri for
     * [Intent.ACTION_SEND]. Caller builds the Intent and launches the chooser.
     */
    suspend fun cacheForShare(bitmap: Bitmap): Uri = withContext(Dispatchers.IO) {
        val file = File(context.cacheDir, "poster_share.png")
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }
}
