package com.tenacy.roadcapture.service

import android.app.*
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.os.Build
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.*
import com.tenacy.roadcapture.BuildConfig
import com.tenacy.roadcapture.MainActivity
import com.tenacy.roadcapture.R
import com.tenacy.roadcapture.data.db.LocationDao
import com.tenacy.roadcapture.data.db.LocationEntity
import com.tenacy.roadcapture.data.pref.DebugSettings
import com.tenacy.roadcapture.data.pref.TravelPref
import com.tenacy.roadcapture.util.Constants
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import java.time.LocalDateTime
import javax.inject.Inject

@AndroidEntryPoint
class LocationTrackingService : Service() {

    @Inject
    lateinit var locationDao: LocationDao

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var fusedLocationClient: FusedLocationProviderClient? = null
    private var locationCallback: LocationCallback? = null

    private var isInitialized = false

    companion object {
        private const val TAG = "LocationTrackingService"

        @Volatile
        private var instance: LocationTrackingService? = null

        fun isServiceRunning(): Boolean {
            return instance != null
        }
    }

    override fun onCreate() {
        super.onCreate()

        // 이미 인스턴스가 존재하는지 체크
        if (instance != null) {
            Log.w(TAG, "서비스가 이미 실행 중입니다. 새 인스턴스를 종료합니다.")
            return  // stopSelf()를 호출하지 않고 그냥 return
        }

        instance = this

        // 디버그 모드와 Mock Location 설정 확인
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "위치 추적 서비스 시작 - Mock Location 사용: ${DebugSettings.useMockLocationInDebugMode}")
        } else {
            Log.d(TAG, "위치 추적 서비스 시작")
        }

        createNotificationChannel()

        // Android 12(API 31) 이상에서 ForegroundServiceStartNotAllowedException 방지
        try {
            startForeground(Constants.TRACKING_NOTIFICATION_ID, createNotification())
        } catch (e: Exception) {
            Log.e(TAG, "Foreground 서비스 시작 실패", e)
            instance = null
            stopSelf()
            return
        }

        // 초기화 진행
        initializeLocationServices()
    }

    private fun initializeLocationServices() {
        try {
            fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
            setupLocationCallback()
            isInitialized = true
            Log.d(TAG, "위치 서비스 초기화 완료")
        } catch (e: Exception) {
            Log.e(TAG, "위치 서비스 초기화 실패", e)
            instance = null
            stopSelf()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand called with action: ${intent?.action}")

        if (intent?.action == Constants.ACTION_STOP_TRACKING_SERVICE) {
            Log.d(TAG, "서비스 중지 요청 받음")
            stopSelf()
            return START_NOT_STICKY
        }

        // 초기화되지 않은 경우에만 체크
        if (!isInitialized) {
            // onCreate가 실행되지 않았거나 실패한 경우
            if (instance == null) {
                Log.e(TAG, "서비스가 onCreate를 거치지 않고 시작됨")
                stopSelf()
                return START_NOT_STICKY
            }

            // onCreate는 실행됐지만 초기화가 아직 안 된 경우 기다림
            Log.w(TAG, "서비스 초기화 중...")
            return START_STICKY
        }

        startLocationUpdates()
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        Log.d(TAG, "위치 추적 서비스 종료")

        // 안전하게 정리
        if (isInitialized) {
            stopLocationUpdates()
        }

        serviceScope.cancel()
        isInitialized = false
        fusedLocationClient = null
        locationCallback = null
        instance = null

        super.onDestroy()
    }

    private fun createStopServicePendingIntent(): PendingIntent {
        val stopIntent = Intent(this, LocationTrackingService::class.java).apply {
            action = Constants.ACTION_STOP_TRACKING_SERVICE
        }

        return PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                Constants.TRACKING_NOTIFICATION_CHANNEL_ID,
                "위치 추적",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "백그라운드에서 위치를 추적합니다"
                setShowBadge(false)
            }

            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                action = Intent.ACTION_MAIN
                addCategory(Intent.CATEGORY_LAUNCHER)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val builder = NotificationCompat.Builder(this, Constants.TRACKING_NOTIFICATION_CHANNEL_ID)
            .setContentTitle("위치 추적 중")
            .setContentText("여행 경로를 기록하고 있습니다")
            .setSmallIcon(R.drawable.ic_pin)
            .setColor(ContextCompat.getColor(this, R.color.primary_normal))
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .addAction(
                R.drawable.ic_stop,
                "추적 중지",
                createStopServicePendingIntent()
            )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder.setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
        }

        return builder.build()
    }

    private fun setupLocationCallback() {
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                locationResult.lastLocation?.let { location ->
                    handleLocationUpdate(location)
                }
            }
        }
    }

    private fun startLocationUpdates() {
        if (!isInitialized || fusedLocationClient == null || locationCallback == null) {
            Log.e(TAG, "위치 서비스가 초기화되지 않았습니다")
            return
        }

        // 디버그 모드 체크
        val isDebugMode = BuildConfig.DEBUG

        val locationRequest = if (isDebugMode) {
            // 디버그 모드: 독립적인 위치 요청
            LocationRequest.Builder(
                Priority.PRIORITY_HIGH_ACCURACY,  // 독립적인 고정밀 요청
                Constants.TRACKING_INTERVAL
            ).apply {
                setMinUpdateIntervalMillis(Constants.TRACKING_INTERVAL)  // 최소 간격 강제
                setIntervalMillis(Constants.TRACKING_INTERVAL)  // 정확한 간격 설정
                setWaitForAccurateLocation(true)  // 정확한 위치 대기
                setMinUpdateDistanceMeters(Constants.MIN_DISTANCE_TO_SAVE)
                setMaxUpdates(Int.MAX_VALUE)  // 무제한 업데이트
                // 배치 처리 비활성화 (setMaxUpdateDelayMillis 제거)
            }.build()
        } else {
            // 릴리즈 모드: 배터리 효율적인 설정
            LocationRequest.Builder(
                Priority.PRIORITY_BALANCED_POWER_ACCURACY,
                Constants.TRACKING_INTERVAL
            ).apply {
                setMinUpdateIntervalMillis(Constants.TRACKING_FASTEST_INTERVAL)
                setWaitForAccurateLocation(false)
                setMaxUpdateDelayMillis(60_000)  // 배치 처리 활성화
                setMinUpdateDistanceMeters(Constants.MIN_DISTANCE_TO_SAVE)
            }.build()
        }

        try {
            fusedLocationClient?.requestLocationUpdates(
                locationRequest,
                locationCallback!!,
                Looper.getMainLooper()
            )
            Log.d(TAG, "위치 업데이트 시작됨 (모드: ${if (isDebugMode) "DEBUG" else "RELEASE"})")
        } catch (e: SecurityException) {
            Log.e(TAG, "위치 권한이 없습니다", e)
            stopSelf()
        } catch (e: Exception) {
            Log.e(TAG, "위치 업데이트 시작 실패", e)
        }
    }

    private fun stopLocationUpdates() {
        try {
            locationCallback?.let { callback ->
                fusedLocationClient?.removeLocationUpdates(callback)
                Log.d(TAG, "위치 업데이트 중지됨")
            }
        } catch (e: Exception) {
            Log.e(TAG, "위치 업데이트 중지 실패", e)
        }
    }

    private fun handleLocationUpdate(location: Location) {
        serviceScope.launch {
            if (BuildConfig.DEBUG && DebugSettings.useMockLocationInDebugMode) {
                // 디버그 모드 + Mock Location 사용 설정이 켜져있는 경우
                // Mock 위치만 허용하고 실제 위치는 무시
                val isMocked = isLocationMocked(location)
                val isSuspicious = isLocationSuspicious(location)

                if (isMocked) {
                    Log.d(TAG, "Mock 위치 감지됨: ${location.latitude}, ${location.longitude}")
                }

                // Mock 위치이거나 의심스러운 위치도 허용
                if (isMocked || isSuspicious || hasLocationMockingApp()) {
                    if (shouldSaveLocation(location)) {
                        saveLocation(location)
                    }
                } else {
                    Log.v(TAG, "실제 GPS 위치 무시됨 - Mock Location 모드에서는 조작된 위치만 사용")
                }
            } else if (BuildConfig.DEBUG && !DebugSettings.useMockLocationInDebugMode) {
                // 디버그 모드 + Mock Location 사용 설정이 꺼져있는 경우
                // Mock 위치와 실제 위치 모두 구분없이 사용
                if (shouldSaveLocation(location)) {
                    saveLocation(location)
                    Log.d(TAG, "위치 저장 (Mock 여부 무관): ${location.latitude}, ${location.longitude}")
                }
            } else {
                // 릴리즈 모드: Mock 위치 차단
                if (!isLocationMocked(location)) {
                    if (shouldSaveLocation(location)) {
                        saveLocation(location)
                    }
                } else {
                    Log.w(TAG, "Mock 위치 무시됨 (릴리즈 모드): ${location.latitude}, ${location.longitude}")
                }
            }
        }
    }

    private fun isLocationMocked(location: Location): Boolean {
        // 1. Location 객체의 isMock 메서드 사용 (API 31+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return location.isMock
        }

        // 2. 이전 버전에서는 isFromMockProvider 사용
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
            return location.isFromMockProvider
        }

        // 3. 더 낮은 버전에서는 Provider 이름으로 판단
        return location.provider == "mock" || location.provider == "gps"
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

        val packageManager = packageManager
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

    private suspend fun saveLocation(location: Location) {
        val locationEntity = LocationEntity(
            latitude = location.latitude,
            longitude = location.longitude,
            createdAt = LocalDateTime.now()
        )

        try {
            locationDao.insert(locationEntity)
            TravelPref.setLastSavedLocation(location)
            TravelPref.lastSavedTime = System.currentTimeMillis()
            Log.d(TAG, "위치 저장됨: ${location.latitude}, ${location.longitude}")
        } catch (e: Exception) {
            Log.e(TAG, "위치 저장 실패", e)
        }
    }

    private fun shouldSaveLocation(currentLocation: Location): Boolean {
        val currentTime = System.currentTimeMillis()
        val isDebugMode = BuildConfig.DEBUG

        // 이전 저장 시간이 0인 경우 (첫 번째 위치)
        if (TravelPref.lastSavedTime == 0L) {
            TravelPref.lastSavedTime = currentTime
            Log.d(TAG, "첫 위치 저장")
            return true
        }

        // 시간 간격 계산
        val timeDiff = currentTime - TravelPref.lastSavedTime

        // 디버그 모드에서는 시간 체크도 추가
        if (isDebugMode) {
            if (timeDiff < Constants.TRACKING_INTERVAL) {
                Log.v(TAG, "위치 저장 건너뜀 - 시간 간격 부족: ${timeDiff}ms (최소: ${Constants.TRACKING_INTERVAL}ms)")
                return false
            }
        }

        val lastLocation = TravelPref.getLastSavedLocation() ?: run {
            TravelPref.lastSavedTime = currentTime
            return true
        }

        val distance = lastLocation.distanceTo(currentLocation)

        // 디버그 모드에서는 시간과 거리 조건 모두 만족해야 함
        if (isDebugMode) {
            val shouldSave = distance >= Constants.MIN_DISTANCE_TO_SAVE && timeDiff >= Constants.TRACKING_INTERVAL

            if (shouldSave) {
                Log.d(TAG, "위치 저장 - 거리: ${distance}m, 시간 간격: ${timeDiff}ms")
            } else {
                if (distance < Constants.MIN_DISTANCE_TO_SAVE) {
                    Log.v(TAG, "위치 저장 건너뜀 - 거리: ${distance}m (최소: ${Constants.MIN_DISTANCE_TO_SAVE}m)")
                }
            }

            return shouldSave
        } else {
            // 릴리즈 모드에서는 거리만 체크
            val shouldSave = distance >= Constants.MIN_DISTANCE_TO_SAVE

            if (shouldSave) {
                Log.d(TAG, "위치 저장 - 거리: ${distance}m")
            } else {
                Log.v(TAG, "위치 저장 건너뜀 - 거리: ${distance}m (최소: ${Constants.MIN_DISTANCE_TO_SAVE}m)")
            }

            return shouldSave
        }
    }
}