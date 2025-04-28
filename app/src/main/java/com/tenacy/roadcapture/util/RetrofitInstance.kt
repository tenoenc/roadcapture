package com.tenacy.roadcapture.util

import com.tenacy.roadcapture.data.api.GooglePlacesApi
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object RetrofitInstance {
     val placesApi = Retrofit.Builder()
        .baseUrl("https://maps.googleapis.com/maps/api/")
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(GooglePlacesApi::class.java)
}