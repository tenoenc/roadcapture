package com.tenacy.roadcapture.service

import android.location.Location
import com.tenacy.roadcapture.data.db.LocationEntity
import com.tenacy.roadcapture.util.Constants
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface LocationProcessor {
    val gpsStatusFlow: StateFlow<GpsStatus>

    /**
     * 위치 업데이트 처리
     * @param location 새로 수신된 위치
     * @return 저장 성공 여부
     */
    suspend fun processLocation(location: Location): LocationEntity?

    /**
     * 위치 저장 여부 결정
     */
    fun shouldSaveLocation(location: Location): Boolean

    /**
     * 최근 저장된 위치 가져오기
     */
    fun getLastSavedLocation(): Location?

    /**
     * 위치 품질 검사
     */
    fun isLocationQualityAcceptable(location: Location): Boolean

    /**
     * 현재 상태 정보를 영구 저장소에 저장
     */
    fun saveState()

    /**
     * 저장된 상태 정보 복원
     */
    fun restoreState()

    /**
     * 최근 저장된 위치 이벤트 Flow
     */
    fun getSavedLocationsFlow(): Flow<Location>
}

enum class GpsStatus {
    GOOD,           // ~50m: 좋음
    OUTDOOR,        // 50~150m: 야외/산악 모드
    POOR,           // 150~200m: 불량
    UNDERGROUND     // 200m+: 지하모드
}

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