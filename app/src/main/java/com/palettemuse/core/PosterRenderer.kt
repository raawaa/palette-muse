package com.palettemuse.core

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PosterRenderer @Inject constructor() {

    enum class TemplateType { GRID, FILM, JOURNAL, MINIMAL }

    data class PosterConfig(
        val title: String = "Moodboard Color Harmony",
        val subtitle: String = "curated with Palette Muse",
        val primaryColor: Int = Color.GRAY,
        val secondaryColor: Int = Color.LTGRAY,
        val accentColor: Int = Color.DKGRAY,
        val photos: List<Bitmap> = emptyList(),
        val template: TemplateType = TemplateType.GRID
    )

    fun render(photo: Bitmap?, config: PosterConfig, width: Int = 1080, height: Int = 1920): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)
        when (config.template) {
            TemplateType.GRID -> renderGrid(canvas, config, width, height)
            TemplateType.FILM -> renderFilm(canvas, config, width, height)
            TemplateType.JOURNAL -> renderJournal(canvas, config, width, height)
            TemplateType.MINIMAL -> renderMinimal(canvas, config, width, height)
        }
        return bitmap
    }

    private fun renderGrid(canvas: Canvas, config: PosterConfig, w: Int, h: Int) {
        // 2x2 Bento + 标题 + 色板 (Plan 2 已有, 扩展接收 config.photos)
        val photos = config.photos.take(4)
        val cellW = w / 2f
        val cellH = (h * 0.65f) / 2f
        val padding = 16f
        photos.forEachIndexed { i, bmp ->
            val row = i / 2
            val col = i % 2
            val left = col * cellW + padding
            val top = row * cellH + padding
            val right = (col + 1) * cellW - padding
            val bottom = (row + 1) * cellH - padding
            canvas.drawBitmap(bmp, null,
                RectF(left, top, right, bottom), null)
        }
        drawTitleAndPalette(canvas, config, w, h)
    }

    private fun renderFilm(canvas: Canvas, config: PosterConfig, w: Int, h: Int) {
        // 顶部大照 + 下方 3 小照横排 + 黑/白边框
        val photos = config.photos.take(4)
        if (photos.isNotEmpty()) {
            val topH = h * 0.65f
            canvas.drawBitmap(photos[0], null, RectF(0f, 0f, w.toFloat(), topH), null)
        }
        val bottomY = h * 0.7f
        val smallW = w / 3f
        val stripCount = (photos.size - 1).coerceAtMost(3).coerceAtLeast(0)
        for (i in 1..stripCount) {
            val left = (i - 1) * smallW
            canvas.drawBitmap(photos[i], null,
                RectF(left, bottomY, left + smallW, h.toFloat()), null)
        }
        drawTitleAndPalette(canvas, config, w, h)
    }

    private fun renderJournal(canvas: Canvas, config: PosterConfig, w: Int, h: Int) {
        // 散落拼贴 (1-2 主照 + 小照倾斜 + 标题) — 简化为网格+旋转
        val photos = config.photos.take(3)
        if (photos.isEmpty()) return
        val cellW = w / 2f
        val cellH = h * 0.4f
        for ((i, bmp) in photos.withIndex()) {
            val row = i / 2
            val col = i % 2
            val angle = (if (i % 2 == 0) -3f else 3f)
            canvas.save()
            canvas.rotate(angle, col * cellW + cellW / 2, row * cellH + cellH / 2)
            canvas.drawBitmap(bmp, null,
                RectF(col * cellW, row * cellH, (col + 1) * cellW, (row + 1) * cellH), null)
            canvas.restore()
        }
        drawTitleAndPalette(canvas, config, w, h)
    }

    private fun renderMinimal(canvas: Canvas, config: PosterConfig, w: Int, h: Int) {
        // 主照 3/4 + 主题名/色板底部 + 1 小图角标
        val photos = config.photos.take(2)
        if (photos.isNotEmpty()) {
            val mainH = h * 0.7f
            canvas.drawBitmap(photos[0], null, RectF(0f, 0f, w.toFloat(), mainH), null)
            if (photos.size > 1) {
                val smallSize = w * 0.2f
                canvas.drawBitmap(photos[1], null,
                    RectF(w - smallSize - 16f, mainH - smallSize - 16f, w - 16f, mainH - 16f), null)
            }
        }
        drawTitleAndPalette(canvas, config, w, h)
    }

    private fun drawTitleAndPalette(canvas: Canvas, config: PosterConfig, w: Int, h: Int) {
        // 标题 + 色板 (在底部, Plan 2 已有)
        val textPaint = Paint().apply {
            color = Color.BLACK
            textSize = 64f
            isAntiAlias = true
        }
        canvas.drawText(config.title, 40f, h * 0.95f, textPaint)
        val palette = listOf(config.primaryColor, config.secondaryColor, config.accentColor)
        val swatchSize = 40f
        val swatchY = h * 0.88f
        palette.forEachIndexed { i, c ->
            val left = 40f + i * (swatchSize + 8f)
            canvas.drawRect(left, swatchY, left + swatchSize, swatchY + swatchSize,
                Paint().apply { this.color = c; isAntiAlias = true })
        }
    }

    /**
     * 保存海报到相册并返回分享 URI
     */
    fun saveToGallery(context: Context, bitmap: Bitmap): Uri? {
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
                ?: return null

            resolver.openOutputStream(uri)?.use { outputStream ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
            }

            contentValues.clear()
            contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, contentValues, null, null)

            return uri
        } else {
            // Android 9-: 写入外部存储
            val picturesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
            val paletteDir = File(picturesDir, "PaletteMuse")
            paletteDir.mkdirs()
            val file = File(paletteDir, filename)
            FileOutputStream(file).use { fos ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos)
            }
            return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        }
    }
}
