package com.tenacy.roadcapture.data.firebase.dto

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import java.time.LocalDateTime

@Parcelize
data class FirebaseLocation(
    val id: String = "",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val createdAt: LocalDateTime,
): Parcelable