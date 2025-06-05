package com.tenacy.roadcapture.service

import android.content.Context
import android.content.pm.PackageManager
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

@Singleton
class DefaultLocationProcessor @Inject constructor(
    @ApplicationContext private val context: Context,
    private val locationDao: LocationDao
) : LocationProcessor {

    private val TAG = "LocationProcessor"

    private val processorScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // 위치 캐싱 관련 필드
    private val locationCache = mutableListOf<Location>()
    private val MAX_CACHE_SIZE = 5

    // 이동 모드 관련 필드
    private var lastProcessedLocation: Location? = null
    private var lastProcessedTime = 0L
    private var consecutiveSpeedFilterCount = 0
    private var isHighSpeedModeActive = false
    private var highSpeedModeStartTime = 0L

    // GPS 튐 감지 관련 필드
    private val recentLocations = LinkedList<Pair<Location, Long>>()
    private val MAX_RECENT_LOCATIONS = 5
    private val MAX_REASONABLE_ACCELERATION = 15.0f  // m/s²

    // 센서 관련 필드
    private var isMoving = false

    // 위치 이벤트 Flow
    private val _savedLocationsFlow = MutableSharedFlow<Location>(replay = 0)

    init {
        // 상태 복원
        restoreState()
    }

    fun setMovingState(moving: Boolean) {
        isMoving = moving
    }

    override suspend fun processLocation(location: Location): LocationEntity? {
        // 디버그 로그 추가
        val isMocked = isLocationMocked(location)
//        val isSuspicious = isLocationSuspicious(location)

        Log.d(TAG, "위치 정보: provider=${location.provider}, isMocked=${isMocked}, accuracy=${location.accuracy}, useMockMode=${DebugSettings.useMockLocationInDebugMode}")

        // GPS 튐 감지
        if (isGpsJump(location)) {
            Log.w(TAG, "GPS 튐 현상 감지됨 - 위치 무시")
            return null
        }

        // 디버그 모드에서 설정에 따라 처리
        if (BuildConfig.DEBUG) {
            if (DebugSettings.useMockLocationInDebugMode) {
                // 모의 위치만 사용 모드: 의심스럽거나 모의 위치가 아니면 무시
                if (!isMocked) {
                    Log.w(TAG, "실제 GPS 위치 무시됨 - 모의 위치만 사용 모드")
                    return null
                }
            } else {
                // 모의 위치 차단 모드: 모의 위치는 무시
                if (isMocked) {
                    Log.w(TAG, "디버그 모드에서 모의/의심 위치 무시됨 (릴리즈 모드와 동일)")
                    return null
                }
            }
        } else {
            // 릴리즈 모드: 모의 위치 차단
            if (isMocked) {
                Log.w(TAG, "릴리즈 모드에서 모의/의심 위치 무시됨")
                return null
            }
        }

        // 위치 저장 로직
        if (shouldSaveLocation(location)) {
            return saveLocation(location)
        }

        return null
    }

    override fun shouldSaveLocation(location: Location): Boolean {
        val currentTime = System.currentTimeMillis()
        val isDebugMode = BuildConfig.DEBUG

        // 첫 번째 위치인 경우
        if (TravelPref.lastSavedTime == 0L) {
            TravelPref.lastSavedTime = currentTime
            lastProcessedLocation = location
            lastProcessedTime = currentTime
            Log.d(TAG, "첫 위치 저장")
            return true
        }

        // 마지막 저장된 위치가 아닌 마지막 처리된 위치를 기준으로 계산
        val lastLocation = getLastSavedLocation()
        val distance = lastLocation?.distanceTo(location) ?: 0f

        // 마지막 저장 후 시간 간격 (저장 기준)
        val timeDiffSinceLastSave = currentTime - TravelPref.lastSavedTime

        // 안전장치 1: 마지막 위치 저장 후 너무 오랜 시간이 지나면 강제 저장 (5분)
        val FORCE_SAVE_INTERVAL = 300_000L
        if (timeDiffSinceLastSave > FORCE_SAVE_INTERVAL) {
            Log.d(TAG, "마지막 저장 후 ${timeDiffSinceLastSave/1000}초 경과 - 강제 위치 저장")
            // 고속 모드 해제
            isHighSpeedModeActive = false
            lastProcessedLocation = location
            lastProcessedTime = currentTime
            return true
        }

        // 마지막 처리된 위치와 시간으로 계산 (더 짧은 간격)
        val processedLocation = lastProcessedLocation ?: lastLocation
        val processedTimeDiff = if (lastProcessedTime > 0) currentTime - lastProcessedTime else timeDiffSinceLastSave
        val processedDistance = processedLocation?.distanceTo(location) ?: 0f

        // 비행기 -> 차량 전환 감지를 위한 속도 계산
        var currentSpeed = 0f
        if (processedTimeDiff > 0 && processedLocation != null) {
            val speedMps = processedDistance / (processedTimeDiff / 1000.0f)
            val speedKmh = speedMps * 3.6f  // m/s를 km/h로 변환
            currentSpeed = speedKmh

            // 현재 속도 로깅 (디버그 모드이고 걷는 속도 이상일 때)
            if (isDebugMode && speedKmh > 5.0f) {
                Log.d(TAG, "현재 이동 속도: ${speedKmh.toInt()}km/h (고속 모드: $isHighSpeedModeActive)")
            }

            // 고속 이동 모드 전환 관리
            val HIGH_SPEED_THRESHOLD = 300.0f  // 고속 모드 진입 임계값 (300km/h)
            val LOW_SPEED_THRESHOLD = 200.0f   // 고속 모드 해제 임계값 (200km/h)

            if (!isHighSpeedModeActive && speedKmh > HIGH_SPEED_THRESHOLD) {
                // 고속 모드 진입
                isHighSpeedModeActive = true
                highSpeedModeStartTime = currentTime
                Log.d(TAG, "고속 이동 모드 활성화: ${speedKmh.toInt()}km/h")
            } else if (isHighSpeedModeActive && speedKmh < LOW_SPEED_THRESHOLD) {
                // 고속 모드에서 저속 모드로 전환 감지
                Log.d(TAG, "고속에서 저속으로 전환 감지: ${speedKmh.toInt()}km/h")
                isHighSpeedModeActive = false

                // 고속 모드가 30분 이상 지속된 경우 비행기로 간주하고 저장
                if (currentTime - highSpeedModeStartTime > 30 * 60 * 1000) {
                    Log.d(TAG, "장시간 고속 이동 후 속도 감소 - 이동수단 전환으로 판단")
                    lastProcessedLocation = location
                    lastProcessedTime = currentTime
                    return true
                }
            }

            // 비행기 속도 필터링 (약 400km/h 이상)
            val MAX_ALLOWED_SPEED_KMH = 400.0f

            if (speedKmh > MAX_ALLOWED_SPEED_KMH) {
                Log.w(TAG, "위치 저장 건너뜀 - 비정상적으로 빠른 속도: ${speedKmh.toInt()}km/h")

                // 연속 필터링 카운터 증가
                consecutiveSpeedFilterCount++

                // 연속 10회 이상 속도 필터링되면 강제 저장
                if (consecutiveSpeedFilterCount > 10) {
                    Log.w(TAG, "연속 ${consecutiveSpeedFilterCount}회 속도 필터링 감지 - 강제 저장")
                    consecutiveSpeedFilterCount = 0
                    lastProcessedLocation = location
                    lastProcessedTime = currentTime
                    return true
                }

                // 처리된 위치는 업데이트하되 저장하지는 않음
                lastProcessedLocation = location
                lastProcessedTime = currentTime
                return false
            }
        }

        // 필터링되지 않았으므로 카운터 리셋
        consecutiveSpeedFilterCount = 0

        // 움직임이 감지되지 않았고, 거리가 매우 작다면 노이즈로 간주
        if (!isMoving && distance < Constants.MIN_DISTANCE_TO_SAVE * 0.5) {
            Log.v(TAG, "위치 저장 건너뜀 - 움직임 없음 & 거리 작음: ${distance}m")
            lastProcessedLocation = location
            lastProcessedTime = currentTime
            return false
        }

        // 디버그 모드에서는 시간과 거리 조건 모두 만족해야 함
        var shouldSave = false
        if (isDebugMode) {
            shouldSave = distance >= Constants.MIN_DISTANCE_TO_SAVE && timeDiffSinceLastSave >= Constants.TRACKING_INTERVAL
        } else {
            // 릴리즈 모드에서는 거리만 체크
            shouldSave = distance >= Constants.MIN_DISTANCE_TO_SAVE
        }

        // 처리된 위치 업데이트
        lastProcessedLocation = location
        lastProcessedTime = currentTime

        return shouldSave
    }

    override fun isLocationQualityAcceptable(location: Location): Boolean {
        // 정확도가 너무 낮은 위치는 거부 (예: 50m 이상의 오차)
        if (location.accuracy > 50f) {
            return false
        }

        // 너무 오래된 위치 데이터는 거부 (1분 이상)
        val locationAgeMs = System.currentTimeMillis() - location.time
        if (locationAgeMs > 60_000) {
            return false
        }

        return true
    }

    override fun getLastSavedLocation(): Location? {
        return TravelPref.getLastSavedLocation()
    }

    override fun saveState() {
        // 상태 변수 저장
        lastProcessedLocation?.let { TravelPref.setLastSavedLocation(it) }
        TravelPref.lastSavedTime = lastProcessedTime
        TravelPref.consecutiveSpeedFilterCount = consecutiveSpeedFilterCount
        TravelPref.isHighSpeedModeActive = isHighSpeedModeActive
        TravelPref.highSpeedModeStartTime = highSpeedModeStartTime

        // 최근 위치 기록 저장
        TravelPref.saveRecentLocations(recentLocations.toList())
    }

    override fun restoreState() {
        // 상태 변수 복원
        lastProcessedLocation = TravelPref.getLastSavedLocation()
        lastProcessedTime = TravelPref.lastSavedTime
        consecutiveSpeedFilterCount = TravelPref.consecutiveSpeedFilterCount
        isHighSpeedModeActive = TravelPref.isHighSpeedModeActive
        highSpeedModeStartTime = TravelPref.highSpeedModeStartTime

        // 최근 위치 기록 복원
        val storedLocations = TravelPref.getRecentLocations()
        if (storedLocations.isNotEmpty()) {
            recentLocations.clear()
            recentLocations.addAll(storedLocations)
        }
    }

    override fun getSavedLocationsFlow(): Flow<Location> {
        return _savedLocationsFlow
    }

    private fun isLocationMocked(location: Location): Boolean {
        // API 31+ 내장 감지
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return location.isMock
        }

        // API 18+ 내장 감지
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
            return location.isFromMockProvider
        }

        // mock 제공자 확인 (이름이 "mock"인 경우만)
        return location.provider == "mock"
    }

    private fun isLocationSuspicious(location: Location): Boolean {
        // 디버그 모드에서는 조작 앱 설치 여부도 체크
        if (BuildConfig.DEBUG && hasLocationMockingApp()) {
            // 정확도가 너무 완벽한 경우 (실제 GPS는 보통 약간의 오차가 있음)
            if (location.accuracy < 1.0f) {
                Log.v(TAG, "의심스러운 위치: 비현실적으로 높은 정확도 ${location.accuracy}m")
                return true
            }
        }

        // 시간 체크 (위치 시간이 현재 시간보다 너무 미래이거나 과거)
        val currentTime = System.currentTimeMillis()
        val timeDiff = Math.abs(location.time - currentTime)
        if (timeDiff > 60_000) { // 1분 이상 차이
            Log.v(TAG, "의심스러운 위치: 시간 차이 ${timeDiff}ms")
            return true
        }

        // Provider가 null이거나 빈 문자열인 경우
        if (location.provider.isNullOrEmpty()) {
            Log.v(TAG, "의심스러운 위치: Provider가 없음")
            return true
        }

        return false
    }

    private fun hasLocationMockingApp(): Boolean {
        val knownMockApps = listOf(
            "com.lexa.fakegps",
            "com.incorporateapps.fakegps",
            "com.flygps",
            "org.hola.gpslocation",
            "com.my.fake.location",
            "com.fake.gps.location.spoofer",
            "com.fragileheart.gpsmock",
            "com.theappninjas.gpsjoystick"
        )

        val packageManager = context.packageManager
        for (packageName in knownMockApps) {
            try {
                packageManager.getPackageInfo(packageName, 0)
                Log.d(TAG, "위치 조작 앱 발견: $packageName")
                return true
            } catch (e: PackageManager.NameNotFoundException) {
                // 앱이 설치되어 있지 않음
            }
        }
        return false
    }

    private suspend fun saveLocation(location: Location): LocationEntity? {
        val locationEntity = LocationEntity(
            coordinates = location,
            createdAt = LocalDateTime.now()
        )

        try {
            val id = locationDao.insert(locationEntity)

            // ID를 포함한 완성된 엔티티를 생성
            val savedEntity = locationEntity.copy(id = id)

            // 캐시 및 상태 업데이트
            updateLocationCache(location)
            TravelPref.setLastSavedLocation(location)
            TravelPref.lastSavedTime = System.currentTimeMillis()

            Log.d(TAG, "위치 저장됨: ${location.latitude}, ${location.longitude}")

            // 저장된 위치 Flow에 전송
            _savedLocationsFlow.emit(location)

            // 저장된 엔티티 반환
            return savedEntity

        } catch (e: Exception) {
            Log.e(TAG, "위치 저장 실패", e)
            return null
        }
    }

    private fun isGpsJump(newLocation: Location): Boolean {
        synchronized(recentLocations) {
            // 최근 위치 기록 업데이트
            recentLocations.add(Pair(newLocation, System.currentTimeMillis()))
            while (recentLocations.size > MAX_RECENT_LOCATIONS) {
                recentLocations.removeFirst()
            }

            // 위치가 3개 미만이면 판단할 데이터가 충분하지 않음
            if (recentLocations.size < 3) {
                return false
            }

            // 마지막 3개 위치 가져오기
            val loc1 = recentLocations[recentLocations.size - 3].first  // 가장 오래된 위치
            val time1 = recentLocations[recentLocations.size - 3].second
            val loc2 = recentLocations[recentLocations.size - 2].first  // 중간 위치
            val time2 = recentLocations[recentLocations.size - 2].second
            val loc3 = newLocation  // 현재 위치
            val time3 = System.currentTimeMillis()

            // 시간 간격 계산 (초 단위)
            val timeDiff1 = (time2 - time1) / 1000.0f
            val timeDiff2 = (time3 - time2) / 1000.0f

            if (timeDiff1 <= 0 || timeDiff2 <= 0) return false  // 시간 간격이 없으면 판단 불가

            // 거리 계산
            val distance1 = loc1.distanceTo(loc2)  // 첫 번째-두 번째 위치 간 거리
            val distance2 = loc2.distanceTo(loc3)  // 두 번째-세 번째(현재) 위치 간 거리

            // 속도 계산 (m/s)
            val speed1 = distance1 / timeDiff1
            val speed2 = distance2 / timeDiff2

            // 가속도 계산 (m/s²)
            val acceleration = Math.abs(speed2 - speed1) / ((timeDiff1 + timeDiff2) / 2)

            // 가속도가 비정상적으로 높고, 방향이 반대이면 GPS 튐으로 간주
            if (acceleration > MAX_REASONABLE_ACCELERATION) {
                // 방향 확인 (베어링 각도 비교)
                val bearing1 = loc1.bearingTo(loc2)
                val bearing2 = loc2.bearingTo(loc3)
                val bearingDiff = Math.abs((bearing1 - bearing2 + 360) % 360)

                // 방향이 140도 이상 차이나면 급격한 방향 전환으로 간주
                if (bearingDiff > 140 && bearingDiff < 220) {
                    Log.w(TAG, "GPS 튐 감지: 가속도=${acceleration}m/s², 방향차=${bearingDiff}°")
                    return true
                }
            }

            return false
        }
    }

    private fun updateLocationCache(location: Location) {
        synchronized(locationCache) {
            // 캐시에 새 위치 추가
            locationCache.add(location)

            // 캐시 사이즈 제한
            while (locationCache.size > MAX_CACHE_SIZE) {
                locationCache.removeAt(0)
            }
        }
    }

    private fun getBestCachedLocation(): Location? {
        synchronized(locationCache) {
            if (locationCache.isEmpty()) return null

            // 가장 최근 위치 반환
            return locationCache.last()
        }
    }

    private fun getReliableLocation(): Location? {
        val lastSaved = getLastSavedLocation()
        val bestCached = getBestCachedLocation()

        // 캐시된 위치가 있고, 최근 저장된 위치보다 더 최신이면 캐시 사용
        if (bestCached != null && lastSaved != null) {
            if (bestCached.time > lastSaved.time) {
                return bestCached
            }
        }

        return lastSaved ?: bestCached
    }
}