package com.tenacy.roadcapture.util

import android.content.res.Resources
import android.util.TypedValue
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

fun LocalDateTime.toTimestamp(): Long =
    this.atZone(ZoneId.systemDefault())
        .toInstant()
        .toEpochMilli()