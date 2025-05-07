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
    val viewCount: Int = 0,
    val likeCount: Int = 0,
    val regionTags: List<Map<String, String>> = emptyList(),
    val user: User,
    val isPublic: Boolean = false,
): Parcelable {

    @Parcelize
    data class User(
        val id: String = "",
        val name: String = "",
        val photoUrl: String = "",
    ): Parcelable
}