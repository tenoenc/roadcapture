package com.tenacy.roadcapture.data.db

import android.os.Parcelable
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.tenacy.roadcapture.data.firebase.dto.FirebaseLocation
import kotlinx.parcelize.Parcelize
import java.time.LocalDateTime

@Entity(tableName = "location_caches",)
@Parcelize
data class LocationCacheEntity(
    @PrimaryKey
    val id: String,
    val albumId: String,
    val latitude: Double,
    val longitude: Double,
    val createdAt: LocalDateTime,
): Parcelable {

    fun toFirebaseLocation() = FirebaseLocation(
        id = id,
        latitude = latitude,
        longitude = longitude,
        createdAt = createdAt,
    )

    companion object {
        fun from(dto: FirebaseLocation, albumId: String) = LocationCacheEntity(
            id = dto.id,
            albumId = albumId,
            latitude = dto.latitude,
            longitude = dto.longitude,
            createdAt = dto.createdAt,
        )
    }
}
