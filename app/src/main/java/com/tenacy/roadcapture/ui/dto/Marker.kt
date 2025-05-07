package com.tenacy.roadcapture.ui.dto

import android.net.Uri
import android.os.Parcelable
import com.tenacy.roadcapture.data.db.LocationEntity
import com.tenacy.roadcapture.data.db.MemoryWithLocation
import com.tenacy.roadcapture.data.firebase.dto.FirebaseLocation
import com.tenacy.roadcapture.data.firebase.dto.FirebaseMemory
import kotlinx.parcelize.Parcelize
import java.time.LocalDateTime

@Parcelize
data class Marker(
    val id: String,
    val latitude: Double,
    val longitude: Double,
    val createdAt: LocalDateTime,
    val photo: Photo? = null,
): Parcelable {

    @Parcelize
    data class Photo(
        val id: String,
        val photoUri: Uri? = null,
        val photoUrl: String = "",
    ): Parcelable

    companion object {
        fun from(memory: FirebaseMemory, location: FirebaseLocation) = Marker(
            location.id,
            location.latitude,
            location.longitude,
            location.createdAt,
            Photo(
                memory.id,
                photoUrl = memory.photoUrl,
            )
        )

        fun of(dto: FirebaseLocation) = Marker(
            dto.id,
            dto.latitude,
            dto.longitude,
            dto.createdAt,
        )

        fun of(dto: MemoryWithLocation) = Marker(
            dto.location.id.toString(),
            dto.location.latitude,
            dto.location.longitude,
            dto.location.createdAt,
            Photo(
                dto.memory.id.toString(),
                photoUri = dto.memory.photoUri,
            )
        )

        fun of(dto: LocationEntity) = Marker(
            dto.id.toString(),
            dto.latitude,
            dto.longitude,
            dto.createdAt,
        )
    }
}
