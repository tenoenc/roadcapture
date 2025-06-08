package com.tenacy.roadcapture.util

import com.google.gson.GsonBuilder
import com.tenacy.roadcapture.BuildConfig
import com.tenacy.roadcapture.data.api.FirebaseApi
import com.tenacy.roadcapture.data.api.LocationIqApi
import com.tenacy.roadcapture.data.api.NominatimApi
import com.tenacy.roadcapture.data.api.dto.NominatimAddress
import com.tenacy.roadcapture.data.api.dto.NominatimAddressDeserializer
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object RetrofitInstance {
    private val gson = GsonBuilder()
        .registerTypeAdapter(NominatimAddress::class.java, NominatimAddressDeserializer())
        .create()

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = if (BuildConfig.DEBUG) {
            HttpLoggingInterceptor.Level.BODY
        } else {
            HttpLoggingInterceptor.Level.NONE
        }
    }

    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(loggingInterceptor)
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    val nominatimApi = Retrofit.Builder()
        .baseUrl("https://nominatim.openstreetmap.org/")
        .addConverterFactory(GsonConverterFactory.create(gson))
        .client(okHttpClient)
        .build()
        .create(NominatimApi::class.java)

    val locationIqApi = Retrofit.Builder()
        .baseUrl("https://us1.locationiq.com/")
        .addConverterFactory(GsonConverterFactory.create(gson))
        .client(okHttpClient)
        .build()
        .create(LocationIqApi::class.java)

    val firebaseApi = Retrofit.Builder()
        .baseUrl("https://us-central1-roadcapture-457911.cloudfunctions.net/")
        .addConverterFactory(GsonConverterFactory.create(gson))
        .client(okHttpClient)
        .build()
        .create(FirebaseApi::class.java)
}