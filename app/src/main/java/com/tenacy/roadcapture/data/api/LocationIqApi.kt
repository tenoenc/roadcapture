package com.tenacy.roadcapture.data.api

import android.os.Build.VERSION_CODES.P
import com.tenacy.roadcapture.data.api.dto.NominatimReverseResponse
import retrofit2.http.GET
import retrofit2.http.Query
import java.util.*

interface LocationIqApi {
    @GET("v1/reverse")  // Nominatim: /reverse
    suspend fun reverse(
        @Query("key") apiKey: String,
        @Query("lat") lat: Double,
        @Query("lon") lon: Double,
        @Query("format") format: String = "json",
        @Query("addressdetails") addressDetails: Int = 1,
        @Query("accept-language") language: String = "${Locale.getDefault().language},en"
    ): NominatimReverseResponse
}