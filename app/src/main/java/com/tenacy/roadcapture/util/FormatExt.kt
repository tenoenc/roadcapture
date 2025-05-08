package com.tenacy.roadcapture.util

import android.content.Context
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.*
import java.util.concurrent.TimeUnit

fun getDurationFormattedText(timestamp1: Long, timestamp2: Long): String {
    val duration = timestamp2 - timestamp1
    val days = duration / (24 * 60 * 60 * 1000)
    val hours = (duration % (24 * 60 * 60 * 1000)) / (60 * 60 * 1000)
    val minutes = (duration % (60 * 60 * 1000)) / (60 * 1000)

    return when {
        days > 0 -> "${days}일 ${hours}시간 ${minutes}분"
        hours > 0 -> "${hours}시간 ${minutes}분"
        else -> "${minutes}분"
    }
}

fun LocalDateTime.formatWithPattern(pattern: String, zoneId: ZoneId = ZoneId.systemDefault()): String {
    val formatter = DateTimeFormatter.ofPattern(pattern)
    return this.atZone(zoneId).format(formatter)
}

fun LocalDateTime.formatToLocalizedDate(
    context: Context,
    locale: Locale = context.resources.configuration.locales[0],
    style: FormatStyle = FormatStyle.LONG
): String {
    // 로케일별 커스텀 패턴 정의
    val pattern = when (locale.language) {
        // 단위 문자를 사용하는 언어
        Locale.KOREAN.language -> "yyyy년 MM월 dd일"
        Locale.JAPANESE.language -> "yyyy年 MM月 dd日"
        Locale.CHINESE.language -> "yyyy年 MM月 dd日"
        Locale.SIMPLIFIED_CHINESE.language -> "yyyy年 MM月 dd日"
        Locale.TRADITIONAL_CHINESE.language -> "yyyy年 MM月 dd日"

        // 다른 언어들은 단위 문자 없이 구분 기호만 사용
        Locale.GERMAN.language -> "dd. MMMM yyyy"
        Locale.FRENCH.language -> "d MMMM yyyy"
        Locale.ITALIAN.language -> "d MMMM yyyy"
        Locale.ENGLISH.language -> "MMMM d, yyyy"

        // 국가 코드 기반 패턴 (단위 문자 사용하지 않음)
        else -> when (locale.country) {
            Locale.FRANCE.country -> "dd/MM/yyyy"
            Locale.GERMANY.country -> "dd.MM.yyyy"
            Locale.ITALY.country -> "dd/MM/yyyy"
            Locale.JAPAN.country -> "yyyy/MM/dd"
            Locale.KOREA.country -> "yyyy. MM. dd"
            Locale.UK.country -> "dd/MM/yyyy"
            Locale.US.country -> "MM/dd/yyyy"
            Locale.CANADA.country -> "yyyy-MM-dd"
            Locale.CANADA_FRENCH.country -> "yyyy-MM-dd"
            Locale.CHINA.country -> "yyyy-MM-dd"
            Locale.PRC.country -> "yyyy-MM-dd"
            Locale.TAIWAN.country -> "yyyy/MM/dd"
            else -> null // 패턴이 없으면 시스템 기본 형식 사용
        }
    }

    // 커스텀 패턴이 있으면 사용, 없으면 FormatStyle 사용
    val formatter = if (pattern != null) {
        DateTimeFormatter.ofPattern(pattern, locale)
    } else {
        DateTimeFormatter.ofLocalizedDate(style).withLocale(locale)
    }

    return this.format(formatter)
}

fun LocalDateTime.formatToLocalizedDateTime(
    context: Context,
    locale: Locale = context.resources.configuration.locales[0],
    style: FormatStyle = FormatStyle.LONG
): String {
    // 로케일별 커스텀 패턴 정의
    val pattern = when (locale.language) {
        // 단위 문자를 사용하는 언어
        Locale.KOREAN.language -> "yyyy년 MM월 dd일 HH시 mm분"
        Locale.JAPANESE.language -> "yyyy年 MM月 dd日 HH時 mm分"
        Locale.CHINESE.language -> "yyyy年 MM月 dd日 HH時 mm分"
        Locale.SIMPLIFIED_CHINESE.language -> "yyyy年 MM月 dd日 HH时 mm分"
        Locale.TRADITIONAL_CHINESE.language -> "yyyy年 MM月 dd日 HH時 mm分"

        // 다른 언어들은 단위 문자 없이 구분 기호만 사용
        Locale.GERMAN.language -> "dd. MMMM yyyy HH:mm 'Uhr'"
        Locale.FRENCH.language -> "d MMMM yyyy HH:mm"
        Locale.ITALIAN.language -> "d MMMM yyyy HH:mm"
        Locale.ENGLISH.language -> "MMMM d, yyyy h:mm a"

        // 국가 코드 기반 패턴 (단위 문자 사용하지 않음)
        else -> when (locale.country) {
            Locale.FRANCE.country -> "dd/MM/yyyy HH:mm"
            Locale.GERMANY.country -> "dd.MM.yyyy HH:mm"
            Locale.ITALY.country -> "dd/MM/yyyy HH:mm"
            Locale.JAPAN.country -> "yyyy/MM/dd HH:mm"
            Locale.KOREA.country -> "yyyy. MM. dd HH:mm"
            Locale.UK.country -> "dd/MM/yyyy HH:mm"
            Locale.US.country -> "MM/dd/yyyy h:mm a"
            Locale.CANADA.country -> "yyyy-MM-dd HH:mm"
            Locale.CANADA_FRENCH.country -> "yyyy-MM-dd HH:mm"
            Locale.CHINA.country -> "yyyy-MM-dd HH:mm"
            Locale.PRC.country -> "yyyy-MM-dd HH:mm"
            Locale.TAIWAN.country -> "yyyy/MM/dd HH:mm"
            else -> null // 패턴이 없으면 시스템 기본 형식 사용
        }
    }

    // 커스텀 패턴이 있으면 사용, 없으면 FormatStyle 사용
    val formatter = if (pattern != null) {
        DateTimeFormatter.ofPattern(pattern, locale)
    } else {
        DateTimeFormatter.ofLocalizedDateTime(style).withLocale(locale)
    }

    return this.format(formatter)
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
    return if (minutes in 1..59) minutes else null
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
    return if (hours in 1..23) hours else null
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
    return if (days in 1..6) days else null
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
    return if (weeks in 1..4) weeks else null
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
    return if (months in 1..11) months else null
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
fun getFormattedDuration(timestamp1: Long, timestamp2: Long): Pair<String, String> {
    return when {
        getDurationMinutes(timestamp1, timestamp2) != null -> {
            Pair(getDurationMinutes(timestamp1, timestamp2)!!.toString(), "분")
        }
        getDurationHours(timestamp1, timestamp2) != null -> {
            Pair(getDurationHours(timestamp1, timestamp2)!!.toString(), "시간")
        }
        getDurationDays(timestamp1, timestamp2) != null -> {
            Pair(getDurationDays(timestamp1, timestamp2)!!.toString(), "일")
        }
        getDurationWeeks(timestamp1, timestamp2) != null -> {
            Pair(getDurationWeeks(timestamp1, timestamp2)!!.toString(), "주")
        }
        getDurationMonths(timestamp1, timestamp2) != null -> {
            Pair(getDurationMonths(timestamp1, timestamp2)!!.toString(), "개월")
        }
        getDurationYears(timestamp1, timestamp2) != null -> {
            Pair(getDurationYears(timestamp1, timestamp2)!!.toString(), "년")
        }
        else -> {
            Pair("방금", "")
        }
    }
}

/**
 * Long 값을 "9.1만", "1.2천"과 같은 형식의 숫자와 단위로 변환하는 확장 함수
 * @return Pair<Double, String> - 첫 번째 값은 변환된 숫자, 두 번째 값은 단위 문자열
 */
fun Long.toReadableUnitText(): Pair<String, String> {
    return when {
        this >= 100000000 -> {
            val value = this / 10000000.0
            val formatted = if (value == value.toLong().toDouble()) {
                value.toLong().toDouble()
            } else {
                (value * 10).toLong() / 10.0
            }
            Pair(formatted.toFormattedDecimalText(), "억")
        }
        this >= 10000 -> {
            val value = this / 10000.0
            val formatted = if (value == value.toLong().toDouble()) {
                value.toLong().toDouble()
            } else {
                (value * 10).toLong() / 10.0
            }
            Pair(formatted.toFormattedDecimalText(), "만")
        }
        this >= 1000 -> {
            val value = this / 1000.0
            val formatted = if (value == value.toLong().toDouble()) {
                value.toLong().toDouble()
            } else {
                (value * 10).toLong() / 10.0
            }
            Pair(formatted.toFormattedDecimalText(), "천")
        }
        else -> Pair(this.toDouble().takeIf { it > 0 }?.toInt()?.toString() ?: "없음", "")
    }
}

fun Double.toFormattedDecimalText(): String {
    val intValue = this.toInt()
    val firstDecimal = ((this - intValue) * 10).toInt()

    return if (firstDecimal > 0) {
        String.format("%.1f", this)
    } else {
        intValue.toString()
    }
}