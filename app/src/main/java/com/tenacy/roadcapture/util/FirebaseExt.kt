package com.tenacy.roadcapture.util

import android.content.Context
import android.net.Uri
import android.util.Log
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.WriteBatch
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.google.firebase.storage.StorageReference
import com.google.firebase.storage.ktx.storage
import com.tenacy.roadcapture.BuildConfig
import com.tenacy.roadcapture.data.firebase.dto.*
import com.tenacy.roadcapture.util.FirebaseConstants.DEFAULT_PROFILE_PATH
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

val auth get() = Firebase.auth
val user get() = auth.currentUser
val db get() = Firebase.firestore
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

fun DocumentSnapshot.toAlbum(): FirebaseAlbum {
    val id = id
    val title = getString("title") ?: ""
    val createdAt = getTimestamp("createdAt")!!.toDate().toLocalDateTime()
    val endedAt = getTimestamp("endedAt")!!.toDate().toLocalDateTime()
    val thumbnailUrl = getString("thumbnailUrl") ?: ""
    val viewCount = getLong("viewCount")?.toInt() ?: 0
    val scrapCount = getLong("scrapCount")?.toInt() ?: 0
    val regionTags = get("regionTags") as? List<Map<String, String>> ?: emptyList()
    val isPublic = getBoolean("isPublic") ?: false
    val userId = getDocumentReference("userRef")?.id ?: ""
    val userDisplayName = getString("userDisplayName") ?: ""
    val userPhotoUrl = getString("userPhotoUrl") ?: ""
    val memoryAddressTags = get("memoryAddressTags") as? List<String> ?: emptyList()
    val memoryPlaceNames = get("memoryPlaceNames") as? List<String> ?: emptyList()

    return FirebaseAlbum(
        id = id,
        title = title,
        createdAt = createdAt,
        endedAt = endedAt,
        thumbnailUrl = thumbnailUrl,
        viewCount = viewCount,
        scrapCount = scrapCount,
        regionTags = regionTags,
        isPublic = isPublic,
        userId = userId,
        userDisplayName = userDisplayName,
        userPhotoUrl = userPhotoUrl,
        memoryAddressTags = memoryAddressTags,
        memoryPlaceNames = memoryPlaceNames,
    )
}

fun DocumentSnapshot.toMemory(): FirebaseMemory {
    val id = id
    val content = getString("content") ?: ""
    val photoUrl = getString("photoUrl") ?: ""
    val photoName = getString("photoName") ?: ""
    val placeName = getString("placeName") ?: ""
    val addressTags = get("addressTags") as? List<String> ?: emptyList()
    val formattedAddress = getString("formattedAddress") ?: ""
    val locationRefId = getDocumentReference("locationRef")?.id ?: ""
    val isPublic = getBoolean("isPublic") ?: false
    val createdAt = getTimestamp("createdAt")!!.toDate().toLocalDateTime()

    return FirebaseMemory(
        id = id,
        content = content,
        photoUrl = photoUrl,
        photoName = photoName,
        placeName = placeName,
        addressTags = addressTags,
        formattedAddress = formattedAddress,
        locationRefId = locationRefId,
        isPublic = isPublic,
        createdAt = createdAt,
    )
}

fun DocumentSnapshot.toLocation(): FirebaseLocation {
    val id = id
    val latitude = getDouble("latitude") ?: 0.0
    val longitude = getDouble("longitude") ?: 0.0
    val createdAt = getTimestamp("createdAt")!!.toDate().toLocalDateTime()

    return FirebaseLocation(
        id = id,
        latitude = latitude,
        longitude = longitude,
        createdAt = createdAt,
    )
}

fun DocumentSnapshot.toUser(): FirebaseUser {
    val uid = id
    val displayName = getString("displayName") ?: ""
    val photoName = getString("photoName") ?: ""
    val photoUrl = getString("photoUrl") ?: ""
    val provider = getString("provider") ?: ""
    val scrapCount = getLong("scrapCount") ?: 0L
    val createdAt = getTimestamp("createdAt")!!.toDate().toLocalDateTime()
    val updatedAt = getTimestamp("updatedAt")!!.toDate().toLocalDateTime()

    return FirebaseUser(
        id = uid,
        displayName = displayName,
        photoName = photoName,
        photoUrl = photoUrl,
        provider = provider,
        scrapCount = scrapCount,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )
}

/**
 * 배치 작업을 최대 사이즈(500)에 맞게 나누어 실행하는 확장 함수
 * @param operations 실행할 작업 목록 (각 작업은 BatchOperation 타입)
 */
suspend fun FirebaseFirestore.executeInBatches(operations: List<BatchOperation>) {
    if (operations.isEmpty()) return

    var currentBatch = batch()
    var operationCount = 0
    val maxBatchSize = 490 // 안전 마진 확보

    operations.forEach { operation ->
        // 현재 배치가 가득 차면 커밋하고 새 배치 시작
        if (operationCount + 1 > maxBatchSize) {
            currentBatch.commit().await()
            currentBatch = batch()
            operationCount = 0
        }

        // 작업 실행
        operation.execute(currentBatch)
        operationCount++
    }

    // 마지막 배치 커밋 (남은 작업이 있는 경우)
    if (operationCount > 0) {
        currentBatch.commit().await()
    }
}

/**
 * 배치 작업 인터페이스
 */
interface BatchOperation {
    fun execute(batch: WriteBatch)
}

/**
 * 문서 생성 배치 작업
 */
class SetDocumentOperation(
    private val documentRef: DocumentReference,
    private val data: Map<String, Any?>
) : BatchOperation {
    override fun execute(batch: WriteBatch) {
        batch.set(documentRef, data)
    }
}

/**
 * 여러 작업을 배치로 나누어 실행하는 확장 함수
 * @param T 데이터 타입
 * @param collectionPath 컬렉션 경로
 * @param items 저장할 데이터 아이템 목록
 * @param transform 데이터 변환 함수 (선택적)
 */
suspend fun <T> FirebaseFirestore.setCollectionInBatches(
    collectionPath: String,
    items: List<T>,
    transform: (T) -> Map<String, Any> = { it as Map<String, Any> }
): List<DocumentReference> {
    val documentRefs = items.map { collection(collectionPath).document() }

    val operations = items.mapIndexed { index, item ->
        SetDocumentOperation(documentRefs[index], transform(item))
    }

    executeInBatches(operations)
    return documentRefs
}

suspend fun Context.uploadImageToStorage(
    uri: Uri,
    storagePath: String
): String = withContext(Dispatchers.IO) {
    try {
        // 이미지를 위한 고유 경로 생성
        val storageRef = storage.reference.child(storagePath)

        // 콘텐츠 URI에서 입력 스트림 열기
        val inputStream = contentResolver.openInputStream(uri)
            ?: throw IllegalStateException("Cannot open input stream for URI: $uri")

        // 이미지 업로드
        val bytes = inputStream.readBytes()
        inputStream.close()

        val uploadTask = storageRef.putBytes(bytes).await()

        // 다운로드 URL 가져오기
        val downloadUrl = storageRef.downloadUrl.await().toString()

        // Storage 참조 ID와 URL 반환
        downloadUrl
    } catch (e: Exception) {
        throw Exception("Failed to upload image: ${e.message}", e)
    }
}