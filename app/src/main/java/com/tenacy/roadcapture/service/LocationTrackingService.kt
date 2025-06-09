// LocationTrackingService.kt (수정된 버전)
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

@AndroidEntryPoint
class LocationTrackingService : Service() {

    @Inject
    lateinit var locationProcessor: LocationProcessor

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var fusedLocationClient: FusedLocationProviderClient? = null
    private var locationCallback: LocationCallback? = null

    // 센서 관련 필드
    private var sensorManager: SensorManager? = null
    private var accelerometer: Sensor? = null

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
            return
        }

        instance = this

        if (BuildConfig.DEBUG) {
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

        // 센서 등록
        registerSensors()

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
        Log.d(TAG, "위치 추적 서비스 종료")

        if (isInitialized) {
            stopLocationUpdates()
        }

        // 센서 해제
        unregisterSensors()

        // 상태 저장
        locationProcessor.saveState()

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

    // 센서 등록 메서드
    private fun registerSensors() {
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        accelerometer?.let {
            sensorManager?.registerListener(
                sensorListener,
                it,
                SensorManager.SENSOR_DELAY_NORMAL
            )
        }
    }

    // 센서 해제 메서드
    private fun unregisterSensors() {
        sensorManager?.unregisterListener(sensorListener)
    }

    // 센서 리스너 구현
    private val sensorListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
                val x = event.values[0]
                val y = event.values[1]
                val z = event.values[2]

                // 가속도 벡터의 크기 계산
                val acceleration = Math.sqrt((x * x + y * y + z * z).toDouble())

                // 임계값 이상이면 움직임으로 판단
                val isMoving = acceleration > 10.5 // 약간의 움직임 감지 (정지 시 ~9.8)

                // LocationProcessor에 움직임 상태 전달
                if (locationProcessor is DefaultLocationProcessor) {
                    (locationProcessor as DefaultLocationProcessor).setMovingState(isMoving)
                }
            }
        }

        override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {
            // 정확도 변경 무시
        }
    }

    private fun setupLocationCallback() {
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                var bestLocation = locationResult.lastLocation

                // 위치 결과에서 가장 정확한 위치 선택
                for (location in locationResult.locations) {
                    if (bestLocation == null || location.accuracy < bestLocation.accuracy) {
                        bestLocation = location
                    }
                }

                // 최종 선택된 위치만 처리
                bestLocation?.let { location ->
                    // 위치 품질 검사
                    if (locationProcessor.isLocationQualityAcceptable(location)) {
                        // 위치 처리 코루틴 실행
                        serviceScope.launch {
                            locationProcessor.processLocation(location)
                        }
                    } else {
                        Log.d(TAG, "낮은 품질의 위치 무시됨: accuracy=${location.accuracy}")
                    }
                }
            }

            override fun onLocationAvailability(locationAvailability: LocationAvailability) {
                if (!locationAvailability.isLocationAvailable) {
                    Log.w(TAG, "위치 사용 불가 - 복구 시도")
                    handleLocationUnavailable()
                }
            }
        }
    }

    // 위치 서비스 불가 시 복구 메서드
    private fun handleLocationUnavailable() {
        serviceScope.launch {
            // 현재 설정된 요청 제거
            stopLocationUpdates()

            // 짧은 지연 후 재시도
            delay(3000)

            // 위치 서비스 재시작
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

        // 디버그 모드 체크
        val isDebugMode = BuildConfig.DEBUG

        val locationRequest = if (isDebugMode) {
            // 디버그 모드: 더 강력한 위치 요청으로 다른 앱과의 간섭 방지
            LocationRequest.Builder(
                Priority.PRIORITY_HIGH_ACCURACY,
                Constants.TRACKING_INTERVAL
            ).apply {
                setMinUpdateIntervalMillis(Constants.TRACKING_INTERVAL)
                setIntervalMillis(Constants.TRACKING_INTERVAL)
                setWaitForAccurateLocation(true)
                setMinUpdateDistanceMeters(Constants.MIN_DISTANCE_TO_SAVE)
                setMaxUpdates(Int.MAX_VALUE)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    setGranularity(Granularity.GRANULARITY_PERMISSION_LEVEL) // 최대 정밀도 요청
                }
                setPriority(Priority.PRIORITY_HIGH_ACCURACY) // 우선순위 명시적 강조
                // 다른 앱과의 간섭 최소화를 위한 정확한 설정
                setDurationMillis(Long.MAX_VALUE) // 영구적인 요청 설정
            }.build()
        } else {
            // 릴리즈 모드: 배터리 효율적인 설정
            LocationRequest.Builder(
                Priority.PRIORITY_BALANCED_POWER_ACCURACY,
                Constants.TRACKING_INTERVAL
            ).apply {
                setMinUpdateIntervalMillis(Constants.TRACKING_FASTEST_INTERVAL)
                setWaitForAccurateLocation(true) // 정확도 향상을 위해 true로 변경
                setMaxUpdateDelayMillis(30_000) // 60초에서 30초로 줄여 더 빠른 응답
                setMinUpdateDistanceMeters(Constants.MIN_DISTANCE_TO_SAVE)
                setPriority(Priority.PRIORITY_BALANCED_POWER_ACCURACY) // 명시적 우선순위
            }.build()
        }

        try {
            // 위치 제공자 설정
            val locationSettingsRequest = LocationSettingsRequest.Builder()
                .addLocationRequest(locationRequest)
                .setAlwaysShow(true) // 사용자에게 설정 변경 다이얼로그 항상 표시
                .build()

            // 위치 설정 확인 및 요청
            LocationServices.getSettingsClient(this)
                .checkLocationSettings(locationSettingsRequest)
                .addOnSuccessListener {
                    // 설정이 만족되면 위치 업데이트 시작
                    fusedLocationClient?.requestLocationUpdates(
                        locationRequest,
                        locationCallback!!,
                        Looper.getMainLooper()
                    )
                    Log.d(TAG, "위치 업데이트 시작됨 (모드: ${if (isDebugMode) "DEBUG" else "RELEASE"})")
                }
                .addOnFailureListener { e ->
                    // 설정이 만족되지 않으면 기존 방식으로 시도
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