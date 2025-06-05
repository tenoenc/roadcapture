package com.tenacy.roadcapture.data.pref

import android.location.Location
import android.preference.PreferenceManager
import android.util.Log
import com.chibatching.kotpref.KotprefModel
import com.google.android.gms.maps.model.LatLng
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.tenacy.roadcapture.data.db.RoomConverters
import com.tenacy.roadcapture.util.getCustomLocationFrom
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

    var isHighSpeedModeActive by booleanPref(default = false, key = "is_high_speed_mode_active")
    var highSpeedModeStartTime by longPref(default = 0L, key = "high_speed_mode_start_time")
    var consecutiveSpeedFilterCount by intPref(default = 0, key = "consecutive_speed_filter_count")

    var recentLocationsJson by stringPref(default = "", key = "recent_locations_json")

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
        isHighSpeedModeActive = false
        highSpeedModeStartTime = 0L
        consecutiveSpeedFilterCount = 0
        recentLocationsJson = ""
    }

    fun setLastSavedLocation(location: Location) {
        lastSavedLatitude = location.latitude.toString()
        lastSavedLongitude = location.longitude.toString()
    }

    fun getLastSavedLocation(): Location? {
        if(lastSavedLatitude.toDouble() == 0.0 && lastSavedLongitude.toDouble() == 0.0) return null
        val location = getCustomLocationFrom(lastSavedLatitude.toDouble(), lastSavedLongitude.toDouble())
        return location
    }

    // 최근 위치 목록 관련 메서드 추가
    fun saveRecentLocations(locations: List<Pair<Location, Long>>) {
        val locationDataList = locations.map {
            LocationData(it.first.latitude, it.first.longitude, it.first.accuracy, it.second)
        }
        val json = Gson().toJson(locationDataList)

        recentLocationsJson = json
    }

    fun getRecentLocations(): List<Pair<Location, Long>> {
        val json = recentLocationsJson

        if (json.isEmpty()) return emptyList()

        try {
            val type = object : TypeToken<List<LocationData>>() {}.type
            val locationDataList: List<LocationData> = Gson().fromJson(json, type)

            return locationDataList.map { data ->
                val location = getCustomLocationFrom(data.latitude, data.longitude)
                location.accuracy = data.accuracy
                Pair(location, data.time)
            }
        } catch (e: Exception) {
            Log.e("TravelPref", "최근 위치 목록 복원 실패", e)
            return emptyList()
        }
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

    // 위치 데이터 직렬화를 위한 데이터 클래스
    private data class LocationData(
        val latitude: Double,
        val longitude: Double,
        val accuracy: Float,
        val time: Long
    )
}