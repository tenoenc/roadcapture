package com.tenacy.roadcapture.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
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
import com.tenacy.roadcapture.util.Constants
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import javax.inject.Inject
import kotlin.math.*

@AndroidEntryPoint
class LocationTrackingService : Service(), SensorEventListener {

    @Inject
    lateinit var locationProcessor: LocationProcessor

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var fusedLocationClient: FusedLocationProviderClient? = null
    private var locationCallback: LocationCallback? = null
    private var isInitialized = false

    // 센서 관련 변수들
    private var sensorManager: SensorManager? = null
    private var accelerometer: Sensor? = null
    private var magnetometer: Sensor? = null
    private var gyroscope: Sensor? = null

    // 센서 데이터 저장
    private var accelerometerData = FloatArray(3)
    private var magnetometerData = FloatArray(3)
    private var gyroscopeData = FloatArray(3)
    private var rotationMatrix = FloatArray(9)
    private var orientationAngles = FloatArray(3)

    // 이동 감지 관련
    private var lastAcceleration = 0f
    private var isMoving = false
    private var lastMovementTime = 0L
    private var stationaryStartTime = 0L

    // 동적 업데이트 간격 관리
    private var currentUpdateInterval = Constants.TRACKING_INTERVAL
    private var isAdaptiveMode = true
    private var lastLocationTime = 0L
    private var consecutiveLocationUpdates = 0

    // 배터리 최적화
    private var lowAccuracyModeStartTime = 0L
    private var isLowAccuracyMode = false

    companion object {
        private const val TAG = "LocationTrackingService"
        private val MOVEMENT_THRESHOLD = Constants.MOVEMENT_THRESHOLD  // Constants 사용
        private val STATIONARY_TIMEOUT = Constants.STATIONARY_TIMEOUT  // Constants 사용
        private val LOW_ACCURACY_TIMEOUT = Constants.LOW_ACCURACY_TIMEOUT  // Constants 사용

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
            return
        }

        instance = this

        if (BuildConfig.DEBUG) {
            Log.d(TAG, "향상된 위치 추적 서비스 시작")
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
        initializeServices()
    }

    private fun initializeServices() {
        try {
            // 위치 서비스 초기화
            fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
            setupLocationCallback()

            // 센서 서비스 초기화
            initializeSensors()

            isInitialized = true
            Log.d(TAG, "모든 서비스 초기화 완료")
        } catch (e: Exception) {
            Log.e(TAG, "서비스 초기화 실패", e)
            instance = null
            stopSelf()
        }
    }

    private fun initializeSensors() {
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager

        accelerometer = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        magnetometer = sensorManager?.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
        gyroscope = sensorManager?.getDefaultSensor(Sensor.TYPE_GYROSCOPE)

        // 센서 등록
        accelerometer?.let {
            sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
            Log.d(TAG, "가속도계 센서 등록됨")
        }
        magnetometer?.let {
            sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
            Log.d(TAG, "자기계 센서 등록됨")
        }
        gyroscope?.let {
            sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
            Log.d(TAG, "자이로스코프 센서 등록됨")
        }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        event?.let { sensorEvent ->
            when (sensorEvent.sensor.type) {
                Sensor.TYPE_ACCELEROMETER -> {
                    accelerometerData = sensorEvent.values.clone()
                    detectMovement()
                }
                Sensor.TYPE_MAGNETIC_FIELD -> {
                    magnetometerData = sensorEvent.values.clone()
                }
                Sensor.TYPE_GYROSCOPE -> {
                    gyroscopeData = sensorEvent.values.clone()
                }
            }

            // 방향 계산 (가속도계 + 자기계)
            if (accelerometerData.isNotEmpty() && magnetometerData.isNotEmpty()) {
                calculateOrientation()
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // 센서 정확도 변화 처리
    }

    private fun detectMovement() {
        // 중력 제거하여 실제 가속도 계산
        val totalAcceleration = sqrt(
            accelerometerData[0] * accelerometerData[0] +
                    accelerometerData[1] * accelerometerData[1] +
                    accelerometerData[2] * accelerometerData[2]
        ) - SensorManager.GRAVITY_EARTH

        lastAcceleration = abs(totalAcceleration)
        val currentTime = System.currentTimeMillis()

        if (lastAcceleration > MOVEMENT_THRESHOLD) {
            if (!isMoving) {
                Log.d(TAG, "이동 감지됨: ${lastAcceleration}m/s²")
                isMoving = true
                onMovementDetected()
            }
            lastMovementTime = currentTime
            stationaryStartTime = 0L
        } else {
            if (isMoving && stationaryStartTime == 0L) {
                stationaryStartTime = currentTime
            }

            // 일정 시간 동안 움직임이 없으면 정지 상태로 전환
            if (stationaryStartTime > 0L &&
                currentTime - stationaryStartTime > STATIONARY_TIMEOUT) {
                if (isMoving) {
                    Log.d(TAG, "정지 상태 감지됨")
                    isMoving = false
                    onStationaryDetected()
                }
            }
        }
    }

    private fun calculateOrientation() {
        if (SensorManager.getRotationMatrix(rotationMatrix, null, accelerometerData, magnetometerData)) {
            SensorManager.getOrientation(rotationMatrix, orientationAngles)
            // orientationAngles[0] = 방위각 (azimuth)
            // orientationAngles[1] = 피치 (pitch)
            // orientationAngles[2] = 롤 (roll)
        }
    }

    private fun onMovementDetected() {
        // 이동 감지 시 업데이트 간격을 짧게 조정
        if (isAdaptiveMode) {
            adjustUpdateInterval(isMoving = true)
            restartLocationUpdates()
        }
    }

    private fun onStationaryDetected() {
        // 정지 감지 시 업데이트 간격을 길게 조정
        if (isAdaptiveMode) {
            adjustUpdateInterval(isMoving = false)
            restartLocationUpdates()
        }
    }

    private fun adjustUpdateInterval(isMoving: Boolean) {
        val newInterval = if (isMoving) {
            when {
                lastAcceleration > 5.0f -> 2_000L  // 빠른 이동: 2초
                lastAcceleration > 2.0f -> 5_000L  // 보통 이동: 5초
                else -> 10_000L  // 느린 이동: 10초
            }
        } else {
            30_000L  // 정지 상태: 30초
        }

        if (newInterval != currentUpdateInterval) {
            currentUpdateInterval = newInterval
            Log.d(TAG, "업데이트 간격 조정: ${currentUpdateInterval/1000}초")
        }
    }

    private fun restartLocationUpdates() {
        serviceScope.launch {
            stopLocationUpdates()
            delay(1000)  // 잠시 대기
            if (isInitialized) {
                startLocationUpdates()
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand called with action: ${intent?.action}")

        if (intent?.action == Constants.ACTION_STOP_TRACKING_SERVICE) {
            Log.d(TAG, "서비스 중지 요청 받음")
            stopSelf()
            return START_NOT_STICKY
        }

        if (!isInitialized) {
            if (instance == null) {
                Log.e(TAG, "서비스가 onCreate를 거치지 않고 시작됨")
                stopSelf()
                return START_NOT_STICKY
            }
            Log.w(TAG, "서비스 초기화 중...")
            return START_STICKY
        }

        startLocationUpdates()
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        Log.d(TAG, "향상된 위치 추적 서비스 종료")

        if (isInitialized) {
            stopLocationUpdates()
        }

        // 센서 해제
        sensorManager?.unregisterListener(this)

        // 상태 저장
        locationProcessor.saveState()

        serviceScope.cancel()
        isInitialized = false
        fusedLocationClient = null
        locationCallback = null
        sensorManager = null
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
                getString(R.string.location_tracking),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.background_location_tracking)
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
            .setContentTitle(getString(R.string.location_tracking_active))
            .setContentText(getString(R.string.travel_route_recording))
            .setSmallIcon(R.drawable.ic_pin)
            .setColor(ContextCompat.getColor(this, R.color.primary_normal))
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .addAction(
                R.drawable.ic_stop,
                getString(R.string.stop_tracking),
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
                val currentTime = System.currentTimeMillis()
                var bestLocation = locationResult.lastLocation

                // 위치 결과에서 가장 정확한 위치 선택
                for (location in locationResult.locations) {
                    if (bestLocation == null ||
                        (location.accuracy < bestLocation.accuracy && location.accuracy > 0)) {
                        bestLocation = location
                    }
                }

                bestLocation?.let { location ->
                    consecutiveLocationUpdates++
                    lastLocationTime = currentTime

                    // 배터리 최적화: 연속으로 낮은 품질 위치만 받으면 저전력 모드 전환
                    if (location.accuracy > 50f) {
                        if (lowAccuracyModeStartTime == 0L) {
                            lowAccuracyModeStartTime = currentTime
                        } else if (currentTime - lowAccuracyModeStartTime > LOW_ACCURACY_TIMEOUT) {
                            if (!isLowAccuracyMode) {
                                Log.d(TAG, "저전력 모드 활성화 - GPS 신호 불량")
                                isLowAccuracyMode = true
                                adjustForLowAccuracyMode()
                            }
                        }
                    } else {
                        // 좋은 신호 복구 시 저전력 모드 해제
                        if (isLowAccuracyMode) {
                            Log.d(TAG, "GPS 신호 복구 - 저전력 모드 해제")
                            isLowAccuracyMode = false
                            restartLocationUpdates()
                        }
                        lowAccuracyModeStartTime = 0L
                    }

                    // 위치 품질 검사 (향상된 기준)
                    if (locationProcessor.isLocationQualityAcceptable(location)) {
                        // 위치 처리 코루틴 실행
                        serviceScope.launch {
                            try {
                                locationProcessor.processLocation(location)

                                // 성공적인 처리 후 알림 업데이트
                                updateNotification()

                            } catch (e: Exception) {
                                Log.e(TAG, "위치 처리 중 오류 발생", e)
                            }
                        }
                    } else {
                        Log.d(TAG, "낮은 품질의 위치 무시됨: accuracy=${location.accuracy}m, age=${(currentTime - location.time)/1000}s")
                    }
                }
            }

            override fun onLocationAvailability(locationAvailability: LocationAvailability) {
                if (!locationAvailability.isLocationAvailable) {
                    Log.w(TAG, "위치 사용 불가 - 복구 시도")
                    handleLocationUnavailable()
                } else {
                    Log.d(TAG, "위치 서비스 복구됨")
                }
            }
        }
    }

    private fun adjustForLowAccuracyMode() {
        // 저전력 모드에서는 업데이트 간격을 크게 늘림
        currentUpdateInterval = 60_000L  // 1분
        restartLocationUpdates()
    }

    private fun updateNotification() {
        try {
            val notification = createNotification()
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.notify(Constants.TRACKING_NOTIFICATION_ID, notification)
        } catch (e: Exception) {
            Log.w(TAG, "알림 업데이트 실패", e)
        }
    }

    private fun handleLocationUnavailable() {
        serviceScope.launch {
            stopLocationUpdates()
            delay(5000)  // 5초 대기

            if (isInitialized) {
                Log.d(TAG, "위치 서비스 재시작 시도")
                startLocationUpdates()
            }
        }
    }

    private fun startLocationUpdates() {
        if (!isInitialized || fusedLocationClient == null || locationCallback == null) {
            Log.e(TAG, "위치 서비스가 초기화되지 않았습니다")
            return
        }

        val isDebugMode = BuildConfig.DEBUG

        // 동적 우선순위 결정
        val priority = when {
            isLowAccuracyMode -> Priority.PRIORITY_LOW_POWER
            isMoving -> Priority.PRIORITY_HIGH_ACCURACY
            isDebugMode -> Priority.PRIORITY_HIGH_ACCURACY
            else -> Priority.PRIORITY_BALANCED_POWER_ACCURACY
        }

        val locationRequest = LocationRequest.Builder(priority, currentUpdateInterval)
            .apply {
                // 최소 업데이트 간격 (이동 상태에 따라 조정)
                val minInterval = if (isMoving) currentUpdateInterval / 2 else currentUpdateInterval
                setMinUpdateIntervalMillis(minInterval)

                // 정확도 기다리기 (이동 중일 때는 더 엄격하게)
                setWaitForAccurateLocation(!isLowAccuracyMode)

                // 최대 지연 시간 (저전력 모드에서는 더 관대하게)
                val maxDelay = if (isLowAccuracyMode) 120_000L else 30_000L
                setMaxUpdateDelayMillis(maxDelay)

                // 최소 이동 거리 (이동 상태에 따라 조정)
                val minDistance = when {
                    !isMoving -> 20f  // 정지 상태에서는 더 큰 거리 변화만 감지
                    isLowAccuracyMode -> 15f
                    else -> Constants.MIN_DISTANCE_TO_SAVE
                }
                setMinUpdateDistanceMeters(minDistance)

                // 고급 설정 (API 31+)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    setGranularity(
                        if (isLowAccuracyMode) Granularity.GRANULARITY_COARSE
                        else Granularity.GRANULARITY_PERMISSION_LEVEL
                    )
                }

                setDurationMillis(Long.MAX_VALUE)
            }.build()

        try {
            val locationSettingsRequest = LocationSettingsRequest.Builder()
                .addLocationRequest(locationRequest)
                .setAlwaysShow(true)
                .build()

            LocationServices.getSettingsClient(this)
                .checkLocationSettings(locationSettingsRequest)
                .addOnSuccessListener {
                    fusedLocationClient?.requestLocationUpdates(
                        locationRequest,
                        locationCallback!!,
                        Looper.getMainLooper()
                    )

                    val modeText = when {
                        isLowAccuracyMode -> "저전력"
                        isMoving -> "고정밀"
                        isDebugMode -> "디버그"
                        else -> "균형"
                    }

                    Log.d(TAG, "위치 업데이트 시작 - 모드: $modeText, 간격: ${currentUpdateInterval/1000}초, 우선순위: $priority")
                }
                .addOnFailureListener { e ->
                    Log.w(TAG, "위치 설정 확인 실패, 기본 요청으로 진행: ${e.message}")
                    fusedLocationClient?.requestLocationUpdates(
                        locationRequest,
                        locationCallback!!,
                        Looper.getMainLooper()
                    )
                }
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
}