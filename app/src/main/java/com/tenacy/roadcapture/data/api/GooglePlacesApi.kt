package com.tenacy.roadcapture.data.api

import com.tenacy.roadcapture.data.api.dto.NearbySearchResponse
import retrofit2.http.GET
import retrofit2.http.Query

interface GooglePlacesApi {
    @GET("place/nearbysearch/json")
    suspend fun nearbySearch(
        @Query("location") location: String,
        @Query("radius") radius: Int,
        @Query("key") apiKey: String
    ): NearbySearchResponse
}