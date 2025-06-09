package com.tenacy.roadcapture.util

fun getDurationFormattedText(timestamp1: Long, timestamp2: Long): Triple<Long, Long, Long> {
    val duration = timestamp2 - timestamp1
    val days = duration / (24 * 60 * 60 * 1000)
    val hours = (duration % (24 * 60 * 60 * 1000)) / (60 * 60 * 1000)
    val minutes = (duration % (60 * 60 * 1000)) / (60 * 1000)

    return when {
        days > 0 -> {
            val `0` = days
            val `1` = hours
            val `2` = minutes
            Triple(`0`, `1`, `2`)
        }
        hours > 0 -> {
            val `0` = hours
            val `1` = minutes
            Triple(0, `0`, `1`)
        }
        else -> {
            val `0` = minutes
            Triple(0, 0, `0`)
        }
    }
}