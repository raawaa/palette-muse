### Task 1.3: 创建 Hilt Application 和基础 Theme

**Files:**
- Create: `app/src/main/java/com/palettemuse/PaletteMuseApp.kt`
- Modify: `app/src/main/java/com/palettemuse/theme/Theme.kt` — 设计系统颜色和字体
- Modify: `app/src/main/java/com/palettemuse/theme/Color.kt` — 自定义色板
- Modify: `app/src/main/java/com/palettemuse/theme/Type.kt` — Playfair Display + Plus Jakarta Sans 字体
- Modify: `app/src/main/res/values/themes.xml`

- [ ] **Step 1: 创建 Hilt Application**

`app/src/main/java/com/palettemuse/PaletteMuseApp.kt`:

```kotlin
package com.palettemuse

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class PaletteMuseApp : Application()
```

- [ ] **Step 2: 更新 Theme.kt**

编辑 `app/src/main/java/com/palettemuse/theme/Theme.kt`：

```kotlin
package com.palettemuse.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// Rose Gold / Aura Aesthetic palette
val RoseGold = Color(0xFFB76E79)
val RoseGoldDark = Color(0xFF8A4853)
val SoftLavender = Color(0xFFE6E6FA)
val PearlWhite = Color(0xFFFDFBF7)
val InkBlack = Color(0xFF1A1A1A)
val WarmGray = Color(0xFF524345)
val OffWhite = Color(0xFFFCF9F8)
val SurfaceDim = Color(0xFFDCD9D9)
val SurfaceLow = Color(0xFFF6F3F2)
val SurfaceContainer = Color(0xFFF0EDED)

private val LightColorScheme = lightColorScheme(
    primary = RoseGold,
    onPrimary = Color.White,
    primaryContainer = RoseGoldDark,
    onPrimaryContainer = Color.White,
    secondary = SoftLavender,
    onSecondary = InkBlack,
    secondaryContainer = SoftLavender,
    onSecondaryContainer = Color(0xFF626374),
    tertiary = Color(0xFF5C5C59),
    onTertiary = Color.White,
    background = PearlWhite,
    onBackground = InkBlack,
    surface = PearlWhite,
    onSurface = InkBlack,
    surfaceVariant = OffWhite,
    onSurfaceVariant = WarmGray,
    outline = Color(0xFF857374),
    outlineVariant = Color(0xFFD7C1C3),
    error = Color(0xFFBA1A1A),
    onError = Color.White,
    inverseSurface = Color(0xFF313030),
    inverseOnSurface = Color(0xFFF3F0EF),
)

@Composable
fun PaletteMuseTheme(
    content: @Composable () -> Unit
) {
    val colorScheme = LightColorScheme
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.surface.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = true
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
```

- [ ] **Step 3: 更新 Color.kt**

`app/src/main/java/com/palettemuse/theme/Color.kt` — 保留设计系统颜色常量（如上面已定义在 Theme.kt 中，Color.kt 可为空或有额外注释），确保文件存在即可：

```kotlin
package com.palettemuse.theme

// 颜色定义在 Theme.kt 中，此文件保留用于未来扩展
```

- [ ] **Step 4: 更新 Type.kt**

`app/src/main/java/com/palettemuse/theme/Type.kt`：

```kotlin
package com.palettemuse.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val Typography = Typography(
    displayLarge = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.Bold,
        fontSize = 40.sp,
        lineHeight = 48.sp,
        letterSpacing = (-0.02).sp,
    ),
    headlineLarge = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 32.sp,
        lineHeight = 40.sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 28.sp,
        lineHeight = 36.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
        lineHeight = 24.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.1.sp,
    ),
)
```

- [ ] **Step 5: 创建 styles.xml**

`app/src/main/res/values/themes.xml`：

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <style name="Theme.PaletteMuse" parent="android:Theme.Material.Light.NoActionBar" />
</resources>
```

- [ ] **Step 6: 创建间距常量化 Dimens**

`app/src/main/java/com/palettemuse/ui/theme/Dimens.kt`：

```kotlin
package com.palettemuse.ui.theme

import androidx.compose.ui.unit.dp

/**
 * Stitch Aura Aesthetic 设计系统间距与圆角常量
 * container-margin: 20px | gutter: 12px | stack: 8/16/32/64
 */
object Dimens {
    // Spacing
    val containerMargin = 20.dp
    val gutter = 12.dp
    val stackSm = 8.dp
    val stackMd = 16.dp
    val stackLg = 32.dp
    val stackXl = 64.dp

    // Shapes — Hyper-Soft (minimum 24-32px pill)
    val pillShape = 28.dp
    val cardCorner = 28.dp
    val buttonCorner = 28.dp
    val chipCorner = 20.dp
    val imageCorner = 16.dp
    val fullRound = 9999.dp
}
```

- [ ] **Step 7: 验证编译**

```bash
./gradlew assembleDebug --no-daemon 2>&1 | tail -10
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 8: Commit**

```bash
git add -A
git commit -m "feat: add Hilt Application, theme, design system colors, and spacing constants"
```

---

