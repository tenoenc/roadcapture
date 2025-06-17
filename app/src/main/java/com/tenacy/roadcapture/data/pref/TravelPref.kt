package com.tenacy.roadcapture.data.pref

import android.location.Location
import android.util.Log
import com.chibatching.kotpref.KotprefModel
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.tenacy.roadcapture.util.getCustomLocationFrom
import com.tenacy.roadcapture.util.toLocalDateTime
import com.tenacy.roadcapture.util.toTimestamp
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

object TravelPref : KotprefModel() {

    // ===== 기존 핵심 프로퍼티들 =====
    var isTraveling by booleanPref(default = false, key = "is_traveling")

    private var _thumbnailMemoryId by longPref(default = -1L, key = "thumbnail_memory_id")
    var thumbnailMemoryId: Long?
        set(value) {
            _thumbnailMemoryId = value ?: -1L
        }
        get() = _thumbnailMemoryId.takeIf { it > -1L }

    var lastSavedLatitude by stringPref(default = "0.0", key = "last_saved_latitude")
    var lastSavedLongitude by stringPref(default = "0.0", key = "last_saved_longitude")
    var lastSavedTime by longPref(default = 0L, key = "last_saved_time")
    var travelStartedAt by longPref(default = 0L, key = "travel_started_at")

    // ===== 기존 고속 모드 관련 =====
    var isHighSpeedModeActive by booleanPref(default = false, key = "is_high_speed_mode_active")
    var highSpeedModeStartTime by longPref(default = 0L, key = "high_speed_mode_start_time")
    var consecutiveSpeedFilterCount by intPref(default = 0, key = "consecutive_speed_filter_count")

    // ===== 기존 최근 위치 저장 =====
    var recentLocationsJson by stringPref(default = "", key = "recent_locations_json")

    // ===== 향상된 위치 추적을 위한 새로운 프로퍼티들 =====

    // 이동 수단 감지 관련
    var currentTransportMode by stringPref(default = "UNKNOWN", key = "current_transport_mode")
    var lastTransportModeChangeTime by longPref(default = 0L, key = "last_transport_mode_change_time")

    // 지하/터널 모드 관련
    var isUndergroundMode by booleanPref(default = false, key = "is_underground_mode")
    var lastGoodGpsTime by longPref(default = 0L, key = "last_good_gps_time")
    var undergroundModeStartTime by longPref(default = 0L, key = "underground_mode_start_time")

    // 위치 품질 필터링 관련
    var consecutiveAccuracyFilterCount by intPref(default = 0, key = "consecutive_accuracy_filter_count")
    var lastLocationAccuracy by floatPref(default = 0f, key = "last_location_accuracy")
    var satelliteCount by intPref(default = 0, key = "satellite_count")

    // 칼만 필터 상태 저장
    var kalmanLatitudeEstimate by stringPref(default = "0.0", key = "kalman_latitude_estimate")
    var kalmanLongitudeEstimate by stringPref(default = "0.0", key = "kalman_longitude_estimate")
    var kalmanAltitudeEstimate by stringPref(default = "0.0", key = "kalman_altitude_estimate")
    var kalmanIsInitialized by booleanPref(default = false, key = "kalman_is_initialized")
    var kalmanErrorCovariance by floatPref(default = 1.0f, key = "kalman_error_covariance")

    // 센서 기반 이동 감지 관련
    var lastDetectedMovementTime by longPref(default = 0L, key = "last_detected_movement_time")
    var isMovingState by booleanPref(default = false, key = "is_moving_state")
    var lastAccelerationValue by floatPref(default = 0f, key = "last_acceleration_value")
    var stationaryStartTime by longPref(default = 0L, key = "stationary_start_time")

    // 적응형 업데이트 간격 관리
    var currentUpdateInterval by longPref(default = 15000L, key = "current_update_interval")
    var isAdaptiveMode by booleanPref(default = true, key = "is_adaptive_mode")
    var lastLocationUpdateTime by longPref(default = 0L, key = "last_location_update_time")

    // 배터리 최적화 관련
    var isLowAccuracyMode by booleanPref(default = false, key = "is_low_accuracy_mode")
    var lowAccuracyModeStartTime by longPref(default = 0L, key = "low_accuracy_mode_start_time")
    var consecutiveLocationUpdates by intPref(default = 0, key = "consecutive_location_updates")

    // ===== 기존 핵심 메서드들 (기능 향상) =====

    fun startTravel() {
        isTraveling = true
        val currentTime = System.currentTimeMillis()

        if (travelStartedAt == 0L) {
            travelStartedAt = LocalDateTime.now().toTimestamp()
        }

        // 향상된 추적 시작 시 초기화
        lastGoodGpsTime = currentTime
        isUndergroundMode = false
        consecutiveAccuracyFilterCount = 0
        consecutiveSpeedFilterCount = 0
        currentTransportMode = "UNKNOWN"
        lastTransportModeChangeTime = currentTime
        isMovingState = false
        lastDetectedMovementTime = 0L
        stationaryStartTime = 0L
        isLowAccuracyMode = false
        lowAccuracyModeStartTime = 0L
        consecutiveLocationUpdates = 0

        Log.d("TravelPref", "여행 시작 - 향상된 추적 모드 활성화")
    }

    fun stopTravel() {
        isTraveling = false
        _thumbnailMemoryId = -1L
        lastSavedLatitude = "0.0"
        lastSavedLongitude = "0.0"
        lastSavedTime = 0L
        travelStartedAt = 0L
        isHighSpeedModeActive = false
        highSpeedModeStartTime = 0L
        consecutiveSpeedFilterCount = 0
        recentLocationsJson = ""

        // 향상된 추적 관련 상태 완전 초기화
        currentTransportMode = "UNKNOWN"
        lastTransportModeChangeTime = 0L
        isUndergroundMode = false
        lastGoodGpsTime = 0L
        undergroundModeStartTime = 0L
        consecutiveAccuracyFilterCount = 0
        lastLocationAccuracy = 0f
        satelliteCount = 0

        // 칼만 필터 상태 초기화
        kalmanLatitudeEstimate = "0.0"
        kalmanLongitudeEstimate = "0.0"
        kalmanAltitudeEstimate = "0.0"
        kalmanIsInitialized = false
        kalmanErrorCovariance = 1.0f

        // 센서 관련 상태 초기화
        lastDetectedMovementTime = 0L
        isMovingState = false
        lastAccelerationValue = 0f
        stationaryStartTime = 0L

        // 업데이트 간격 관련 초기화
        currentUpdateInterval = 15000L
        isAdaptiveMode = true
        lastLocationUpdateTime = 0L

        // 배터리 최적화 관련 초기화
        isLowAccuracyMode = false
        lowAccuracyModeStartTime = 0L
        consecutiveLocationUpdates = 0

        Log.d("TravelPref", "여행 종료 - 모든 상태 초기화 완료")
    }

    fun setLastSavedLocation(location: Location) {
        lastSavedLatitude = location.latitude.toString()
        lastSavedLongitude = location.longitude.toString()
        lastSavedTime = System.currentTimeMillis()
        lastLocationAccuracy = location.accuracy

        // 위성 개수 저장 (가능한 경우)
        location.extras?.getInt("satellites", 0)?.let { count ->
            satelliteCount = count
        }
    }

    fun getLastSavedLocation(): Location? {
        if(lastSavedLatitude.toDouble() == 0.0 && lastSavedLongitude.toDouble() == 0.0) {
            return null
        }

        val location = getCustomLocationFrom(lastSavedLatitude.toDouble(), lastSavedLongitude.toDouble())
        location.accuracy = lastLocationAccuracy
        location.time = lastSavedTime

        return location
    }

    // ===== 기존 최근 위치 목록 관련 메서드들 (대폭 향상) =====

    fun saveRecentLocations(locations: List<Pair<Location, Long>>) {
        try {
            val locationDataList = locations.map { (location, timestamp) ->
                EnhancedLocationData(
                    latitude = location.latitude,
                    longitude = location.longitude,
                    accuracy = location.accuracy,
                    timestamp = timestamp,
                    provider = location.provider ?: "unknown",
                    altitude = if (location.hasAltitude()) location.altitude else 0.0,
                    speed = if (location.hasSpeed()) location.speed else 0f,
                    bearing = if (location.hasBearing()) location.bearing else 0f,
                    time = location.time,
                    satellites = location.extras?.getInt("satellites", 0) ?: 0
                )
            }
            val json = Gson().toJson(locationDataList)
            recentLocationsJson = json

            Log.d("TravelPref", "최근 위치 ${locations.size}개 저장 완료")
        } catch (e: Exception) {
            Log.e("TravelPref", "향상된 최근 위치 저장 실패", e)
        }
    }

    fun getRecentLocations(): List<Pair<Location, Long>> {
        val json = recentLocationsJson

        if (json.isEmpty()) return emptyList()

        return try {
            // 새로운 향상된 형식으로 복원 시도
            val type = object : TypeToken<List<EnhancedLocationData>>() {}.type
            val locationDataList: List<EnhancedLocationData> = Gson().fromJson(json, type)

            locationDataList.map { data ->
                val location = getCustomLocationFrom(data.latitude, data.longitude).apply {
                    accuracy = data.accuracy
                    if (data.altitude != 0.0) altitude = data.altitude
                    if (data.speed != 0f) speed = data.speed
                    if (data.bearing != 0f) bearing = data.bearing
                    time = data.time
                    // 위성 개수 저장 (extras 번들에)
                    if (data.satellites > 0) {
                        extras = android.os.Bundle().apply {
                            putInt("satellites", data.satellites)
                        }
                    }
                }
                Pair(location, data.timestamp)
            }
        } catch (e: Exception) {
            Log.w("TravelPref", "향상된 형식 복원 실패, 기존 형식으로 폴백", e)
            // 기존 형식으로 폴백 시도
            try {
                val type = object : TypeToken<List<BasicLocationData>>() {}.type
                val locationDataList: List<BasicLocationData> = Gson().fromJson(json, type)

                locationDataList.map { data ->
                    val location = getCustomLocationFrom(data.latitude, data.longitude).apply {
                        accuracy = data.accuracy
                        time = data.time
                    }
                    Pair(location, data.time)
                }
            } catch (e2: Exception) {
                Log.e("TravelPref", "기존 형식 복원도 실패", e2)
                emptyList()
            }
        }
    }

    // ===== 기존 유틸리티 메서드들 =====

    fun isOverOneMonth(): Boolean {
        if (!isTraveling || travelStartedAt == 0L) {
            return false
        }

        val startDate = travelStartedAt.toLocalDateTime()
        val now = LocalDateTime.now()

        val monthsBetween = ChronoUnit.MONTHS.between(startDate, now)
        return monthsBetween >= 1
    }

    override fun clear() {
        stopTravel()
        super.clear()
    }

    val createdAt: Long
        get() = travelStartedAt

    // ===== 새로운 향상된 기능 메서드들 =====

    // 이동 수단 관리
    fun updateTransportMode(newMode: String): Boolean {
        val wasChanged = currentTransportMode != newMode
        if (wasChanged) {
            Log.d("TravelPref", "이동 수단 변경: $currentTransportMode -> $newMode")
            currentTransportMode = newMode
            lastTransportModeChangeTime = System.currentTimeMillis()
        }
        return wasChanged
    }

    fun getTimeSinceTransportModeChange(): Long {
        return if (lastTransportModeChangeTime > 0) {
            System.currentTimeMillis() - lastTransportModeChangeTime
        } else {
            0L
        }
    }

    // 지하/터널 모드 관리
    fun enterUndergroundMode() {
        if (!isUndergroundMode) {
            isUndergroundMode = true
            undergroundModeStartTime = System.currentTimeMillis()
            Log.d("TravelPref", "지하 모드 진입")
        }
    }

    fun exitUndergroundMode() {
        if (isUndergroundMode) {
            isUndergroundMode = false
            lastGoodGpsTime = System.currentTimeMillis()
            val duration = System.currentTimeMillis() - undergroundModeStartTime
            Log.d("TravelPref", "지하 모드 해제 (지속시간: ${duration/1000}초)")
            undergroundModeStartTime = 0L
        }
    }

    fun getTimeSinceLastGoodGps(): Long {
        return if (lastGoodGpsTime > 0) {
            System.currentTimeMillis() - lastGoodGpsTime
        } else {
            Long.MAX_VALUE
        }
    }

    fun getUndergroundModeDuration(): Long {
        return if (isUndergroundMode && undergroundModeStartTime > 0) {
            System.currentTimeMillis() - undergroundModeStartTime
        } else {
            0L
        }
    }

    // 칼만 필터 상태 관리
    fun saveKalmanState(latEstimate: Double, lonEstimate: Double, altEstimate: Double, errorCovariance: Float) {
        kalmanLatitudeEstimate = latEstimate.toString()
        kalmanLongitudeEstimate = lonEstimate.toString()
        kalmanAltitudeEstimate = altEstimate.toString()
        kalmanErrorCovariance = errorCovariance
        kalmanIsInitialized = true
    }

    fun getKalmanState(): KalmanState {
        return KalmanState(
            latEstimate = kalmanLatitudeEstimate.toDoubleOrNull() ?: 0.0,
            lonEstimate = kalmanLongitudeEstimate.toDoubleOrNull() ?: 0.0,
            altEstimate = kalmanAltitudeEstimate.toDoubleOrNull() ?: 0.0,
            errorCovariance = kalmanErrorCovariance,
            isInitialized = kalmanIsInitialized
        )
    }

    fun resetKalmanFilter() {
        kalmanLatitudeEstimate = "0.0"
        kalmanLongitudeEstimate = "0.0"
        kalmanAltitudeEstimate = "0.0"
        kalmanErrorCovariance = 1.0f
        kalmanIsInitialized = false
        Log.d("TravelPref", "칼만 필터 상태 리셋")
    }

    // 센서 기반 이동 감지 관리
    fun updateMovementState(isMoving: Boolean, acceleration: Float = 0f) {
        val wasMoving = isMovingState
        isMovingState = isMoving
        lastAccelerationValue = acceleration

        if (isMoving) {
            lastDetectedMovementTime = System.currentTimeMillis()
            stationaryStartTime = 0L
            if (!wasMoving) {
                Log.d("TravelPref", "이동 시작 감지 (가속도: ${acceleration}m/s²)")
            }
        } else {
            if (wasMoving && stationaryStartTime == 0L) {
                stationaryStartTime = System.currentTimeMillis()
                Log.d("TravelPref", "정지 상태 시작")
            }
        }
    }

    fun getTimeSinceLastMovement(): Long {
        return if (lastDetectedMovementTime > 0) {
            System.currentTimeMillis() - lastDetectedMovementTime
        } else {
            0L
        }
    }

    fun getStationaryDuration(): Long {
        return if (stationaryStartTime > 0) {
            System.currentTimeMillis() - stationaryStartTime
        } else {
            0L
        }
    }

    // 적응형 업데이트 간격 관리
    fun updateLocationInterval(newInterval: Long) {
        if (currentUpdateInterval != newInterval) {
            Log.d("TravelPref", "업데이트 간격 변경: ${currentUpdateInterval/1000}초 -> ${newInterval/1000}초")
            currentUpdateInterval = newInterval
        }
        lastLocationUpdateTime = System.currentTimeMillis()
        consecutiveLocationUpdates++
    }

    // 배터리 최적화 모드 관리
    fun enterLowAccuracyMode() {
        if (!isLowAccuracyMode) {
            isLowAccuracyMode = true
            lowAccuracyModeStartTime = System.currentTimeMillis()
            Log.d("TravelPref", "저전력 모드 진입")
        }
    }

    fun exitLowAccuracyMode() {
        if (isLowAccuracyMode) {
            isLowAccuracyMode = false
            val duration = System.currentTimeMillis() - lowAccuracyModeStartTime
            Log.d("TravelPref", "저전력 모드 해제 (지속시간: ${duration/1000}초)")
            lowAccuracyModeStartTime = 0L
        }
    }

    fun getLowAccuracyModeDuration(): Long {
        return if (isLowAccuracyMode && lowAccuracyModeStartTime > 0) {
            System.currentTimeMillis() - lowAccuracyModeStartTime
        } else {
            0L
        }
    }

    // 통계 및 디버깅 정보
    fun getTrackingStatistics(): TrackingStatistics {
        return TrackingStatistics(
            travelDuration = if (travelStartedAt > 0) System.currentTimeMillis() - travelStartedAt.toLocalDateTime().toTimestamp() else 0L,
            currentTransportMode = currentTransportMode,
            isUndergroundMode = isUndergroundMode,
            isLowAccuracyMode = isLowAccuracyMode,
            isMoving = isMovingState,
            lastAccuracy = lastLocationAccuracy,
            satelliteCount = satelliteCount,
            consecutiveSpeedFilters = consecutiveSpeedFilterCount,
            consecutiveAccuracyFilters = consecutiveAccuracyFilterCount,
            currentUpdateInterval = currentUpdateInterval,
            timeSinceLastGoodGps = getTimeSinceLastGoodGps(),
            timeSinceLastMovement = getTimeSinceLastMovement()
        )
    }

    // ===== 데이터 클래스들 =====

    // 기존 호환성을 위한 기본 위치 데이터 클래스
    private data class BasicLocationData(
        val latitude: Double,
        val longitude: Double,
        val accuracy: Float,
        val time: Long
    )

    // 향상된 위치 데이터 직렬화를 위한 데이터 클래스
    private data class EnhancedLocationData(
        val latitude: Double,
        val longitude: Double,
        val accuracy: Float,
        val timestamp: Long,
        val provider: String = "unknown",
        val altitude: Double = 0.0,
        val speed: Float = 0f,
        val bearing: Float = 0f,
        val time: Long = 0L,
        val satellites: Int = 0
    )

    // 칼만 필터 상태 데이터 클래스
    data class KalmanState(
        val latEstimate: Double,
        val lonEstimate: Double,
        val altEstimate: Double,
        val errorCovariance: Float,
        val isInitialized: Boolean
    )

    // 추적 통계 데이터 클래스
    data class TrackingStatistics(
        val travelDuration: Long,
        val currentTransportMode: String,
        val isUndergroundMode: Boolean,
        val isLowAccuracyMode: Boolean,
        val isMoving: Boolean,
        val lastAccuracy: Float,
        val satelliteCount: Int,
        val consecutiveSpeedFilters: Int,
        val consecutiveAccuracyFilters: Int,
        val currentUpdateInterval: Long,
        val timeSinceLastGoodGps: Long,
        val timeSinceLastMovement: Long
    )
}