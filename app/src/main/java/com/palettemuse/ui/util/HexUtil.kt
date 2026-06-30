package com.palettemuse.ui.util

import androidx.compose.ui.graphics.Color
import com.palettemuse.core.parseHexRgb
import com.palettemuse.theme.RoseGold

fun parseHex(hex: String): Color = runCatching {
    val rgb = parseHexRgb(hex)
    Color(rgb.r, rgb.g, rgb.b)
}.getOrDefault(RoseGold)
