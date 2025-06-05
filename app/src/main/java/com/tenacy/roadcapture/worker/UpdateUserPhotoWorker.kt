package com.tenacy.roadcapture.worker

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.firestore.FieldValue
import com.tenacy.roadcapture.data.pref.UserPref
import com.tenacy.roadcapture.util.*
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.*
import java.util.concurrent.TimeUnit

@HiltWorker
class UpdateUserPhotoWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        private const val TAG = "UpdateUserPhotoWorker"
        const val KEY_PHOTO_URI = "photo_uri"

        fun enqueueOneTimeWork(context: Context, photoUri: Uri): UUID {
            val inputData = workDataOf(
                KEY_PHOTO_URI to photoUri.toString(),
            )

            val workRequest = OneTimeWorkRequestBuilder<UpdateUserPhotoWorker>()
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

            WorkManager.getInstance(context)
                .enqueueUniqueWork(
                    Constants.USER_WORK_NAME_UPDATE_PHOTO,
                    ExistingWorkPolicy.KEEP,
                    workRequest
                )

            return workRequest.id
        }

        fun cancelWork(context: Context) {
            Log.d(TAG, "프로필 사진 업데이트 워커 취소")
            WorkManager.getInstance(context).cancelAllWorkByTag(TAG)
        }
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val photoUri = inputData.getString(KEY_PHOTO_URI)?.let(Uri::parse) ?: return@withContext Result.failure()
            val userId = UserPref.id

            Log.d(TAG, "프로필 사진 업데이트 시작: userId=$userId, photoUri=$photoUri")

            val userRef = db.collection("users")
                .document(userId)

            val albumRefs = db.collection("albums")
                .whereEqualTo("userRef", userRef).getAllReferences()

            val compressedUri = context.compressImage(quality = 10, contentUri = photoUri)

            val storagePath = "images/users/$userId/profile.jpg"
            val imageUrl = context.uploadImageToStorage(compressedUri, storagePath)

            val profileUpdates = UserProfileChangeRequest.Builder()
                .setPhotoUri(Uri.parse(imageUrl))
                .build()

            user?.updateProfile(profileUpdates)?.await()

            val allOperations = mutableListOf<BatchOperation>()
            albumRefs.forEach {
                allOperations.add(UpdateDocumentOperation(it, mapOf("userPhotoUrl" to imageUrl)))
            }
            allOperations.add(UpdateDocumentOperation(userRef, mapOf(
                "photoName" to storagePath,
                "photoUrl" to imageUrl,
                "updatedAt" to FieldValue.serverTimestamp(),
            )))
            db.executeInBatches(allOperations)

            UserPref.photoUrl = imageUrl

            Log.d(TAG, "프로필 사진 업데이트 완료")

            // 성공 브로드캐스트 인텐트 전송
            Result.success()

        } catch (e: Exception) {
            Log.e(TAG, "프로필 사진 업데이트 중 오류 발생", e)

            // 실패 브로드캐스트 인텐트 전송
            if (runAttemptCount < 3) {
                Result.retry()
            } else {
                Result.failure()
            }
        }
    }
}