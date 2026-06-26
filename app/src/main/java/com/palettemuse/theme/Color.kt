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
