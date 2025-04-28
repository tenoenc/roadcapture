package com.tenacy.roadcapture.data.pref

import com.chibatching.kotpref.KotprefModel
import com.google.android.gms.maps.model.LatLng

object Album: KotprefModel() {

    var createdAt: Long by longPref(0L)
    var lastLatitude: String? by nullableStringPref(null)
    var lastLongitude: String? by nullableStringPref(null)

    private fun getLastLatitude(): Double? = lastLatitude?.let(String::toDoubleOrNull)
    private fun getLastLongitude(): Double? = lastLongitude?.let(String::toDoubleOrNull)
    fun getLastLocation(): LatLng? {
        val lastLatitude = getLastLatitude()
        val lastLongitude = getLastLongitude()
        return if(lastLatitude != null && lastLongitude != null) {
            LatLng(lastLatitude, lastLongitude)
        } else {
            null
        }
    }

    fun saveLastLocation(lastLocation: LatLng?) {
        lastLatitude = lastLocation?.latitude.toString()
        lastLongitude = lastLocation?.longitude.toString()
    }
}