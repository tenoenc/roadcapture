package com.tenacy.roadcapture.manager

import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.tenacy.roadcapture.data.pref.TravelPref
import com.tenacy.roadcapture.service.LocationTrackingService
import com.tenacy.roadcapture.worker.LocationCheckWorker
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TravelingStateManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val _isTraveling = MutableStateFlow(false)
    val isTraveling = _isTraveling.asStateFlow()

    // init 블록도 수정
    init {
        // 앱 시작 시 이전 여행 상태 확인
        val isCurrentlyTraveling = TravelPref.isTraveling
        _isTraveling.value = isCurrentlyTraveling

        if (isCurrentlyTraveling) {
            Log.d(TAG, "이전 여행 상태 복원됨")
            // 앱 시작 시 워커 등록
            LocationCheckWorker.enqueuePeriodicWork(context)
            // 앱 시작 시에만 즉시 실행 (TripFragment가 아닐 수 있으므로)
            LocationCheckWorker.enqueueOneTimeWork(context)
        }
    }

    fun startTraveling() {
        _isTraveling.value = true
        TravelPref.startTravel()

        // 워커 등록
        LocationCheckWorker.enqueuePeriodicWork(context)
        // 즉시 실행하는 워커는 제거 - TripFragment에 있을 때는 서비스가 필요 없음
        // LocationCheckWorker.enqueueOneTimeWork(context) <- 이 라인 제거!

        Log.d(TAG, "여행 시작 - 워커 등록됨")
    }

    fun stopTraveling() {
        _isTraveling.value = false
        TravelPref.stopTravel()

        // 워커 취소
        LocationCheckWorker.cancelAll(context)
        // 서비스 중지
        stopLocationTrackingService()

        Log.d(TAG, "여행 종료 - 워커 취소됨")
    }

    fun startLocationTrackingService() {
        if (!_isTraveling.value) {
            Log.d(TAG, "여행 중이 아니므로 서비스를 시작하지 않음")
            return
        }

        if (LocationTrackingService.isServiceRunning()) {
            Log.d(TAG, "위치 추적 서비스가 이미 실행 중입니다")
            return
        }

        val intent = Intent(context, LocationTrackingService::class.java)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
            Log.d(TAG, "위치 추적 서비스 시작")
        } catch (e: Exception) {
            Log.e(TAG, "서비스 시작 실패", e)
        }
    }

    fun stopLocationTrackingService() {
        if (!LocationTrackingService.isServiceRunning()) {
            Log.d(TAG, "위치 추적 서비스가 실행 중이지 않습니다")
            return
        }

        val intent = Intent(context, LocationTrackingService::class.java)
        context.stopService(intent)
        Log.d(TAG, "위치 추적 서비스 중지")
    }

    companion object {
        private const val TAG = "TravelingStateManager"
    }
}