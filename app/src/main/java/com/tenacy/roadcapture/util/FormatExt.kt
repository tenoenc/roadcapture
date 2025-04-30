package com.tenacy.roadcapture.util

import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

fun getDurationFormattedText(timestamp1: Long, timestamp2: Long): String {
    val duration = timestamp2 - timestamp1
    val hours = duration / (60 * 60 * 1000)
    val minutes = (duration % (60 * 60 * 1000)) / (60 * 1000)

    return when {
        hours > 0 -> "${hours}시간 ${minutes}분"
        else -> "${minutes}분"
    }
}

fun getDurationHour(timestamp1: Long, timestamp2: Long): Int? {
    val duration = timestamp2 - timestamp1
    val durationHour = duration / (60 * 60 * 1000)
    return durationHour.takeIf { it > 0 }?.toInt()
}

fun getDurationMinute(timestamp1: Long, timestamp2: Long): Int? {
    val duration = timestamp2 - timestamp1
    val durationMinutes = (duration % (60 * 60 * 1000)) / (60 * 1000)
    return durationMinutes.takeIf { it > 0 }?.toInt()
}

fun LocalDateTime.formatWithPattern(pattern: String, zoneId: ZoneId = ZoneId.systemDefault()): String {
    val formatter = DateTimeFormatter.ofPattern(pattern)
    return this.atZone(zoneId).format(formatter)
}