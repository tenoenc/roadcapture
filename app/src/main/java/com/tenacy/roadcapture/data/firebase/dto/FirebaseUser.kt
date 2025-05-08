package com.tenacy.roadcapture.data.firebase.dto

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import java.time.LocalDateTime

@Parcelize
data class FirebaseUser(
    val id: String,
    val displayName: String,
    val photoName: String,
    val photoUrl: String,
    val provider: String,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime,
): Parcelable
