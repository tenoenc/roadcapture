package com.tenacy.roadcapture.worker

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.tenacy.roadcapture.util.*
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID
import java.util.concurrent.TimeUnit

@HiltWorker
class UpdateAlbumPublicWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        const val TAG = "UpdatePublicWorker"
        const val KEY_ALBUM_ID = "album_id"
        const val KEY_PUBLIC = "public"

        private fun getUniqueWorkName(albumId: String): String {
            return "${Constants.ALBUM_WORK_NAME_UPDATE_PUBLIC}_${albumId}"
        }

        fun enqueueOneTimeWork(context: Context, albumId: String, isPublic: Boolean): UUID {
            val inputData = workDataOf(
                KEY_ALBUM_ID to albumId,
                KEY_PUBLIC to isPublic,
            )

            val workRequest = OneTimeWorkRequestBuilder<UpdateAlbumPublicWorker>()
                .setInputData(inputData)
                .addTag(TAG)
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

            val uniqueWorkName = getUniqueWorkName(albumId)

            WorkManager.getInstance(context)
                .enqueueUniqueWork(
                    uniqueWorkName,
                    ExistingWorkPolicy.KEEP,
                    workRequest
                )

            return workRequest.id
        }

        fun cancelWork(context: Context) {
            Log.d(TAG, "앨범 공개 여부 업데이트 워커 취소")
            WorkManager.getInstance(context).cancelAllWorkByTag(TAG)
        }
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val albumId = inputData.getString(KEY_ALBUM_ID) ?: return@withContext Result.failure()
            val isPublic = inputData.getBoolean(KEY_PUBLIC, false)

            Log.d(TAG, "앨범 공개 여부 업데이트 시작: albumId=$albumId, isPublic=$isPublic")

            val albumRef = db.collection("albums").document(albumId)
            val scrapRefs = db.collection("scraps")
                .whereEqualTo("albumRef", albumRef).getAllReferences()

            val allOperations = mutableListOf<BatchOperation>()
            scrapRefs.forEach {
                allOperations.add(UpdateDocumentOperation(it, mapOf("albumPublic" to isPublic)))
            }
            allOperations.add(UpdateDocumentOperation(albumRef, mapOf("isPublic" to isPublic)))
            db.executeInBatches(allOperations)

            Log.d(TAG, "앨범 공개 여부 업데이트 완료")

            val outputData = workDataOf(
                KEY_ALBUM_ID to albumId,
                KEY_PUBLIC to isPublic,
            )

            // 성공 브로드캐스트 인텐트 전송
            Result.success(outputData)

        } catch (e: Exception) {
            Log.e(TAG, "앨범 공개 여부 업데이트 중 오류 발생", e)

            // 실패 브로드캐스트 인텐트 전송
            if (runAttemptCount < 3) {
                Result.retry()
            } else {
                Result.failure()
            }
        }
    }
}