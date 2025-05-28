package com.tenacy.roadcapture.data.firebase.dto

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import java.time.LocalDateTime

@Parcelize
data class FirebaseMemory(
    val id: String = "",
    val albumId: String = "",
    val userId: String = "",
    val content: String = "",
    val photoUrl: String = "",
    val photoName: String = "",
    val placeName: String = "",
    val addressTags: List<String> = emptyList(),
    val formattedAddress: String = "",
    val locationRefId: String = "",
    val isPublic: Boolean = false,
    val createdAt: LocalDateTime,
): Parcelable