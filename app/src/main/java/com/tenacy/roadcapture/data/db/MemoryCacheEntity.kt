package com.tenacy.roadcapture.data.db

import android.os.Parcelable
import androidx.room.*
import com.tenacy.roadcapture.data.firebase.dto.FirebaseMemory
import kotlinx.parcelize.Parcelize
import java.time.LocalDateTime

@Entity(tableName = "memory_caches")
@Parcelize
data class MemoryCacheEntity(
    @PrimaryKey
    val id: String,
    val albumId: String,
    val userId: String,
    val isThumbnail: Boolean,
    val content: String,
    val photoUrl: String,
    val photoName: String,
    val placeName: String,
    val addressTags: List<String>,
    val formattedAddress: String,
    val locationRefId: String,
    val isPublic: Boolean,
    val createdAt: LocalDateTime,
): Parcelable {

    fun toFirebaseMemory() = FirebaseMemory(
        id = id,
        albumId = albumId,
        userId = userId,
        isThumbnail = isThumbnail,
        content = content,
        photoUrl = photoUrl,
        photoName = photoName,
        placeName = placeName,
        addressTags = addressTags,
        formattedAddress = formattedAddress,
        locationRefId = locationRefId,
        isPublic = isPublic,
        createdAt = createdAt,
    )

    companion object {
        fun of(dto: FirebaseMemory) = MemoryCacheEntity(
            id = dto.id,
            albumId = dto.albumId,
            userId = dto.userId,
            isThumbnail = dto.isThumbnail,
            content = dto.content,
            photoUrl = dto.photoUrl,
            photoName = dto.photoName,
            placeName = dto.placeName,
            addressTags = dto.addressTags,
            formattedAddress = dto.formattedAddress,
            locationRefId = dto.locationRefId,
            isPublic = dto.isPublic,
            createdAt = dto.createdAt,
        )
    }
}