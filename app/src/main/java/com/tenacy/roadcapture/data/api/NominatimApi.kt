package com.tenacy.roadcapture.data.api

import com.tenacy.roadcapture.data.api.dto.NominatimReverseResponse
import retrofit2.http.GET
import retrofit2.http.Query

interface NominatimApi {
    @GET("reverse")
    suspend fun reverse(
        @Query("lat") lat: Double,
        @Query("lon") lon: Double,
        @Query("format") format: String = "jsonv2",
        @Query("zoom") zoom: Int = 18,
        @Query("addressdetails") addressDetails: Int = 1,
    ): NominatimReverseResponse
}