package com.tenacy.roadcapture.util

import com.google.gson.GsonBuilder
import com.tenacy.roadcapture.data.api.FirebaseApi
import com.tenacy.roadcapture.data.api.NominatimApi
import com.tenacy.roadcapture.data.api.dto.NominatimAddress
import com.tenacy.roadcapture.data.api.dto.NominatimAddressDeserializer
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object RetrofitInstance {
    private val gson = GsonBuilder()
        .registerTypeAdapter(NominatimAddress::class.java, NominatimAddressDeserializer())
        .create()

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    val nominatimApi = Retrofit.Builder()
        .baseUrl("https://nominatim.openstreetmap.org/")
        .addConverterFactory(GsonConverterFactory.create(gson))
        .client(client)
        .build()
        .create(NominatimApi::class.java)

    val firebaseApi = Retrofit.Builder()
        .baseUrl("https://us-central1-roadcapture-457911.cloudfunctions.net/")
        .addConverterFactory(GsonConverterFactory.create(gson))
        .client(client)
        .build()
        .create(FirebaseApi::class.java)
}