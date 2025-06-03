package com.tenacy.roadcapture.worker

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.tenacy.roadcapture.data.db.CacheDao
import com.tenacy.roadcapture.data.db.LocationCacheDao
import com.tenacy.roadcapture.data.db.MemoryCacheDao
import com.tenacy.roadcapture.util.Constants
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.concurrent.TimeUnit

@HiltWorker
class CleanupOldCachesWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted params: WorkerParameters,
    private val cacheDao: CacheDao,
    private val memoryCacheDao: MemoryCacheDao,
    private val locationCacheDao: LocationCacheDao,
) : CoroutineWorker(context, params) {

    companion object {
        const val TAG = "CleanupOldCachesWorker"

        fun enqueuePeriodicWork(context: Context): Operation {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
                .setRequiresBatteryNotLow(true)
                .build()

            val workRequest = PeriodicWorkRequestBuilder<CleanupOldCachesWorker>(
                repeatInterval = Constants.CACHE_REPEAT_INTERVAL_DAYS,
                repeatIntervalTimeUnit = TimeUnit.DAYS
            )
                .setConstraints(constraints)
                .addTag(TAG)
                .build()

            return WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(
                    Constants.CACHE_CLEANUP_WORK_NAME,
                    ExistingPeriodicWorkPolicy.KEEP, // 이미 예약된 작업이 있으면 유지
                    workRequest
                )
        }

        fun enqueueOneTimeWork(context: Context): Operation {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
                .build()

            val workRequest = OneTimeWorkRequestBuilder<CleanupOldCachesWorker>()
                .setConstraints(constraints)
                .addTag(TAG)
                .build()

            return WorkManager.getInstance(context)
                .enqueueUniqueWork(
                    "${Constants.CACHE_CLEANUP_WORK_NAME}_onetime",
                    ExistingWorkPolicy.KEEP,
                    workRequest
                )
        }

        fun cancelAll(context: Context) {
            Log.d(TAG, "캐시 정리 워커 취소")
            WorkManager.getInstance(context).cancelAllWorkByTag(TAG)
        }
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "오래된 캐시 정리 시작")

            // 현재 시간에서 30일 전의 UTC 타임스탬프 계산
            val currentTime = Instant.now()
            val expirationTime = currentTime.minus(Constants.CACHE_EXPIRATION_DAYS, ChronoUnit.DAYS)
            val expirationTimestamp = expirationTime.toEpochMilli()

            // 삭제하기 전에 만료된 캐시의 targetId 목록 가져오기
            val expiredTargetIds = cacheDao.selectTargetIdByCreatedAtLessThan(expirationTimestamp)

            memoryCacheDao.deleteInAlbumIds(expiredTargetIds)
            locationCacheDao.deleteInAlbumIds(expiredTargetIds)

            // 캐시 테이블에서 30일이 지난 레코드 삭제
            val deletedCount = cacheDao.deleteByCreatedAtLessThan(expirationTimestamp)

            Log.d(TAG, "캐시 정리 완료: $deletedCount 개 삭제됨, 삭제된 targetIds: $expiredTargetIds")

            // 워크 데이터에 삭제된 targetIds JSON 문자열로 포함
            val targetIdsJson = expiredTargetIds.joinToString(",")

            return@withContext Result.success(
                workDataOf(
                    "deleted_count" to deletedCount,
                    "deleted_target_ids" to targetIdsJson
                )
            )
        } catch (e: Exception) {
            Log.e(TAG, "캐시 정리 중 오류 발생", e)
            if (runAttemptCount < 3) {
                Result.retry()
            } else {
                Result.failure()
            }
        }
    }
}