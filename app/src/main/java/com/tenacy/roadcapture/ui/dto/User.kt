package com.tenacy.roadcapture.ui.dto

import android.os.Parcelable
import com.tenacy.roadcapture.data.firebase.dto.FirebaseUser
import kotlinx.parcelize.Parcelize
import java.time.LocalDateTime

@Parcelize
data class User(
    val id: String,
    val displayName: String,
    val photoName: String,
    val photoUrl: String,
    val provider: String,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime,
    val albumCount: Long,
    val memoryCount: Long,
    val scrapCount: Long = 0L,
): Parcelable {

    companion object {

        fun from(user: FirebaseUser, albumCount: Long, memoryCount: Long) = User(
            id = user.id,
            displayName = user.displayName,
            photoName = user.photoName,
            photoUrl = user.photoUrl,
            provider = user.provider,
            scrapCount = user.scrapCount,
            createdAt = user.createdAt,
            updatedAt = user.updatedAt,
            albumCount = albumCount,
            memoryCount = memoryCount,
        )
    }
}
