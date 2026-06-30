package com.palettemuse.ui.util

fun relativeTimeLabel(epoch: Long): String {
    val days = ((System.currentTimeMillis() - epoch) / 86_400_000L).toInt()
    return when {
        days <= 0 -> "今日更新"
        days == 1 -> "昨日"
        else -> "${days}天前"
    }
}
