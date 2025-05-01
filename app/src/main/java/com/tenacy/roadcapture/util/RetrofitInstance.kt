package com.tenacy.roadcapture.util

import com.google.gson.GsonBuilder
import com.tenacy.roadcapture.data.api.NominatimApi
import com.tenacy.roadcapture.data.api.dto.NominatimAddress
import com.tenacy.roadcapture.data.api.dto.NominatimAddressDeserializer
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object RetrofitInstance {
    private val gson = GsonBuilder()
        .registerTypeAdapter(NominatimAddress::class.java, NominatimAddressDeserializer())
        .create()

     val nominatimApi = Retrofit.Builder()
        .baseUrl("https://nominatim.openstreetmap.org/")
        .addConverterFactory(GsonConverterFactory.create(gson))
        .build()
        .create(NominatimApi::class.java)
}