### Task 2.3: ColorMatcher — 实时色彩匹配

**Files:**
- Create: `app/src/main/java/com/palettemuse/core/ColorMatcher.kt`

- [ ] **Step 1: 实现 ColorMatcher**

`app/src/main/java/com/palettemuse/core/ColorMatcher.kt`：

```kotlin
package com.palettemuse.core

import android.graphics.Color
import kotlin.math.abs
import kotlin.math.sqrt
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ColorMatcher @Inject constructor() {

    /**
     * 计算 CIELAB 色差 ΔE 并转为匹配百分比
     */
    fun matchPercentage(targetHex: String, sampleHex: String): Int {
        val targetRgb = Color.parseColor(targetHex)
        val sampleRgb = Color.parseColor(sampleHex)

        val targetLab = rgbToLab(targetRgb)
        val sampleLab = rgbToLab(sampleRgb)

        val deltaE = sqrt(
            (targetLab[0] - sampleLab[0]).let { it * it } +
            (targetLab[1] - sampleLab[1]).let { it * it } +
            (targetLab[2] - sampleLab[2]).let { it * it }
        )

        return (100.0 - deltaE * 2.5).toInt().coerceIn(0, 100)
    }

    /**
     * 从 Bitmap 中心区域提取平均色
     */
    fun extractCenterAverageColor(pixels: IntArray, width: Int, height: Int): String {
        val centerX = width / 2
        val centerY = height / 2
        val sampleSize = minOf(width, height) / 4

        var r = 0L
        var g = 0L
        var b = 0L
        var count = 0

        for (y in (centerY - sampleSize) until (centerY + sampleSize)) {
            for (x in (centerX - sampleSize) until (centerX + sampleSize)) {
                if (y in 0 until height && x in 0 until width) {
                    val pixel = pixels[y * width + x]
                    r += Color.red(pixel)
                    g += Color.green(pixel)
                    b += Color.blue(pixel)
                    count++
                }
            }
        }

        if (count == 0) return "#808080"

        val hex = "#%02X%02X%02X".format(
            (r / count).toInt(),
            (g / count).toInt(),
            (b / count).toInt()
        )
        return hex
    }

    private fun rgbToLab(rgb: Int): DoubleArray {
        val r = srgbLinearize(Color.red(rgb) / 255.0)
        val g = srgbLinearize(Color.green(rgb) / 255.0)
        val b = srgbLinearize(Color.blue(rgb) / 255.0)

        // D65 参考白点
        val x = 0.4124564 * r + 0.3575761 * g + 0.1804375 * b
        val y = 0.2126729 * r + 0.7151522 * g + 0.0721750 * b
        val z = 0.0193339 * r + 0.1191920 * g + 0.9503041 * b

        val xn = x / 0.95047
        val yn = y / 1.0
        val zn = z / 1.08883

        return doubleArrayOf(
            116.0 * labF(yn) - 16,
            500.0 * (labF(xn) - labF(yn)),
            200.0 * (labF(yn) - labF(zn))
        )
    }

    private fun srgbLinearize(c: Double): Double {
        return if (c <= 0.04045) c / 12.92
        else Math.pow((c + 0.055) / 1.055, 2.4)
    }

    private fun labF(t: Double): Double {
        return if (t > 0.008856) Math.pow(t, 1.0 / 3.0)
        else (903.3 * t + 16) / 116.0
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
git commit -m "feat: add ColorMatcher with CIELAB delta-E color matching"
```

---

