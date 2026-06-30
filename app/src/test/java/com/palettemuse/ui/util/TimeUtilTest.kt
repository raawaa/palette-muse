package com.palettemuse.ui.util

import org.junit.Assert.assertEquals
import org.junit.Test

class TimeUtilTest {

    @Test
    fun relativeTimeLabel_today() {
        val now = System.currentTimeMillis()
        assertEquals("今日更新", relativeTimeLabel(now))
    }

    @Test
    fun relativeTimeLabel_yesterday() {
        val yesterday = System.currentTimeMillis() - 86_400_000L
        assertEquals("昨日", relativeTimeLabel(yesterday))
    }

    @Test
    fun relativeTimeLabel_threeDaysAgo() {
        val threeDays = System.currentTimeMillis() - 3 * 86_400_000L
        assertEquals("3天前", relativeTimeLabel(threeDays))
    }

    @Test
    fun relativeTimeLabel_future_returnsToday() {
        val future = System.currentTimeMillis() + 86_400_000L
        assertEquals("今日更新", relativeTimeLabel(future))
    }

    @Test
    fun relativeTimeLabel_oneSecondAgo_isToday() {
        val oneSecAgo = System.currentTimeMillis() - 1_000L
        assertEquals("今日更新", relativeTimeLabel(oneSecAgo))
    }
}
