package com.tenacy.roadcapture.worker

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.facebook.internal.NativeProtocol.EXTRA_USER_ID
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.tenacy.roadcapture.data.pref.UserPref
import com.tenacy.roadcapture.util.*
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.UUID
import java.util.concurrent.TimeUnit

@HiltWorker
class UpdateUsernameWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        private const val TAG = "UpdateUsernameWorker"
        const val KEY_USER_ID = "user_id"
        const val KEY_USERNAME = "username"
        const val KEY_RESULT_ERROR_MESSAGE = "result_error_message"

        fun enqueueOneTimeWork(context: Context, userId: String, username: String): UUID {
            val inputData = workDataOf(
                KEY_USER_ID to userId,
                KEY_USERNAME to username
            )

            val workRequest = OneTimeWorkRequestBuilder<UpdateUsernameWorker>()
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
                    Constants.USER_WORK_NAME_UPDATE_NAME,
                    ExistingWorkPolicy.KEEP,
                    workRequest
                )

            return workRequest.id
        }

        fun cancelWork(context: Context) {
            Log.d(TAG, "사용자 이름 업데이트 워커 취소")
            WorkManager.getInstance(context).cancelAllWorkByTag(TAG)
        }
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val userId = inputData.getString(KEY_USER_ID) ?: return@withContext Result.failure()
            val username = inputData.getString(KEY_USERNAME) ?: return@withContext Result.failure()

            Log.d(TAG, "사용자 이름 업데이트 시작: userId=$userId, username=$username")

            // Firebase Auth 프로필 업데이트
            val auth = com.google.firebase.auth.FirebaseAuth.getInstance()
            val currentUser = auth.currentUser

            if (currentUser != null) {
                val profileUpdates = UserProfileChangeRequest.Builder()
                    .setDisplayName(username)
                    .build()

                currentUser.updateProfile(profileUpdates).await()
                Log.d(TAG, "Firebase Auth 프로필 업데이트 완료")
            } else {
                Log.w(TAG, "현재 사용자가 로그인되어 있지 않음")
            }

            // Firestore 데이터베이스 업데이트
            val db = FirebaseFirestore.getInstance()
            val userRef = db.collection(FirebaseConstants.COLLECTION_USERS).document(userId)

            // 사용자가 소유한 모든 앨범 찾기
            val albumRefs = db.collection(FirebaseConstants.COLLECTION_ALBUMS)
                .whereEqualTo("userRef", userRef).getAllReferences()

            // 해당 앨범을 스크랩한 항목 찾기
            val scrapRefs = db.collection(FirebaseConstants.COLLECTION_SCRAPS)
                .whereInAllReferences("albumRefs", albumRefs)

            // 일괄 업데이트 작업 준비
            val allOperations = mutableListOf<BatchOperation>()

            // 앨범 업데이트
            albumRefs.forEach {
                allOperations.add(UpdateDocumentOperation(
                    it,
                    mapOf(
                        "userDisplayName" to username,
                        "updatedAt" to FieldValue.serverTimestamp(),
                    )
                ))
            }

            // 스크랩 업데이트
            scrapRefs.forEach {
                allOperations.add(UpdateDocumentOperation(it, mapOf("albumUserDisplayName" to username)))
            }

            // 사용자 문서 업데이트
            allOperations.add(UpdateDocumentOperation(userRef, mapOf(
                "displayName" to username,
                "updatedAt" to FieldValue.serverTimestamp()
            )))

            // 일괄 작업 실행
            db.executeInBatches(allOperations)

            // 사용자 환경설정 업데이트
            UserPref.displayName = username

            Log.d(TAG, "사용자 이름 업데이트 완료")

            // 성공 브로드캐스트 인텐트 전송
            Result.success()

        } catch (e: Exception) {
            Log.e(TAG, "사용자 이름 업데이트 중 오류 발생", e)

            // 실패 브로드캐스트 인텐트 전송
            if (runAttemptCount < 3) {
                Result.retry()
            } else {
                Result.failure()
            }
        }
    }
}