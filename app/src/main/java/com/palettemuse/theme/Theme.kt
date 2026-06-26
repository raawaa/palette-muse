package com.palettemuse.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
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

// ===================================================================
// Glassmorphism
// 20px backdrop-blur + 60% white fill + 0.5px white border（降级方案）
// Android 12+ 可使用 RenderEffect.createBlurEffect() 实现真实模糊
// ===================================================================
val GlassBackground = Brush.verticalGradient(
    colors = listOf(GlassWhite, GlassWhiteLight)
)

val GlassShape = RoundedCornerShape(28.dp)

@Composable
fun PaletteMuseTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = LightColorScheme
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            // enableEdgeToEdge() in MainActivity handles transparent status bar;
            // only set icon appearance here
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = true
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
