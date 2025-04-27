package com.tenacy.roadcapture.util

import android.net.Uri
import android.util.Log
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.google.firebase.storage.StorageReference
import com.google.firebase.storage.ktx.storage
import com.tenacy.roadcapture.BuildConfig
import com.tenacy.roadcapture.util.FirebaseConstants.DEFAULT_PROFILE_PATH
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

val auth get() = Firebase.auth
val user get() = auth.currentUser
val db get() = Firebase.firestore("roadcapture-db")
val storage get() = Firebase.storage(BuildConfig.STORAGE_BASE_URL)
private val storageRef = storage.reference


/**
 * 기본 프로필 이미지를 사용자의 프로필 이미지로 복사합니다.
 * @param userId 사용자 UID
 * @return 복사된 이미지의 다운로드 URL
 */
suspend fun setDefaultProfileImage(userId: String): String = withContext(Dispatchers.IO) {
    try {
        // 1. 기본 이미지 참조
        val defaultImageRef = storageRef.child(DEFAULT_PROFILE_PATH)

        // 2. 새 사용자 이미지 경로 생성
        val userImagePath = "images/$userId/profile.jpg"
        val userImageRef = storageRef.child(userImagePath)

        // 3. 이미지 복사 (기본 이미지 → 사용자 이미지)
        return@withContext copyImage(defaultImageRef, userImageRef)

    } catch (e: Exception) {
        Log.e("ProfileManager", "프로필 이미지 설정 실패", e)
        throw e
    }
}

/**
 * Firebase Storage 내에서 이미지를 복사합니다.
 */
private suspend fun copyImage(sourceRef: StorageReference, destinationRef: StorageReference): String {
    // 1. 원본 파일의 다운로드 URL 가져오기
    val sourceUrl = sourceRef.downloadUrl.await().toString()

    // 2. 원본 파일 바이트 데이터로 다운로드
    val bytes = sourceRef.getBytes(Long.MAX_VALUE).await()

    // 3. 대상 경로에 바이트 데이터 업로드
    destinationRef.putBytes(bytes).await()

    // 4. 새 이미지의 다운로드 URL 가져오기
    return destinationRef.downloadUrl.await().toString()
}

suspend fun uploadImageIfExists(uri: Uri?) = uri?.let {
    val storageRef = storage.reference
    val riversRef = storageRef.child("images/${user!!.uid}/${it.lastPathSegment}")
    val uploadTask = riversRef.putFile(it)
    uploadTask
        .continueWith { task ->
            task.exception ?: throw task.exception!!
            riversRef.downloadUrl
        }.await()
}