package com.tenacy.roadcapture.ui.dto

import android.os.Parcelable
import com.tenacy.roadcapture.data.firebase.dto.FirebaseAlbum
import kotlinx.parcelize.Parcelize
import java.time.LocalDateTime

@Parcelize
data class Album(
    val id: String = "", // Firestore 문서 ID
    val title: String = "",
    val createdAt: LocalDateTime,
    val endedAt: LocalDateTime,
    val thumbnailUrl: String = "",
    val viewCount: Int = 0,
    val isScraped: Boolean = false,
    val scrapCount: Int = 0,
    val isPublic: Boolean = false,
    val regionTags: List<Map<String, String>> = emptyList(),
    val user: User,
): Parcelable {
    @Parcelize
    data class User(
        val id: String = "",
        val displayName: String = "",
        val photoUrl: String = "",
    ): Parcelable

    companion object {

        fun from(
            album: FirebaseAlbum,
            isScraped: Boolean,
        ): Album = Album(
            id = album.id,
            title = album.title,
            createdAt = album.createdAt,
            endedAt = album.endedAt,
            thumbnailUrl = album.thumbnailUrl,
            viewCount = album.viewCount,
            isScraped = isScraped,
            scrapCount = album.scrapCount,
            isPublic = album.isPublic,
            regionTags = album.regionTags,
            user = User(
                id = album.userId,
                displayName = album.userDisplayName,
                photoUrl = album.userPhotoUrl,
            )
        )
    }
}