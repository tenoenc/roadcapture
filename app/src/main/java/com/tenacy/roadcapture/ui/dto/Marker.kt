package com.tenacy.roadcapture.ui.dto

import android.location.Location
import android.net.Uri
import android.os.Parcelable
import com.tenacy.roadcapture.data.db.LocationEntity
import com.tenacy.roadcapture.data.db.MemoryWithLocation
import com.tenacy.roadcapture.data.firebase.dto.FirebaseLocation
import com.tenacy.roadcapture.data.firebase.dto.FirebaseMemory
import com.tenacy.roadcapture.util.getCustomLocationFrom
import kotlinx.parcelize.Parcelize
import java.time.LocalDateTime

@Parcelize
data class Marker(
    val id: String,
    val coordinates: Location,
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
            getCustomLocationFrom(location.latitude, location.longitude),
            location.createdAt,
            Photo(
                memory.id,
                photoUrl = memory.photoUrl,
            )
        )

        fun of(dto: FirebaseLocation) = Marker(
            dto.id,
            getCustomLocationFrom(dto.latitude, dto.longitude),
            dto.createdAt,
        )

        fun of(dto: MemoryWithLocation) = Marker(
            dto.location.id.toString(),
            dto.location.coordinates,
            dto.location.createdAt,
            Photo(
                dto.memory.id.toString(),
                photoUri = dto.memory.photoUri,
            )
        )

        fun of(dto: LocationEntity) = Marker(
            dto.id.toString(),
            dto.coordinates,
            dto.createdAt,
        )
    }
}
