package com.tenacy.roadcapture.util

import android.content.Context
import android.content.res.Resources
import android.location.Location
import android.net.Uri
import android.os.Parcelable
import android.util.Log
import android.util.TypedValue
import androidx.work.Data
import androidx.work.workDataOf
import com.google.android.gms.maps.model.LatLng
import com.google.firebase.Timestamp
import com.google.gson.Gson
import com.tenacy.roadcapture.R
import com.tenacy.roadcapture.worker.UpdateUserPhotoWorker
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.*

val Number.toPx
    get() = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP,
        this.toFloat(),
        Resources.getSystem().displayMetrics
    ).toInt()

val Number.toDp
    get() = this.toFloat() / Resources.getSystem().displayMetrics.density

fun Long.toLocalDateTime(): LocalDateTime =
    Instant
        .ofEpochMilli(this)
        .atZone(ZoneId.systemDefault())
        .toLocalDateTime()

fun LocalDateTime.toTimestamp(): Long =
    this.atZone(ZoneId.systemDefault())
        .toInstant()
        .toEpochMilli()

fun LocalDateTime.toUtcTimestamp(): Long =
    this.atZone(ZoneId.of("UTC"))
        .toInstant()
        .toEpochMilli()

fun Date.toLocalDateTime(): LocalDateTime =
    LocalDateTime.ofInstant(this.toInstant(), ZoneId.systemDefault())

fun LocalDateTime.toDate(): Date {
    val instant = this.atZone(ZoneId.systemDefault()).toInstant()
    return Date.from(instant)
}

fun Long.toFirebaseTimestamp(): Timestamp {
    val instant = Instant.ofEpochMilli(this)
    return Timestamp(instant.epochSecond, instant.nano)
}

fun LocalDateTime.toFirebaseTimestamp(): Timestamp {
    val instant = this.atZone(ZoneId.systemDefault()).toInstant()
    return Timestamp(instant.epochSecond, instant.nano)
}

fun Location.toLatLng() = LatLng(latitude, longitude)
fun getCustomLocationFrom(latitude: Double, longitude: Double) = Location("custom_location").apply {
    this.latitude = latitude
    this.longitude = longitude
}

/**
 * 큰 숫자를 각 언어별 표기 방식에 맞게 변환하는 확장 함수
 * 지원 언어: 한국어, 영어, 중국어(간체), 스페인어, 포르투갈어, 일본어,
 *          독일어, 프랑스어, 러시아어, 인도네시아어, 힌디어, 아랍어
 */
fun Number.toLocalizedString(context: Context): String {
    val locale = context.resources.configuration.locales[0]
    val language = locale.language
    val country = locale.country
    val number = this.toDouble()

    return when {
        // 한국어 (만, 억, 조)
        language == "ko" -> {
            when {
                number >= 1_000_000_000_000 -> {
                    val count = number / 1_000_000_000_000
                    formatWithDecimal(count) + context.getString(R.string.unit_ko_trillion)
                }
                number >= 100_000_000 -> {
                    val count = number / 100_000_000
                    formatWithDecimal(count) + context.getString(R.string.unit_ko_billion)
                }
                number >= 10_000 -> {
                    val count = number / 10_000
                    formatWithDecimal(count) + context.getString(R.string.unit_ko_tenthousand)
                }
                number >= 1_000 -> {
                    val count = number / 1_000
                    formatWithDecimal(count) + context.getString(R.string.unit_ko_thousand)
                }
                else -> formatWithDecimal(number)
            }
        }

        // 중국어 간체 (万, 亿)
        language == "zh" -> {
            when {
                number >= 100_000_000 -> {
                    val count = number / 100_000_000
                    formatWithDecimal(count) + context.getString(R.string.unit_zh_billion)
                }
                number >= 10_000 -> {
                    val count = number / 10_000
                    formatWithDecimal(count) + context.getString(R.string.unit_zh_tenthousand)
                }
                number >= 1_000 -> {
                    val count = number / 1_000
                    formatWithDecimal(count) + context.getString(R.string.unit_zh_thousand)
                }
                else -> formatWithDecimal(number)
            }
        }

        // 일본어 (万, 億, 兆)
        language == "ja" -> {
            when {
                number >= 1_000_000_000_000 -> {
                    val count = number / 1_000_000_000_000
                    formatWithDecimal(count) + context.getString(R.string.unit_ja_trillion)
                }
                number >= 100_000_000 -> {
                    val count = number / 100_000_000
                    formatWithDecimal(count) + context.getString(R.string.unit_ja_billion)
                }
                number >= 10_000 -> {
                    val count = number / 10_000
                    formatWithDecimal(count) + context.getString(R.string.unit_ja_tenthousand)
                }
                number >= 1_000 -> {
                    val count = number / 1_000
                    formatWithDecimal(count) + context.getString(R.string.unit_ja_thousand)
                }
                else -> formatWithDecimal(number)
            }
        }

        // 인도/힌디어 (lakh, crore)
        language == "hi" || (language == "en" && country == "IN") -> {
            when {
                number >= 10_000_000 -> {
                    val count = number / 10_000_000
                    formatWithDecimal(count) + context.getString(R.string.unit_hi_crore)
                }
                number >= 100_000 -> {
                    val count = number / 100_000
                    formatWithDecimal(count) + context.getString(R.string.unit_hi_lakh)
                }
                number >= 1_000 -> {
                    val count = number / 1_000
                    formatWithDecimal(count) + context.getString(R.string.unit_hi_thousand)
                }
                else -> formatWithDecimal(number)
            }
        }

        // 러시아어 (тысяча, миллион, миллиард)
        language == "ru" -> {
            when {
                number >= 1_000_000_000 -> {
                    val count = number / 1_000_000_000
                    formatWithDecimal(count) + " " + context.getString(R.string.unit_ru_billion)
                }
                number >= 1_000_000 -> {
                    val count = number / 1_000_000
                    formatWithDecimal(count) + " " + context.getString(R.string.unit_ru_million)
                }
                number >= 1_000 -> {
                    val count = number / 1_000
                    formatWithDecimal(count) + " " + context.getString(R.string.unit_ru_thousand)
                }
                else -> formatWithDecimal(number)
            }
        }

        // 아랍어 (ألف, مليون, مليار)
        language == "ar" -> {
            when {
                number >= 1_000_000_000 -> {
                    val count = number / 1_000_000_000
                    formatWithDecimal(count) + " " + context.getString(R.string.unit_ar_billion)
                }
                number >= 1_000_000 -> {
                    val count = number / 1_000_000
                    formatWithDecimal(count) + " " + context.getString(R.string.unit_ar_million)
                }
                number >= 1_000 -> {
                    val count = number / 1_000
                    formatWithDecimal(count) + " " + context.getString(R.string.unit_ar_thousand)
                }
                else -> formatWithDecimal(number, locale)
            }
        }

        // 서구 언어(영어, 스페인어, 포르투갈어, 독일어, 프랑스어, 인도네시아어)
        else -> {
            when {
                number >= 1_000_000_000 -> {
                    val count = number / 1_000_000_000
                    val unitKey = when (language) {
                        "en" -> R.string.unit_en_billion
                        "es" -> R.string.unit_es_billion
                        "pt" -> R.string.unit_pt_billion
                        "de" -> R.string.unit_de_billion
                        "fr" -> R.string.unit_fr_billion
                        "in", "id" -> R.string.unit_id_billion
                        else -> R.string.unit_en_billion
                    }
                    val unit = context.getString(unitKey)
                    val needsSpace = language in listOf("es", "pt", "de", "fr")
                    formatWithDecimal(count) + if (needsSpace) " " else "" + unit
                }
                number >= 1_000_000 -> {
                    val count = number / 1_000_000
                    val unitKey = when (language) {
                        "en" -> R.string.unit_en_million
                        "es" -> R.string.unit_es_million
                        "pt" -> R.string.unit_pt_million
                        "de" -> R.string.unit_de_million
                        "fr" -> R.string.unit_fr_million
                        "in", "id" -> R.string.unit_id_million
                        else -> R.string.unit_en_million
                    }
                    val unit = context.getString(unitKey)
                    val needsSpace = language in listOf("es", "pt", "de", "fr")
                    formatWithDecimal(count) + if (needsSpace) " " else "" + unit
                }
                number >= 1_000 -> {
                    val count = number / 1_000
                    val unitKey = when (language) {
                        "en" -> R.string.unit_en_thousand
                        "es" -> R.string.unit_es_thousand
                        "pt" -> R.string.unit_pt_thousand
                        "de" -> R.string.unit_de_thousand
                        "fr" -> R.string.unit_fr_thousand
                        "in", "id" -> R.string.unit_id_thousand
                        else -> R.string.unit_en_thousand
                    }
                    val unit = context.getString(unitKey)
                    val needsSpace = language in listOf("es", "pt", "de", "fr")
                    formatWithDecimal(count) + if (needsSpace) " " else "" + unit
                }
                else -> formatWithDecimal(number, locale)
            }
        }
    }
}

/**
 * 소수점이 있는 숫자를 정수 또는 소수 형태로 변환 (정수면 .0 제거)
 */
private fun formatWithDecimal(number: Double, locale: Locale = Locale.getDefault()): String {
    return if (number == number.toLong().toDouble()) {
        number.toLong().toString()
    } else {
        String.format(locale, "%.1f", number)
    }
}

/**
 * 상대적 시간 표시 (X 시간 전, 방금 전)를 지역화하는 확장 함수
 */
fun Long.toLocalizedTimeAgo(context: Context): String {
    val diffInMillis = System.currentTimeMillis() - this
    val seconds = diffInMillis / 1000
    val minutes = seconds / 60
    val hours = minutes / 60
    val days = hours / 24

    val locale = context.resources.configuration.locales[0]
    val language = locale.language

    return when {
        seconds < 60 -> context.getString(R.string.time_just_now)
        minutes < 60 -> {
            val resId = R.plurals.time_minutes_ago
            context.resources.getQuantityString(resId, minutes.toInt(), minutes.toInt())
        }
        hours < 24 -> {
            val resId = R.plurals.time_hours_ago
            context.resources.getQuantityString(resId, hours.toInt(), hours.toInt())
        }
        else -> {
            val resId = R.plurals.time_days_ago
            context.resources.getQuantityString(resId, days.toInt(), days.toInt())
        }
    }
}

/**
 * 날짜를 지역화된 형식으로 변환하는 확장 함수
 */
fun Date.toLocalizedDateString(context: Context): String {
    val locale = context.resources.configuration.locales[0]
    val language = locale.language

    // 한국어, 일본어, 중국어는 yyyy년 MM월 dd일 형식 사용
    val pattern = when (language) {
        "ko" -> context.getString(R.string.date_format_ko)
        "ja" -> context.getString(R.string.date_format_ja)
        "zh" -> context.getString(R.string.date_format_zh)
        else -> context.getString(R.string.date_format_default)
    }

    val dateFormat = SimpleDateFormat(pattern, locale)
    return dateFormat.format(this)
}

/**
 * 날짜와 시간을 지역화된 형식으로 변환하는 확장 함수
 * @param includeTime 시간(시,분,초) 포함 여부
 * @param includeSeconds 초 포함 여부 (includeTime이 true일 때만 적용)
 */
fun Date.toLocalizedDateTimeString(
    context: Context,
    includeTime: Boolean = false,
    includeSeconds: Boolean = false
): String {
    val locale = context.resources.configuration.locales[0]
    val language = locale.language

    // 패턴 결정 (날짜만, 날짜+시간, 날짜+시간+초)
    val pattern = when {
        !includeTime -> when (language) {
            "ko" -> context.getString(R.string.date_format_ko)
            "ja" -> context.getString(R.string.date_format_ja)
            "zh" -> context.getString(R.string.date_format_zh)
            else -> context.getString(R.string.date_format_default)
        }
        includeTime && !includeSeconds -> when (language) {
            "ko" -> context.getString(R.string.datetime_format_ko)
            "ja" -> context.getString(R.string.datetime_format_ja)
            "zh" -> context.getString(R.string.datetime_format_zh)
            else -> context.getString(R.string.datetime_format_default)
        }
        else -> when (language) {
            "ko" -> context.getString(R.string.datetime_seconds_format_ko)
            "ja" -> context.getString(R.string.datetime_seconds_format_ja)
            "zh" -> context.getString(R.string.datetime_seconds_format_zh)
            else -> context.getString(R.string.datetime_seconds_format_default)
        }
    }

    val dateFormat = SimpleDateFormat(pattern, locale)
    return dateFormat.format(this)
}