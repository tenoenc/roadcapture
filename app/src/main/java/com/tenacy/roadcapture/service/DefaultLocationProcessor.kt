package com.tenacy.roadcapture.service

import android.content.Context
import android.location.Location
import android.os.Build
import android.util.Log
import com.tenacy.roadcapture.BuildConfig
import com.tenacy.roadcapture.data.db.LocationDao
import com.tenacy.roadcapture.data.db.LocationEntity
import com.tenacy.roadcapture.data.pref.DebugSettings
import com.tenacy.roadcapture.data.pref.TravelPref
import com.tenacy.roadcapture.util.Constants
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import java.time.LocalDateTime
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.*

@Singleton
class DefaultLocationProcessor @Inject constructor(
    @ApplicationContext private val context: Context,
    private val locationDao: LocationDao
) : LocationProcessor {

    private val TAG = "LocationProcessor"

    private val processorScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // 기존 필드들
    private var lastProcessedLocation: Location? = null
    private var lastProcessedTime = 0L
    private var consecutiveSpeedFilterCount = 0
    private var consecutiveAccuracyFilterCount = 0
    private var isHighSpeedModeActive = false
    private var highSpeedModeStartTime = 0L

    // 향상된 기능을 위한 새로운 필드들
    private var currentTransportMode = TransportMode.UNKNOWN
    private var isUndergroundMode = false
    private var lastGoodGpsTime = 0L
    private var satelliteCount = 0
    private var lastKnownAccuracy = Float.MAX_VALUE

    // GPS 점프 감지를 위한 최근 위치 저장
    private val recentLocations = LinkedList<LocationData>()
    private val MAX_RECENT_LOCATIONS = 10

    // 칼만 필터 인스턴스들
    private var kalmanLatitude: KalmanFilter? = null
    private var kalmanLongitude: KalmanFilter? = null
    private var kalmanAltitude: KalmanFilter? = null

    // 위치 이벤트 Flow
    private val _savedLocationsFlow = MutableSharedFlow<Location>(replay = 0)

    // ===== 데이터 클래스 및 열거형 =====

    enum class TransportMode(val speedRange: Pair<Float, Float>) {
        STATIONARY(0f to 2f),
        WALKING(2f to 15f),
        CYCLING(15f to 50f),
        DRIVING(50f to 200f),
        TRAIN(80f to 350f),
        FLIGHT(200f to 1000f),
        UNKNOWN(0f to Float.MAX_VALUE)
    }

    data class LocationData(
        val location: Location,
        val timestamp: Long,
        val speed: Float = 0f,
        val bearing: Float = 0f,
        val accuracy: Float = 0f
    )

    // 간단한 1차원 칼만 필터 구현
    class KalmanFilter(
        private var processNoise: Float = Constants.KALMAN_PROCESS_NOISE_DEFAULT,
        private var measurementNoise: Float = Constants.KALMAN_MEASUREMENT_NOISE_DEFAULT
    ) {
        private var estimate = 0.0
        private var errorCovariance = 1.0
        private var isInitialized = false

        fun update(measurement: Double, accuracy: Float): Double {
            if (!isInitialized) {
                estimate = measurement
                errorCovariance = (accuracy * accuracy).toDouble()
                isInitialized = true
                return estimate
            }

            // 예측 단계
            errorCovariance += processNoise

            // 업데이트 단계
            val measurementVariance = (accuracy * accuracy).toDouble()
            val kalmanGain = errorCovariance / (errorCovariance + measurementVariance)

            estimate += kalmanGain * (measurement - estimate)
            errorCovariance *= (1 - kalmanGain)

            return estimate
        }

        fun getCurrentEstimate(): Double = estimate
        fun isInitialized(): Boolean = isInitialized

        fun reset() {
            isInitialized = false
            estimate = 0.0
            errorCovariance = 1.0
        }
    }

    init {
        restoreState()
        initializeKalmanFilters()
    }

    // ===== 핵심 메서드들 =====

    override suspend fun processLocation(location: Location): LocationEntity? {
        val currentTime = System.currentTimeMillis()

        // 모의 위치 감지
        val isMocked = isLocationMocked(location)
        Log.d(TAG, "위치 수신: provider=${location.provider}, 모의=${isMocked}, 정확도=${location.accuracy}m")

        // 위성 개수 업데이트
        location.extras?.getInt("satellites", 0)?.let { count ->
            satelliteCount = count
        }

        // GPS 신호 품질 확인
        checkGpsSignalQuality(location, currentTime)

        // 모의 위치 처리
        if (!handleMockLocation(isMocked)) {
            return null
        }

        // 위치 품질 검사
        if (!isLocationQualityAcceptable(location)) {
            consecutiveAccuracyFilterCount++
            Log.d(TAG, "품질 불량으로 무시 (연속 ${consecutiveAccuracyFilterCount}회)")

            // 연속으로 너무 많이 필터링되면 임계값 완화
            if (consecutiveAccuracyFilterCount > 20) {
                Log.w(TAG, "연속 품질 문제 - 임계값 완화")
                consecutiveAccuracyFilterCount = 0
                if (location.accuracy < 100f && (currentTime - location.time) < 120_000) {
                    // 100m 정확도, 2분 이내면 허용
                } else {
                    return null
                }
            } else {
                return null
            }
        } else {
            consecutiveAccuracyFilterCount = 0
        }

        // 칼만 필터 적용
        val filteredLocation = applyKalmanFilter(location)

        // GPS 점프 감지
        if (isAdvancedGpsJump(filteredLocation, currentTime)) {
            Log.w(TAG, "GPS 점프 감지 - 위치 무시")
            return null
        }

        // 이동 수단 자동 감지
        detectTransportMode(filteredLocation, currentTime)

        // 위치 저장 여부 결정
        if (shouldSaveLocation(filteredLocation)) {
            return saveLocation(filteredLocation)
        }

        return null
    }

    override fun shouldSaveLocation(location: Location): Boolean {
        val currentTime = System.currentTimeMillis()
        val isDebugMode = BuildConfig.DEBUG

        // 첫 번째 위치는 항상 저장
        if (TravelPref.lastSavedTime == 0L) {
            updateProcessedLocation(location, currentTime)
            Log.d(TAG, "첫 위치 저장")
            return true
        }

        val lastLocation = getLastSavedLocation()
        val distance = lastLocation?.distanceTo(location) ?: 0f
        val timeDiffSinceLastSave = currentTime - TravelPref.lastSavedTime

        // 강제 저장 체크 (이동 수단별 다른 간격)
        val forceInterval = getForceInterval()
        if (timeDiffSinceLastSave > forceInterval) {
            Log.d(TAG, "강제 저장: ${currentTransportMode} 모드, ${timeDiffSinceLastSave/1000}초 경과")
            updateProcessedLocation(location, currentTime)
            return true
        }

        // 지하 모드에서는 저장 건너뛰기 (단, 모의 위치는 예외)
        if (isUndergroundMode && !isLocationMocked(location)) {
            Log.d(TAG, "지하 모드 - 저장 건너뜀 (실제 GPS만)")
            updateProcessedLocation(location, currentTime)
            return false
        }

        // 속도 기반 필터링 및 저장 결정
        return evaluateLocationForSaving(location, currentTime, distance, isDebugMode)
    }

    override fun isLocationQualityAcceptable(location: Location): Boolean {
        // 이동 수단별 정확도 임계값
        val accuracyThreshold = when (currentTransportMode) {
            TransportMode.STATIONARY -> 20f
            TransportMode.WALKING -> 30f
            TransportMode.CYCLING -> 40f
            TransportMode.DRIVING -> 50f
            TransportMode.TRAIN -> 70f
            TransportMode.FLIGHT -> 100f
            TransportMode.UNKNOWN -> 50f
        }

        if (location.accuracy > accuracyThreshold) {
            return false
        }

        // 이동 수단별 위치 데이터 최대 나이
        val maxAge = when (currentTransportMode) {
            TransportMode.STATIONARY -> 300_000L  // 5분
            TransportMode.WALKING -> 120_000L     // 2분
            TransportMode.CYCLING -> 60_000L      // 1분
            TransportMode.DRIVING -> 30_000L      // 30초
            TransportMode.TRAIN -> 60_000L        // 1분
            TransportMode.FLIGHT -> 120_000L      // 2분
            TransportMode.UNKNOWN -> 60_000L      // 1분
        }

        val locationAge = System.currentTimeMillis() - location.time
        if (locationAge > maxAge) {
            return false
        }

        // 위성 개수 체크
        if (satelliteCount > 0 && satelliteCount < 4 && location.accuracy > 20f) {
            return false
        }

        return true
    }

    override fun getLastSavedLocation(): Location? {
        return TravelPref.getLastSavedLocation()
    }

    override fun saveState() {
        // 기존 상태 저장
        lastProcessedLocation?.let { TravelPref.setLastSavedLocation(it) }
        TravelPref.lastSavedTime = lastProcessedTime
        TravelPref.consecutiveSpeedFilterCount = consecutiveSpeedFilterCount
        TravelPref.isHighSpeedModeActive = isHighSpeedModeActive
        TravelPref.highSpeedModeStartTime = highSpeedModeStartTime

        // 향상된 상태 저장
        TravelPref.updateTransportMode(currentTransportMode.name)
        TravelPref.consecutiveAccuracyFilterCount = consecutiveAccuracyFilterCount

        if (isUndergroundMode) {
            TravelPref.enterUndergroundMode()
        } else {
            TravelPref.exitUndergroundMode()
        }

        // 칼만 필터 상태 저장
        kalmanLatitude?.let { filter ->
            if (filter.isInitialized()) {
                TravelPref.saveKalmanState(
                    filter.getCurrentEstimate(),
                    kalmanLongitude?.getCurrentEstimate() ?: 0.0,
                    kalmanAltitude?.getCurrentEstimate() ?: 0.0,
                    1.0f // errorCovariance는 간단화
                )
            }
        }

        // 최근 위치 기록 저장
        TravelPref.saveRecentLocations(recentLocations.map {
            Pair(it.location, it.timestamp)
        })

        Log.d(TAG, "상태 저장 완료")
    }

    override fun restoreState() {
        // 기존 상태 복원
        lastProcessedLocation = TravelPref.getLastSavedLocation()
        lastProcessedTime = TravelPref.lastSavedTime
        consecutiveSpeedFilterCount = TravelPref.consecutiveSpeedFilterCount
        isHighSpeedModeActive = TravelPref.isHighSpeedModeActive
        highSpeedModeStartTime = TravelPref.highSpeedModeStartTime

        // 향상된 상태 복원
        currentTransportMode = try {
            TransportMode.valueOf(TravelPref.currentTransportMode)
        } catch (e: Exception) {
            TransportMode.UNKNOWN
        }

        consecutiveAccuracyFilterCount = TravelPref.consecutiveAccuracyFilterCount
        isUndergroundMode = TravelPref.isUndergroundMode
        lastGoodGpsTime = TravelPref.lastGoodGpsTime

        // 최근 위치 기록 복원
        val storedLocations = TravelPref.getRecentLocations()
        if (storedLocations.isNotEmpty()) {
            recentLocations.clear()
            recentLocations.addAll(storedLocations.map {
                LocationData(it.first, it.second)
            })
        }

        Log.d(TAG, "상태 복원 완료: 모드=${currentTransportMode}, 지하=${isUndergroundMode}")
    }

    override fun getSavedLocationsFlow(): Flow<Location> {
        return _savedLocationsFlow
    }

    // ===== 헬퍼 메서드들 =====

    private fun initializeKalmanFilters() {
        kalmanLatitude = KalmanFilter()
        kalmanLongitude = KalmanFilter()
        kalmanAltitude = KalmanFilter(
            processNoise = Constants.KALMAN_PROCESS_NOISE_DEFAULT * 2,
            measurementNoise = Constants.KALMAN_MEASUREMENT_NOISE_DEFAULT * 2
        )
    }

    private fun checkGpsSignalQuality(location: Location, currentTime: Long) {
        val isMocked = isLocationMocked(location)

        // 모의 위치는 지하 모드 판단에서 제외
        if (isMocked && BuildConfig.DEBUG) {
            Log.d(TAG, "모의 위치 감지 - 지하 모드 판단 로직 건너뜀 (현재 지하모드: $isUndergroundMode)")
            lastGoodGpsTime = currentTime  // 모의 위치도 "좋은 신호"로 취급
            if (isUndergroundMode) {
                Log.d(TAG, "모의 위치 감지 - 지하 모드 강제 해제")
                isUndergroundMode = false
                TravelPref.exitUndergroundMode()
            }
            lastKnownAccuracy = location.accuracy
            return
        }

        val isGoodSignal = location.accuracy <= 20f && satelliteCount >= 4

        if (isGoodSignal) {
            lastGoodGpsTime = currentTime
            if (isUndergroundMode) {
                Log.d(TAG, "GPS 신호 복구 - 지하 모드 해제")
                isUndergroundMode = false
                TravelPref.exitUndergroundMode()
            }
        } else {
            // 5분 이상 신호 불량 시 지하 모드 활성화 (실제 GPS만)
            if (currentTime - lastGoodGpsTime > Constants.LOW_ACCURACY_TIMEOUT) {
                if (!isUndergroundMode) {
                    Log.d(TAG, "GPS 신호 장기간 불량 - 지하 모드 활성화")
                    isUndergroundMode = true
                    TravelPref.enterUndergroundMode()
                }
            }
        }

        lastKnownAccuracy = location.accuracy
    }

    private fun handleMockLocation(isMocked: Boolean): Boolean {
        if (BuildConfig.DEBUG) {
            if (DebugSettings.useMockLocationInDebugMode) {
                if (!isMocked) {
                    Log.w(TAG, "실제 GPS 무시 - 모의 위치 전용 모드")
                    return false
                }
            } else {
                if (isMocked) {
                    Log.w(TAG, "모의 위치 무시 - 디버그 모드")
                    return false
                }
            }
        } else {
            if (isMocked) {
                Log.w(TAG, "모의 위치 무시 - 릴리즈 모드")
                return false
            }
        }
        return true
    }

    private fun applyKalmanFilter(location: Location): Location {
        // 칼만 필터 초기화 확인
        if (kalmanLatitude?.isInitialized() != true) {
            initializeKalmanFilters()
        }

        val accuracy = maxOf(location.accuracy, 1.0f)

        // 각 좌표에 칼만 필터 적용
        val filteredLat = kalmanLatitude?.update(location.latitude, accuracy) ?: location.latitude
        val filteredLon = kalmanLongitude?.update(location.longitude, accuracy) ?: location.longitude
        val filteredAlt = if (location.hasAltitude()) {
            kalmanAltitude?.update(location.altitude, accuracy * 2) ?: location.altitude
        } else {
            location.altitude
        }

        // 새로운 Location 객체 생성
        return createFilteredLocation(location, filteredLat, filteredLon, filteredAlt)
    }

    private fun createFilteredLocation(original: Location, lat: Double, lon: Double, alt: Double): Location {
        return Location(original.provider).apply {
            latitude = lat
            longitude = lon
            if (original.hasAltitude()) altitude = alt
            if (original.hasSpeed()) speed = original.speed
            if (original.hasBearing()) bearing = original.bearing
            time = original.time

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
                elapsedRealtimeNanos = original.elapsedRealtimeNanos
            }

            // extras 복사
            if (original.extras != null) {
                extras = android.os.Bundle(original.extras)
            }
        }.also { filteredLocation ->
            // 정확도 향상 반영 (reflection 사용)
            try {
                val accuracyField = Location::class.java.getDeclaredField("mAccuracy")
                accuracyField.isAccessible = true
                accuracyField.setFloat(filteredLocation, original.accuracy * 0.8f)
            } catch (e: Exception) {
                // reflection 실패해도 계속 진행
            }
        }
    }

    private fun detectTransportMode(location: Location, currentTime: Long) {
        val processedLocation = lastProcessedLocation
        val timeDiff = if (lastProcessedTime > 0) currentTime - lastProcessedTime else 0L

        if (processedLocation != null && timeDiff > 0) {
            val distance = processedLocation.distanceTo(location)
            val speedMps = distance / (timeDiff / 1000.0f)
            val speedKmh = speedMps * 3.6f

            // 이동 수단 분류
            val detectedMode = when {
                speedKmh < 2f -> TransportMode.STATIONARY
                speedKmh < 15f -> TransportMode.WALKING
                speedKmh < 50f -> TransportMode.CYCLING
                speedKmh < 200f -> TransportMode.DRIVING
                speedKmh < 350f -> TransportMode.TRAIN
                speedKmh < 1000f -> TransportMode.FLIGHT
                else -> TransportMode.UNKNOWN
            }

            // 이동 수단 변경 감지 및 칼만 필터 조정
            if (detectedMode != currentTransportMode) {
                Log.d(TAG, "이동 수단 변경: ${currentTransportMode} → ${detectedMode} (${speedKmh.toInt()}km/h)")
                currentTransportMode = detectedMode
                TravelPref.updateTransportMode(detectedMode.name)
                adjustKalmanFilterParameters(detectedMode)
            }
        }
    }

    private fun adjustKalmanFilterParameters(mode: TransportMode) {
        val (processNoise, measurementNoise) = when (mode) {
            TransportMode.STATIONARY -> 0.001f to 0.5f
            TransportMode.WALKING -> 0.01f to 1.0f
            TransportMode.CYCLING -> 0.05f to 2.0f
            TransportMode.DRIVING -> 0.1f to 3.0f
            TransportMode.TRAIN -> 0.2f to 5.0f
            TransportMode.FLIGHT -> 0.5f to 10.0f
            TransportMode.UNKNOWN -> 0.1f to 3.0f
        }

        // 칼만 필터 재초기화
        kalmanLatitude = KalmanFilter(processNoise, measurementNoise)
        kalmanLongitude = KalmanFilter(processNoise, measurementNoise)
        kalmanAltitude = KalmanFilter(processNoise * 2, measurementNoise * 2)

        Log.d(TAG, "칼만 필터 조정: 모드=$mode, 노이즈=$processNoise")
    }

    private fun isAdvancedGpsJump(newLocation: Location, currentTime: Long): Boolean {
        synchronized(recentLocations) {
            val locationData = LocationData(
                location = newLocation,
                timestamp = currentTime,
                speed = if (newLocation.hasSpeed()) newLocation.speed else 0f,
                bearing = if (newLocation.hasBearing()) newLocation.bearing else 0f,
                accuracy = newLocation.accuracy
            )

            recentLocations.add(locationData)
            while (recentLocations.size > MAX_RECENT_LOCATIONS) {
                recentLocations.removeFirst()
            }

            return if (recentLocations.size >= 3) {
                detectAdvancedAnomalies()
            } else {
                false
            }
        }
    }

    private fun detectAdvancedAnomalies(): Boolean {
        val recent = recentLocations.takeLast(3)
        val loc1 = recent[0]
        val loc2 = recent[1]
        val loc3 = recent[2]

        val timeDiff1 = (loc2.timestamp - loc1.timestamp) / 1000.0f
        val timeDiff2 = (loc3.timestamp - loc2.timestamp) / 1000.0f

        if (timeDiff1 <= 0 || timeDiff2 <= 0) return false

        val distance1 = loc1.location.distanceTo(loc2.location)
        val distance2 = loc2.location.distanceTo(loc3.location)
        val speed1 = distance1 / timeDiff1
        val speed2 = distance2 / timeDiff2

        // 급격한 가속도 변화 감지
        val acceleration = abs(speed2 - speed1) / ((timeDiff1 + timeDiff2) / 2)
        if (acceleration > Constants.MAX_REASONABLE_ACCELERATION) {
            val bearing1 = loc1.location.bearingTo(loc2.location)
            val bearing2 = loc2.location.bearingTo(loc3.location)
            val bearingDiff = abs((bearing1 - bearing2 + 360) % 360)

            if (bearingDiff > Constants.MAX_BEARING_CHANGE && bearingDiff < 220f) {
                Log.w(TAG, "GPS 점프: 가속도=${acceleration}m/s², 방향차=${bearingDiff}°")
                return true
            }
        }

        // 정확도 급변 감지
        val accuracyChange1 = abs(loc2.accuracy - loc1.accuracy)
        val accuracyChange2 = abs(loc3.accuracy - loc2.accuracy)
        if (accuracyChange1 > 30f && accuracyChange2 > 30f) {
            Log.w(TAG, "정확도 급변: ${accuracyChange1}m, ${accuracyChange2}m")
            return true
        }

        return false
    }

    private fun getForceInterval(): Long {
        return when (currentTransportMode) {
            TransportMode.STATIONARY -> 600_000L  // 10분
            TransportMode.WALKING -> 300_000L     // 5분
            TransportMode.CYCLING -> 180_000L     // 3분
            TransportMode.DRIVING -> 120_000L     // 2분
            TransportMode.TRAIN -> 180_000L       // 3분
            TransportMode.FLIGHT -> 300_000L      // 5분
            TransportMode.UNKNOWN -> 300_000L     // 5분
        }
    }

    private fun evaluateLocationForSaving(
        location: Location,
        currentTime: Long,
        distance: Float,
        isDebugMode: Boolean
    ): Boolean {
        val processedLocation = lastProcessedLocation ?: getLastSavedLocation()
        val processedTimeDiff = if (lastProcessedTime > 0) {
            currentTime - lastProcessedTime
        } else {
            currentTime - TravelPref.lastSavedTime
        }
        val processedDistance = processedLocation?.distanceTo(location) ?: 0f

        var shouldSave = false

        if (processedTimeDiff > 0 && processedLocation != null) {
            val speedMps = processedDistance / (processedTimeDiff / 1000.0f)
            val speedKmh = speedMps * 3.6f

            // 이동 수단별 속도 제한
            val speedLimit = when (currentTransportMode) {
                TransportMode.WALKING -> 20f
                TransportMode.CYCLING -> 60f
                TransportMode.DRIVING -> 250f
                TransportMode.TRAIN -> 400f
                TransportMode.FLIGHT -> 1200f
                else -> 400f
            }

            if (speedKmh > speedLimit) {
                Log.w(TAG, "비정상 속도: ${speedKmh.toInt()}km/h (모드: $currentTransportMode)")
                consecutiveSpeedFilterCount++

                if (consecutiveSpeedFilterCount > 15) {
                    Log.w(TAG, "연속 속도 필터링 - 강제 저장")
                    consecutiveSpeedFilterCount = 0
                    detectTransportMode(location, currentTime)
                    shouldSave = true
                }
            } else {
                consecutiveSpeedFilterCount = 0

                // 이동 수단별 최소 거리
                val minDistance = when (currentTransportMode) {
                    TransportMode.STATIONARY -> 5f
                    TransportMode.WALKING -> 10f
                    TransportMode.CYCLING -> 20f
                    TransportMode.DRIVING -> 30f
                    TransportMode.TRAIN -> 50f
                    TransportMode.FLIGHT -> 200f
                    TransportMode.UNKNOWN -> Constants.MIN_DISTANCE_TO_SAVE
                }

                shouldSave = if (isDebugMode) {
                    distance >= minDistance && (currentTime - TravelPref.lastSavedTime) >= Constants.TRACKING_INTERVAL
                } else {
                    distance >= minDistance
                }
            }
        }

        updateProcessedLocation(location, currentTime)
        return shouldSave
    }

    private fun updateProcessedLocation(location: Location, currentTime: Long) {
        lastProcessedLocation = location
        lastProcessedTime = currentTime
    }

    private fun isLocationMocked(location: Location): Boolean {
        return when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> location.isMock
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2 -> location.isFromMockProvider
            else -> location.provider == "mock"
        }
    }

    private suspend fun saveLocation(location: Location): LocationEntity? {
        val locationEntity = LocationEntity(
            coordinates = location,
            createdAt = LocalDateTime.now()
        )

        return try {
            val id = locationDao.insert(locationEntity)
            val savedEntity = locationEntity.copy(id = id)

            // 상태 업데이트
            TravelPref.setLastSavedLocation(location)
            TravelPref.lastSavedTime = System.currentTimeMillis()

            Log.d(TAG, "위치 저장: ${location.latitude}, ${location.longitude} (모드: $currentTransportMode, 정확도: ${location.accuracy}m)")

            // Flow 전송
            _savedLocationsFlow.emit(location)

            savedEntity
        } catch (e: Exception) {
            Log.e(TAG, "위치 저장 실패", e)
            null
        }
    }
}