package com.tenacy.roadcapture.data.firebase.dto

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import java.time.LocalDateTime

@Parcelize
data class FirebaseAlbum(
    val id: String = "", // Firestore 문서 ID
    val title: String = "",
    val createdAt: LocalDateTime,
    val endedAt: LocalDateTime,
    val thumbnailUrl: String = "",
    val scrapCount: Int = 0,
    val viewCount: Int = 0,
    val regionTags: List<Map<String, String>> = emptyList(),
    val isPublic: Boolean = false,
    val branchLink: String? = null,
    val shareId: String? = null,
    val shareCreatedAt: LocalDateTime? = null,
    val userId: String = "",
    val userDisplayName: String = "",
    val userPhotoUrl: String = "",
    val memoryAddressTags: List<String> = emptyList(),
    val memoryPlaceNames: List<String> = emptyList(),
): Parcelable