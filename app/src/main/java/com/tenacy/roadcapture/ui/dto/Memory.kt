package com.tenacy.roadcapture.ui.dto

import android.os.Parcelable
import com.tenacy.roadcapture.data.firebase.dto.FirebaseMemory
import kotlinx.parcelize.Parcelize
import java.time.LocalDateTime

@Parcelize
data class Memory(
    val id: String = "",
    val albumId: String = "",
    val userId: String = "",
    val content: String = "",
    val photoUrl: String = "",
    val photoName: String = "",
    val placeName: String? = null,
    val addressTags: List<String> = emptyList(),
    val formattedAddress: String = "",
    val locationRefId: String = "",
    val createdAt: LocalDateTime,
): Parcelable {

    companion object {

        fun of(memory: FirebaseMemory) = Memory(
            id = memory.id,
            albumId = memory.albumId,
            userId = memory.userId,
            content = memory.content,
            photoUrl = memory.photoUrl,
            photoName = memory.photoName,
            placeName = memory.placeName.takeIf { it.isNotBlank() },
            addressTags = memory.addressTags,
            formattedAddress = memory.formattedAddress,
            locationRefId = memory.locationRefId,
            createdAt = memory.createdAt,
        )
    }
}