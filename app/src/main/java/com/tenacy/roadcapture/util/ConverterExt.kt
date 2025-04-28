package com.tenacy.roadcapture.util

import android.content.res.Resources
import android.util.TypedValue
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

val Number.toPx
    get() = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP,
        this.toFloat(),
        Resources.getSystem().displayMetrics
    ).toInt()

val Number.toDp
    get() = this.toFloat() / Resources.getSystem().displayMetrics.density

fun Long.toLocalDateTime(zoneId: ZoneId = ZoneId.of("UTC")): LocalDateTime =
    Instant
        .ofEpochMilli(this)
        .atZone(zoneId)
        .toLocalDateTime()

fun LocalDateTime.toTimestamp(zoneId: ZoneId = ZoneId.of("UTC")): Long =
    this.atZone(zoneId)
        .toInstant()
        .toEpochMilli()