package com.tenacy.roadcapture.data.pref

import android.location.Location
import com.chibatching.kotpref.KotprefModel
import com.google.android.gms.maps.model.LatLng
import com.tenacy.roadcapture.util.toLocalDateTime
import com.tenacy.roadcapture.util.toTimestamp
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

object TravelPref : KotprefModel() {
    var isTraveling by booleanPref(default = false, key = "is_traveling")
    var lastSavedLatitude by stringPref(default = "0.0", key = "last_saved_latitude")
    var lastSavedLongitude by stringPref(default = "0.0", key = "last_saved_longitude")
    var lastSavedTime by longPref(default = 0L, key = "last_saved_time")
    var travelStartedAt by longPref(default = 0L, key = "travel_started_at")

    fun startTravel() {
        isTraveling = true
        if (travelStartedAt == 0L) {
            travelStartedAt = LocalDateTime.now().toTimestamp()
        }
    }

    fun stopTravel() {
        isTraveling = false
        lastSavedLatitude = "0.0"
        lastSavedLongitude = "0.0"
        lastSavedTime = 0L
        travelStartedAt = 0L
    }

    fun setLastSavedLatLng(latLng: LatLng) {
        lastSavedLatitude = latLng.latitude.toString()
        lastSavedLongitude = latLng.longitude.toString()
    }

    fun getLastSavedLatLng(): LatLng? {
        if(lastSavedLatitude.toDouble() == 0.0 && lastSavedLongitude.toDouble() == 0.0) return null
        return LatLng(lastSavedLatitude.toDouble(), lastSavedLongitude.toDouble())
    }

    fun setLastSavedLocation(location: Location) {
        lastSavedLatitude = location.latitude.toString()
        lastSavedLongitude = location.longitude.toString()
    }

    fun getLastSavedLocation(): Location? {
        if(lastSavedLatitude.toDouble() == 0.0 && lastSavedLongitude.toDouble() == 0.0) return null
        val location = Location("custom_provider")
        location.latitude = lastSavedLatitude.toDouble()
        location.longitude = lastSavedLongitude.toDouble()
        return location
    }

    fun isOverOneMonth(): Boolean {
        if (!isTraveling || travelStartedAt == 0L) {
            return false
        }

        val startDate = travelStartedAt.toLocalDateTime()
        val now = LocalDateTime.now()

        // ChronoUnit.MONTHS로 차이 계산
        val monthsBetween = ChronoUnit.MONTHS.between(startDate, now)
        return monthsBetween >= 1
    }

    override fun clear() {
        stopTravel()
        super.clear()
    }

    // Album.createdAt을 대체하는 getter
    val createdAt: Long
        get() = travelStartedAt
}