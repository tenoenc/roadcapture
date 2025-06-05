package com.tenacy.roadcapture.worker

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.tenacy.roadcapture.util.*
import com.tenacy.roadcapture.worker.UpdateAlbumPublicWorker.Companion.KEY_PUBLIC
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.UUID
import java.util.concurrent.TimeUnit

@HiltWorker
class CreateShareLinkWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        const val TAG = "CreateShareLinkWorker"
        const val KEY_ALBUM_ID = "album_id"
        const val KEY_ID_TOKEN = "id_token"
        const val RESULT_SHARE_LINK = "share_link"

        private fun getUniqueWorkName(albumId: String): String {
            return "${Constants.ALBUM_WORK_NAME_CREATE_SHARE_LINK}_${albumId}"
        }

        fun enqueueOneTimeWork(context: Context, albumId: String, idToken: String): UUID {
            val inputData = workDataOf(
                KEY_ALBUM_ID to albumId,
                KEY_ID_TOKEN to idToken,
            )

            val workRequest = OneTimeWorkRequestBuilder<CreateShareLinkWorker>()
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

        fun cancelAll(context: Context) {
            Log.d(TAG, "공유 링크 생성 워커 취소")
            WorkManager.getInstance(context).cancelAllWorkByTag(TAG)
        }
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val albumId = inputData.getString(KEY_ALBUM_ID) ?: return@withContext Result.failure()
            val idToken = inputData.getString(KEY_ID_TOKEN) ?: return@withContext Result.failure()

            Log.d(TAG, "공유 링크 생성 시작: albumId=$albumId")

            val response = RetrofitInstance.firebaseApi.shareAlbum(
                authToken = "Bearer $idToken",
                albumId = albumId,
            )
            if(!response.isSuccessful) {
                throw Exception(response.errorBody()?.string())
            }
            val responseDto = response.body() ?: throw Exception()

            Log.d(TAG, "공유 링크 생성 완료")

            val outputData = workDataOf(
                RESULT_SHARE_LINK to responseDto.shareLink,
            )

            // 성공 브로드캐스트 인텐트 전송
            Result.success(outputData)

        } catch (e: Exception) {
            Log.e(TAG, "공유 링크 생성 중 오류 발생", e)

            // 실패 브로드캐스트 인텐트 전송
            if (runAttemptCount < 3) {
                Result.retry()
            } else {
                Result.failure()
            }
        }
    }
}