package com.tenacy.roadcapture.ui.dto

import android.os.Parcelable
import com.tenacy.roadcapture.data.SocialType
import com.tenacy.roadcapture.data.firebase.dto.FirebaseUser
import kotlinx.parcelize.Parcelize
import java.time.LocalDateTime

@Parcelize
data class User(
    val id: String,
    val displayName: String,
    val photoName: String,
    val photoUrl: String,
    val provider: SocialType,
    val scrapCount: Long,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime,
    val albumCount: Long,
    val memoryCount: Long,
): Parcelable {

    companion object {

        fun from(user: FirebaseUser, albumCount: Long = 0L, memoryCount: Long = 0L) = User(
            id = user.id,
            displayName = user.displayName,
            photoName = user.photoName,
            photoUrl = user.photoUrl,
            provider = SocialType.of(user.provider),
            scrapCount = user.scrapCount,
            createdAt = user.createdAt,
            updatedAt = user.updatedAt,
            albumCount = albumCount,
            memoryCount = memoryCount,
        )
    }
}
