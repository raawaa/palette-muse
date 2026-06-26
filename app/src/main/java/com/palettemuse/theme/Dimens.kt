package com.palettemuse.theme

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
