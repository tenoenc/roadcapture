package com.tenacy.roadcapture.worker

import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.tenacy.roadcapture.data.pref.TravelStatePref
import com.tenacy.roadcapture.service.LocationTrackingService
import com.tenacy.roadcapture.util.Constants
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

@HiltWorker
class LocationCheckWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters
) : CoroutineWorker(context, params) {

    // LocationCheckWorker.kt의 doWork() 메서드 수정
    override suspend fun doWork(): Result {
        Log.d(TAG, "위치 추적 상태 체크 시작")

        return try {
            // 여행 상태 확인 - TravelStatePref만 사용
            val isTraveling = TravelStatePref.isTraveling

            if (isTraveling) {
                Log.d(TAG, "여행 중 상태 확인됨")

                if (!LocationTrackingService.isServiceRunning()) {
                    Log.d(TAG, "서비스가 실행 중이지 않음 - 서비스 시작")
                    startLocationTrackingService()
                } else {
                    Log.d(TAG, "서비스가 이미 실행 중")
                }
            } else {
                Log.d(TAG, "여행 중이 아님")

                if (LocationTrackingService.isServiceRunning()) {
                    Log.d(TAG, "서비스가 실행 중 - 서비스 중지")
                    stopLocationTrackingService()
                }
            }

            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "워커 실행 중 오류", e)
            Result.retry()
        }
    }

    private fun startLocationTrackingService() {
        val serviceIntent = Intent(applicationContext, LocationTrackingService::class.java)

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                applicationContext.startForegroundService(serviceIntent)
            } else {
                applicationContext.startService(serviceIntent)
            }
            Log.d(TAG, "위치 추적 서비스 시작 성공")
        } catch (e: Exception) {
            Log.e(TAG, "서비스 시작 실패", e)
            throw e
        }
    }

    private fun stopLocationTrackingService() {
        val serviceIntent = Intent(applicationContext, LocationTrackingService::class.java)
        applicationContext.stopService(serviceIntent)
        Log.d(TAG, "위치 추적 서비스 중지")
    }

    @AssistedFactory
    interface Factory {
        fun create(context: Context, params: WorkerParameters): LocationCheckWorker
    }

    companion object {
        private const val TAG = "LocationCheckWorker"

        fun enqueuePeriodicWork(context: Context) {
            Log.d(TAG, "주기적 워커 등록")

            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
                .setRequiresBatteryNotLow(false)  // 배터리 낮아도 실행
                .setRequiresCharging(false)       // 충전 중이 아니어도 실행
                .build()

            val periodicWorkRequest = PeriodicWorkRequestBuilder<LocationCheckWorker>(
                Constants.TRACKING_REPEAT_INTERVAL_MINUTES,
                TimeUnit.MINUTES
            )
                .addTag(TAG)
                .setConstraints(constraints)
                .setInitialDelay(Constants.TRACKING_INITIAL_DELAY_MINUTES, TimeUnit.MINUTES)
                .setBackoffCriteria(
                    BackoffPolicy.LINEAR,
                    WorkRequest.MIN_BACKOFF_MILLIS,  // 수정됨
                    TimeUnit.MILLISECONDS
                )
                .build()

            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(
                    Constants.TRACKING_WORK_NAME,
                    ExistingPeriodicWorkPolicy.UPDATE,
                    periodicWorkRequest
                )

            Log.d(TAG, "주기적 워커 등록 완료")
        }

        // 즉시 실행을 위한 일회성 워커
        fun enqueueOneTimeWork(context: Context) {
            Log.d(TAG, "일회성 워커 등록")

            val oneTimeWorkRequest = OneTimeWorkRequestBuilder<LocationCheckWorker>()
                .addTag(TAG)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
                        .build()
                )
                .build()

            WorkManager.getInstance(context)
                .enqueue(oneTimeWorkRequest)
        }

        fun cancelAll(context: Context) {
            Log.d(TAG, "위치 추적 워커 취소")
            WorkManager.getInstance(context).cancelAllWorkByTag(TAG)
        }
    }
}