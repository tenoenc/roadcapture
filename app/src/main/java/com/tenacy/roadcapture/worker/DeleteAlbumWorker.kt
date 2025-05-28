package com.tenacy.roadcapture.worker

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.google.firebase.storage.FirebaseStorage
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

@HiltWorker
class DeleteAlbumWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        private const val TAG = "DeleteAlbumWorker"
        private const val KEY_USER_ID = "user_id"
        private const val KEY_ALBUM_ID = "album_id"

        fun createWorkRequest(userId: String, albumId: String): OneTimeWorkRequest {
            val inputData = workDataOf(
                KEY_USER_ID to userId,
                KEY_ALBUM_ID to albumId
            )

            return OneTimeWorkRequestBuilder<DeleteAlbumWorker>()
                .setInputData(inputData)
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
        }
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val userId = inputData.getString(KEY_USER_ID) ?: return@withContext Result.failure()
            val albumId = inputData.getString(KEY_ALBUM_ID) ?: return@withContext Result.failure()

            Log.d(TAG, "앨범 삭제 시작: userId=$userId, albumId=$albumId")

            val storage = FirebaseStorage.getInstance()
            val albumRef = storage.reference.child("images/albums/$userId/$albumId")

            // 폴더 내 모든 파일 리스트 가져오기
            val listResult = albumRef.listAll().await()

            // 모든 파일 삭제
            val deleteResults = listResult.items.map { fileRef ->
                try {
                    fileRef.delete().await()
                    Log.d(TAG, "파일 삭제 성공: ${fileRef.path}")
                    true
                } catch (e: Exception) {
                    Log.e(TAG, "파일 삭제 실패: ${fileRef.path}", e)
                    false
                }
            }

            // 하위 폴더가 있다면 재귀적으로 삭제
            listResult.prefixes.forEach { prefix ->
                deleteFolder(prefix)
            }

            val successCount = deleteResults.count { it }
            val totalCount = deleteResults.size

            Log.d(TAG, "앨범 삭제 완료: $successCount/$totalCount 파일 삭제됨")

            if (successCount == totalCount) {
                Result.success()
            } else if (successCount > 0) {
                // 부분 성공
                Result.success(
                    workDataOf(
                        "deleted_count" to successCount,
                        "total_count" to totalCount
                    )
                )
            } else {
                // 재시도
                if (runAttemptCount < 3) {
                    Result.retry()
                } else {
                    Result.failure()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "앨범 삭제 중 오류 발생", e)
            if (runAttemptCount < 3) {
                Result.retry()
            } else {
                Result.failure()
            }
        }
    }

    private suspend fun deleteFolder(folderRef: com.google.firebase.storage.StorageReference) {
        try {
            val listResult = folderRef.listAll().await()

            // 파일 삭제
            listResult.items.forEach { fileRef ->
                try {
                    fileRef.delete().await()
                } catch (e: Exception) {
                    Log.e(TAG, "하위 파일 삭제 실패: ${fileRef.path}", e)
                }
            }

            // 하위 폴더 재귀적 삭제
            listResult.prefixes.forEach { prefix ->
                deleteFolder(prefix)
            }
        } catch (e: Exception) {
            Log.e(TAG, "폴더 삭제 중 오류: ${folderRef.path}", e)
        }
    }
}