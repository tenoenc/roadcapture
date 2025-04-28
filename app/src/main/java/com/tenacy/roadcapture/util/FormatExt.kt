package com.tenacy.roadcapture.util

fun Long.toDurationFormattedText(): String {
    val hours = this / (60 * 60 * 1000)
    val minutes = (this % (60 * 60 * 1000)) / (60 * 1000)

    return when {
        hours > 0 -> "${hours}시간 ${minutes}분"
        else -> "${minutes}분"
    }
}