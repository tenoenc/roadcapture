package com.tenacy.roadcapture.util

import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

fun getDurationFormattedText(timestamp1: Long, timestamp2: Long): String {
    val duration = timestamp2 - timestamp1
    val hours = duration / (60 * 60 * 1000)
    val minutes = (duration % (60 * 60 * 1000)) / (60 * 1000)

    return when {
        hours > 0 -> "${hours}시간 ${minutes}분"
        else -> "${minutes}분"
    }
}

fun LocalDateTime.formatWithPattern(pattern: String, zoneId: ZoneId = ZoneId.systemDefault()): String {
    val formatter = DateTimeFormatter.ofPattern(pattern)
    return this.atZone(zoneId).format(formatter)
}

/**
 * 두 타임스탬프 사이의 기간을 분 단위로 반환 (60분 미만인 경우에만)
 * @param timestamp1 시작 타임스탬프 (밀리초)
 * @param timestamp2 종료 타임스탬프 (밀리초)
 * @return 분 단위 기간 또는 60분 이상인 경우 null
 */
fun getDurationMinutes(timestamp1: Long, timestamp2: Long): Int? {
    val durationMillis = timestamp2 - timestamp1
    val minutes = TimeUnit.MILLISECONDS.toMinutes(durationMillis).toInt()

    // 60분 미만인 경우에만 반환
    return if (minutes < 60) minutes else null
}

/**
 * 두 타임스탬프 사이의 기간을 시간 단위로 반환 (24시간 미만인 경우에만)
 * @param timestamp1 시작 타임스탬프 (밀리초)
 * @param timestamp2 종료 타임스탬프 (밀리초)
 * @return 시간 단위 기간 또는 24시간 이상인 경우 null
 */
fun getDurationHours(timestamp1: Long, timestamp2: Long): Int? {
    val durationMillis = timestamp2 - timestamp1
    val hours = TimeUnit.MILLISECONDS.toHours(durationMillis).toInt()

    // 24시간 미만인 경우에만 반환
    return if (hours < 24) hours else null
}

/**
 * 두 타임스탬프 사이의 기간을 일 단위로 반환 (7일 미만인 경우에만)
 * @param timestamp1 시작 타임스탬프 (밀리초)
 * @param timestamp2 종료 타임스탬프 (밀리초)
 * @return 일 단위 기간 또는 7일 이상인 경우 null
 */
fun getDurationDays(timestamp1: Long, timestamp2: Long): Int? {
    val durationMillis = timestamp2 - timestamp1
    val days = TimeUnit.MILLISECONDS.toDays(durationMillis).toInt()

    // 7일 미만인 경우에만 반환
    return if (days < 7) days else null
}

/**
 * 두 타임스탬프 사이의 기간을 주 단위로 반환 (5주 미만인 경우에만)
 * @param timestamp1 시작 타임스탬프 (밀리초)
 * @param timestamp2 종료 타임스탬프 (밀리초)
 * @return 주 단위 기간 또는 5주 이상인 경우 null
 */
fun getDurationWeeks(timestamp1: Long, timestamp2: Long): Int? {
    val durationMillis = timestamp2 - timestamp1
    val days = TimeUnit.MILLISECONDS.toDays(durationMillis)
    val weeks = (days / 7).toInt()

    // 5주 미만인 경우에만 반환
    return if (weeks < 5) weeks else null
}

/**
 * 두 타임스탬프 사이의 기간을 월 단위로 반환 (12개월 미만인 경우에만)
 * @param timestamp1 시작 타임스탬프 (밀리초)
 * @param timestamp2 종료 타임스탬프 (밀리초)
 * @return 월 단위 기간 또는 12개월 이상인 경우 null
 */
fun getDurationMonths(timestamp1: Long, timestamp2: Long): Int? {
    val durationMillis = timestamp2 - timestamp1

    // 대략적인 월 계산 (한 달을 30.44일로 가정)
    val days = TimeUnit.MILLISECONDS.toDays(durationMillis)
    val months = (days / 30.44).toInt()

    // 12개월 미만인 경우에만 반환
    return if (months < 12) months else null
}

/**
 * 두 타임스탬프 사이의 기간을 년 단위로 반환
 * @param timestamp1 시작 타임스탬프 (밀리초)
 * @param timestamp2 종료 타임스탬프 (밀리초)
 * @return 년 단위 기간
 */
fun getDurationYears(timestamp1: Long, timestamp2: Long): Int? {
    val durationMillis = timestamp2 - timestamp1

    // 대략적인 년 계산 (1년을 365.25일로 가정)
    val days = TimeUnit.MILLISECONDS.toDays(durationMillis)
    return (days / 365.25).toInt().takeIf { it > 0 }
}

/**
 * 두 타임스탬프 사이의 기간을 가장 적합한 단위로 표현
 * @param timestamp1 시작 타임스탬프 (밀리초)
 * @param timestamp2 종료 타임스탬프 (밀리초)
 * @return 단위와 값을 포함한 Pair (예: Pair("분", 30))
 */
fun getFormattedDuration(timestamp1: Long, timestamp2: Long): Pair<String, Int> {
    return when {
        getDurationMinutes(timestamp1, timestamp2) != null -> {
            Pair("분", getDurationMinutes(timestamp1, timestamp2)!!)
        }
        getDurationHours(timestamp1, timestamp2) != null -> {
            Pair("시간", getDurationHours(timestamp1, timestamp2)!!)
        }
        getDurationDays(timestamp1, timestamp2) != null -> {
            Pair("일", getDurationDays(timestamp1, timestamp2)!!)
        }
        getDurationWeeks(timestamp1, timestamp2) != null -> {
            Pair("주", getDurationWeeks(timestamp1, timestamp2)!!)
        }
        getDurationMonths(timestamp1, timestamp2) != null -> {
            Pair("개월", getDurationMonths(timestamp1, timestamp2)!!)
        }
        getDurationYears(timestamp1, timestamp2) != null -> {
            Pair("년", getDurationYears(timestamp1, timestamp2)!!)
        }
        else -> {
            Pair("", -1)
        }
    }
}