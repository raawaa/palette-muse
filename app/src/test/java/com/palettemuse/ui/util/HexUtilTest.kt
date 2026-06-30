package com.palettemuse.ui.util

import androidx.compose.ui.graphics.Color
import com.palettemuse.theme.RoseGold
import org.junit.Assert.assertEquals
import org.junit.Test

class HexUtilTest {

    @Test
    fun parseHex_red() {
        assertEquals(Color(0xFFFF0000), parseHex("#FF0000"))
    }

    @Test
    fun parseHex_green() {
        assertEquals(Color(0xFF00FF00), parseHex("#00FF00"))
    }

    @Test
    fun parseHex_blue() {
        assertEquals(Color(0xFF0000FF), parseHex("#0000FF"))
    }

    @Test
    fun parseHex_withoutHash() {
        assertEquals(Color(0xFFFF0000), parseHex("FF0000"))
    }

    @Test
    fun parseHex_black() {
        assertEquals(Color(0xFF000000), parseHex("#000000"))
    }

    @Test
    fun parseHex_white() {
        assertEquals(Color(0xFFFFFFFF), parseHex("#FFFFFF"))
    }

    @Test
    fun parseHex_invalid_fallsBackToRoseGold() {
        assertEquals(RoseGold, parseHex("not-a-hex"))
    }

    @Test
    fun parseHex_shortHex_fallsBackToRoseGold() {
        assertEquals(RoseGold, parseHex("#FFF"))
    }

    @Test
    fun parseHex_empty_fallsBackToRoseGold() {
        assertEquals(RoseGold, parseHex(""))
    }
}
