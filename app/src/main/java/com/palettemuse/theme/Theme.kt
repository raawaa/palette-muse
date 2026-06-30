package com.palettemuse.theme

import android.app.Activity
import android.graphics.RenderEffect
import android.graphics.Shader
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat

// ===================================================================
// Aura Aesthetic — Rose Gold 色板
// ===================================================================
val RoseGold = Color(0xFFB76E79)
val RoseGoldDark = Color(0xFF8A4853)
val SoftLavender = Color(0xFFE6E6FA)
val PearlWhite = Color(0xFFFDFBF7)
val InkBlack = Color(0xFF1A1A1A)
val WarmGray = Color(0xFF524345)
val SurfaceWhite = Color(0xFFFCF9F8)
val SurfaceDim = Color(0xFFDCD9D9)
val SurfaceLow = Color(0xFFF6F3F2)
val SurfaceContainer = Color(0xFFF0EDED)

// 自定义阴影色 (玫瑰金 5-8% 透明度)
val AmbientShadow = RoseGold.copy(alpha = 0.06f)
val SpotShadow = RoseGold.copy(alpha = 0.08f)

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
    surfaceVariant = SurfaceWhite,
    onSurfaceVariant = WarmGray,
    outline = Color(0xFF857374),
    outlineVariant = Color(0xFFD7C1C3),
    error = Color(0xFFBA1A1A),
    onError = Color.White,
    inverseSurface = Color(0xFF313030),
    inverseOnSurface = Color(0xFFF3F0EF),
)

// ===================================================================
// Glassmorphic overlay for TopAppBar / BottomBar / pills
// -------------------------------------------------------------------
// Implementation note: the blur sits on a `graphicsLayer` with
// `compositingStrategy = Offscreen`, so the layer's content (including
// children drawn into it) is rendered to an offscreen buffer and
// post-processed with a `RenderEffect.createBlurEffect` before composite.
// The accompanying translucent fill gives the surface its "frosted"
// tint; the blur does the visual work of softening whatever sits on the
// overlay.
//
// Composability: callers continue to apply `.clip(RoundedCornerShape(...))`
// and `.border(...)` AFTER `glassmorphicBackground`; the modifier itself
// only sets up the blur layer and the scrim. minSdk is 31 (Android 12+)
// — see ADR 0010.
// ===================================================================

/** A blurred translucent overlay for glass-style bars and pills. */
fun Modifier.glassmorphicBackground(
    alpha: Float = 0.3f
): Modifier = this
    .graphicsLayer {
        compositingStrategy = CompositingStrategy.Offscreen
        renderEffect = RenderEffect
            .createBlurEffect(8f, 8f, Shader.TileMode.CLAMP)
            .asComposeRenderEffect()
    }
    .background(Color.White.copy(alpha = alpha))

val GlassShape = RoundedCornerShape(28.dp)

@Composable
fun PaletteMuseTheme(
    content: @Composable () -> Unit
) {
    val colorScheme = LightColorScheme
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = true
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
