### Task 2.4: PosterRenderer — 海报合成

**Files:**
- Create: `app/src/main/java/com/palettemuse/core/PosterRenderer.kt`

- [ ] **Step 1: 实现 PosterRenderer**

`app/src/main/java/com/palettemuse/core/PosterRenderer.kt`：

```kotlin
package com.palettemuse.core

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
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

    data class PosterConfig(
        val title: String = "Moodboard Color Harmony",
        val subtitle: String = "curated with Palette Muse",
        val primaryColor: Int = Color.GRAY,
        val secondaryColor: Int = Color.LTGRAY,
        val accentColor: Int = Color.DKGRAY
    )

    fun render(
        photo: Bitmap,
        config: PosterConfig,
        width: Int = 1080,
        height: Int = 1920
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // Background
        canvas.drawColor(Color.parseColor("#FDFBF7"))

        // Photo section (top 60%)
        val photoRect = RectF(40f, 40f, (width - 40).toFloat(), (height * 0.58).toFloat())
        canvas.drawBitmap(photo, null, photoRect, Paint(Paint.FILTER_BITMAP_FLAG))

        // Palette swatches bar
        val swatchHeight = 80f
        val swatchY = photoRect.bottom + 60f
        val swatchWidth = (width - 120f) / 3f

        drawSwatch(canvas, 40f, swatchY, swatchWidth, swatchHeight, config.primaryColor)
        drawSwatch(canvas, 40f + swatchWidth + 20f, swatchY, swatchWidth, swatchHeight, config.secondaryColor)
        drawSwatch(canvas, 40f + (swatchWidth + 20f) * 2, swatchY, swatchWidth, swatchHeight, config.accentColor)

        // Title
        val titlePaint = Paint().apply {
            color = Color.parseColor("#1A1A1A")
            textSize = 64f
            typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
            isAntiAlias = true
        }
        canvas.drawText(config.title, 40f, swatchY + swatchHeight + 120f, titlePaint)

        // Subtitle
        val subtitlePaint = Paint().apply {
            color = Color.parseColor("#857374")
            textSize = 32f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
            isAntiAlias = true
        }
        canvas.drawText(config.subtitle, 40f, swatchY + swatchHeight + 180f, subtitlePaint)

        return bitmap
    }

    private fun drawSwatch(canvas: Canvas, left: Float, top: Float, width: Float, height: Float, color: Int) {
        val paint = Paint().apply {
            this.color = color
            isAntiAlias = true
            setShadowLayer(12f, 0f, 4f, Color.argb(30, 0, 0, 0))
        }
        val rect = RectF(left, top, left + width, top + height)
        canvas.drawRoundRect(rect, 24f, 24f, paint)
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
```

- [ ] **Step 2: 验证编译**

```bash
./gradlew assembleDebug --no-daemon 2>&1 | tail -10
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add -A
git commit -m "feat: add PosterRenderer with Canvas poster compositing and gallery save"
```

---

## Phase 3: 相机模块

