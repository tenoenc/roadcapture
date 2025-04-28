package com.tenacy.roadcapture.data.api.dto

data class NearbySearchResponse(
    val results: List<PlaceResult>,
    val status: String
)