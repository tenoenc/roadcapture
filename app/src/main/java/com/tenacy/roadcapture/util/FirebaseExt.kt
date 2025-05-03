package com.tenacy.roadcapture.util

import android.util.Log
import com.google.firebase.Timestamp
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.google.firebase.storage.StorageReference
import com.google.firebase.storage.ktx.storage
import com.tenacy.roadcapture.BuildConfig
import com.tenacy.roadcapture.data.firebase.dto.FirebaseAlbum
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
suspend fun setDefaultProfileImage(storagePath: String): String = withContext(Dispatchers.IO) {
    try {
        // 1. 기본 이미지 참조
        val defaultImageRef = storageRef.child(DEFAULT_PROFILE_PATH)

        // 2. 새 사용자 이미지 경로 생성
        val userImageRef = storageRef.child(storagePath)

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

fun DocumentSnapshot.toAlbum(user: FirebaseAlbum.User): FirebaseAlbum {
    val id = id
    val title = getString("title") ?: ""
    val createdAt = getTimestamp("createdAt")!!.toDate().toLocalDateTime()
    val endedAt = getTimestamp("endedAt")!!.toDate().toLocalDateTime()
    val thumbnailUrl = getString("thumbnailUrl") ?: ""
    val viewCount = getLong("viewCount")?.toInt() ?: 0
    val likeCount = getLong("likeCount")?.toInt() ?: 0
    val isPublic = getBoolean("isPublic") ?: false

    // 중첩 리스트 처리
    @Suppress("UNCHECKED_CAST")
    val regionTags = get("regionTags") as? List<Map<String, String>> ?: emptyList()

    // 복잡한 중첩 객체 변환
    @Suppress("UNCHECKED_CAST")
    val locationsData = get("locations") as? List<Map<String, Any>> ?: emptyList()
    val locations = locationsData.map { locData ->
        FirebaseAlbum.Location(
            id = locData["id"] as? String ?: "",
            latitude = (locData["latitude"] as? Number)?.toDouble() ?: 0.0,
            longitude = (locData["longitude"] as? Number)?.toDouble() ?: 0.0,
            createdAt = (locData["createdAt"] as? Timestamp)?.toDate()?.toLocalDateTime()
        )
    }

    @Suppress("UNCHECKED_CAST")
    val memoriesData = get("memories") as? List<Map<String, Any>> ?: emptyList()
    val memories = memoriesData.map { memData ->
        FirebaseAlbum.Memory(
            id = memData["id"] as? String ?: "",
            content = memData["content"] as? String ?: "",
            photoUrl = memData["photoUrl"] as? String ?: "",
            photoName = memData["photoName"] as? String ?: "",
            placeName = memData["placeName"] as? String ?: "",
            addressTags = memData["addressTags"] as? List<String> ?: emptyList(),
            formattedAddress = memData["formattedAddress"] as? String ?: "",
            locationRefId = memData["locationRefId"] as? String ?: "",
            createdAt = (memData["createdAt"] as? Timestamp)?.toDate()?.toLocalDateTime()
        )
    }

    return FirebaseAlbum(
        id = id,
        title = title,
        createdAt = createdAt,
        endedAt = endedAt,
        thumbnailUrl = thumbnailUrl,
        viewCount = viewCount,
        likeCount = likeCount,
        regionTags = regionTags,
        user = user,
        isPublic = isPublic,
        locations = locations,
        memories = memories
    )
}

