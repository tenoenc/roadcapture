package com.tenacy.roadcapture.data.api.dto

import com.google.gson.annotations.SerializedName

data class NominatimReverseResponse(
    @SerializedName("place_id")
    val placeId: Long,
    @SerializedName("display_name")
    val displayName: String?,
    val address: NominatimAddress?,
)

data class NominatimAddress(
    val country: String,
    val otherFields: LinkedHashMap<String, String>
)