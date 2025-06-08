package com.tenacy.roadcapture.worker

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.tenacy.roadcapture.util.Constants
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import java.util.concurrent.TimeUnit

@HiltWorker
class PhotoCacheWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    companion object {
        private const val TAG = "PhotoCacheWorker"
        const val KEY_PHOTO_URLS = "photo_urls"

        fun enqueueOneTimeWork(context: Context, photoUrls: List<String>) {
            val data = Data.Builder()
                .putStringArray(KEY_PHOTO_URLS, photoUrls.toTypedArray())
                .build()

            val request = OneTimeWorkRequestBuilder<PhotoCacheWorker>()
                .addTag(TAG)
                .setInputData(data)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    WorkRequest.MIN_BACKOFF_MILLIS,
                    TimeUnit.MILLISECONDS
                )
                .build()

            WorkManager.getInstance(context)
                .enqueueUniqueWork(
                    Constants.CACHE_PHOTO_WORK_NAME,
                    ExistingWorkPolicy.APPEND_OR_REPLACE,
                    request
                )
        }

        fun cancelAll(context: Context) {
            Log.d(TAG, "이미지 캐싱 워커 취소")
            WorkManager.getInstance(context).cancelAllWorkByTag(TAG)
        }
    }

    override suspend fun doWork(): Result = coroutineScope {
        return@coroutineScope try {
            val photoUrls = inputData.getStringArray(KEY_PHOTO_URLS)?.toList() ?: emptyList()

            if (photoUrls.isEmpty()) {
                Log.d(TAG, "캐싱할 이미지가 없음")
                return@coroutineScope Result.success()
            }

            Log.d(TAG, "이미지 캐싱 시작: ${photoUrls.size}개")

            // 병렬로 이미지 캐싱
            val cacheJobs = photoUrls.map { url ->
                async {
                    try {
                        // Glide를 사용해 이미지를 캐시로 다운로드
                        Glide.with(context)
                            .load(url)
                            .diskCacheStrategy(DiskCacheStrategy.ALL) // 원본 + 변환된 이미지 모두 캐시
                            .preload() // 실제로 표시하지 않고 캐시만 수행

                        Log.d(TAG, "이미지 캐싱 완료: $url")
                        true
                    } catch (e: Exception) {
                        Log.e(TAG, "이미지 캐싱 실패: $url", e)
                        false
                    }
                }
            }

            val results = cacheJobs.awaitAll()
            val successCount = results.count { it }
            val failCount = results.count { !it }

            Log.d(TAG, "이미지 캐싱 완료 - 성공: $successCount, 실패: $failCount")

            if (failCount > 0) {
                // 일부 실패했지만 재시도하지 않음 (선택적)
                Result.success()
            } else {
                Result.success()
            }
        } catch (e: Exception) {
            Log.e(TAG, "이미지 캐싱 중 오류", e)
            Result.retry()
        }
    }
}