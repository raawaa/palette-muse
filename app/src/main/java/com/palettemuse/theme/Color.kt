package com.palettemuse.theme

import androidx.compose.ui.graphics.Color

// ===================================================================
// Stitch Design System Glassmorphism & Shadow Colors
// ===================================================================

// Glassmorphism — semi-transparent white layers
val GlassWhite = Color.White.copy(alpha = 0.6f)
val GlassWhiteLight = Color.White.copy(alpha = 0.4f)

// Custom Soft Shadows 禁止黑色阴影
// 主色 RoseGold 5-8% 透明度 + blur 20-30px + 长偏移
val AmbientShadowColor = Color(0xFFB76E79).copy(alpha = 0.06f)
val SpotShadowColor = Color(0xFFB76E79).copy(alpha = 0.08f)

// Semantic helpers
val SuccessGreen = Color(0xFF4CAF50)

// ===================================================================
// Aura Aesthetic design tokens (from Stitch design HTML)
// Shared across HomeScreen / ThemeDetailScreen / ExportScreen.
// surface: #FCF9F8 | on-surface: #1C1B1B | on-surface-variant: #524345
// primary: #8A4853 | surface-tint: #8C4B55 | outline-variant: #D7C1C3
// ===================================================================
val PrimaryDesign = Color(0xFF8A4853)
val SurfaceTint = Color(0xFF8C4B55)
val OnSurface = Color(0xFF1C1B1B)
val OnSurfaceVariant = Color(0xFF524345)
val OutlineVariant = Color(0xFFD7C1C3)
val SurfaceContainerHighest = Color(0xFFE5E2E1)
val TertiaryFixedDim = Color(0xFFC8C6C3)
