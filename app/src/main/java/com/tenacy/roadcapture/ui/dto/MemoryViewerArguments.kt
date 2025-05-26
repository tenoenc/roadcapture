package com.tenacy.roadcapture.ui.dto

import android.net.Uri
import android.os.Parcelable
import com.google.android.gms.maps.model.LatLng
import com.tenacy.roadcapture.data.db.MemoryWithLocation
import com.tenacy.roadcapture.data.firebase.dto.FirebaseMemory
import com.tenacy.roadcapture.ui.ViewScope
import kotlinx.parcelize.Parcelize
import java.time.LocalDateTime

@Parcelize
data class MemoryViewerArguments(
    val memories: List<Memory>,
    val selectedMemoryId: String? = null,
    val viewScope: ViewScope,
) : Parcelable {
    @Parcelize
    data class Memory(
        val id: String = "",
        val locationId: String = "",
        val content: String? = null,
        val photoUri: Uri? = null,
        val photoUrl: String = "",
        val placeName: String? = null,
        val addressTags: List<String> = emptyList(),
        val formattedAddress: String = "",
        val coordinates: LatLng,
        val createdAt: LocalDateTime,
    ) : Parcelable {
        companion object {
            fun of(dto: MemoryWithLocation) = Memory(
                id = dto.memory.id.toString(),
                locationId = dto.location.id.toString(),
                content = dto.memory.content,
                photoUri = dto.memory.photoUri,
                placeName = dto.memory.placeName,
                addressTags = dto.memory.addressTags,
                formattedAddress = dto.memory.formattedAddress,
                coordinates = LatLng(dto.location.latitude, dto.location.longitude),
                createdAt = dto.memory.createdAt,
            )
            fun from(memory: FirebaseMemory, coordinates: LatLng) = Memory(
                id = memory.id,
                locationId = memory.locationRefId,
                content = memory.content.takeIf { it.isNotBlank() },
                photoUrl = memory.photoUrl,
                placeName = memory.placeName.takeIf { it.isNotBlank() },
                addressTags = memory.addressTags,
                formattedAddress = memory.formattedAddress,
                coordinates = coordinates,
                createdAt = memory.createdAt,
            )
        }
    }
}