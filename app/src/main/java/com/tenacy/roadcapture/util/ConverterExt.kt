package com.tenacy.roadcapture.util

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
import com.tenacy.roadcapture.worker.UpdateUserPhotoWorker
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

fun Date.toLocalDateTime(): LocalDateTime =
    LocalDateTime.ofInstant(this.toInstant(), ZoneId.systemDefault())

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