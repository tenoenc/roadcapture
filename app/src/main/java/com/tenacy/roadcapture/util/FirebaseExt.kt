package com.tenacy.roadcapture.util

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.fragment.app.Fragment
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.*
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.functions.ktx.functions
import com.google.firebase.ktx.Firebase
import com.google.firebase.storage.StorageReference
import com.google.firebase.storage.ktx.storage
import com.tenacy.roadcapture.BuildConfig
import com.tenacy.roadcapture.data.firebase.dto.*
import com.tenacy.roadcapture.data.firebase.exception.SystemConfigException
import com.tenacy.roadcapture.data.firebase.exception.UnderMaintenanceException
import com.tenacy.roadcapture.data.firebase.exception.UpdateRequiredException
import com.tenacy.roadcapture.ui.BaseViewModel
import com.tenacy.roadcapture.ui.CommonSystemViewEvent
import com.tenacy.roadcapture.ui.GlobalViewEvent
import com.tenacy.roadcapture.util.FirebaseConstants.DEFAULT_PROFILE_PATH
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

val auth get() = Firebase.auth
val user get() = auth.currentUser
val db get() = Firebase.firestore
val storage get() = Firebase.storage(BuildConfig.STORAGE_BASE_URL)
val functions get() = Firebase.functions
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
    val isLocked = getBoolean("isLocked") ?: false
    val lockReason = getString("lockReason") ?: ""
    val lockedAt = getTimestamp("lockedAt")?.toDate()?.toLocalDateTime()
    val lockedBy = getString("lockedBy") ?: ""
    val branchLink = getString("branchLink")
    val shareId = getString("shareId")
    val shareCreatedAt = getTimestamp("shareCreatedAt")?.toDate()?.toLocalDateTime()
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
        isLocked = isLocked,
        lockReason = lockReason,
        lockedAt = lockedAt,
        lockedBy = lockedBy,
        branchLink = branchLink,
        shareId = shareId,
        shareCreatedAt = shareCreatedAt,
        userId = userId,
        userDisplayName = userDisplayName,
        userPhotoUrl = userPhotoUrl,
        memoryAddressTags = memoryAddressTags,
        memoryPlaceNames = memoryPlaceNames,
    )
}

fun DocumentSnapshot.toMemory(): FirebaseMemory {
    val id = id
    val albumId = getDocumentReference("albumRef")?.id ?: ""
    val userId = getDocumentReference("userRef")?.id ?: ""
    val isThumbnail = getBoolean("isThumbnail") ?: false
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
        albumId = albumId,
        userId = userId,
        isThumbnail = isThumbnail,
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
    val todayMemoryCount = getLong("todayMemoryCount") ?: 0L
    val scrapCount = getLong("scrapCount") ?: 0L
    val createdAt = getTimestamp("createdAt")!!.toDate().toLocalDateTime()
    val updatedAt = getTimestamp("updatedAt")!!.toDate().toLocalDateTime()

    return FirebaseUser(
        id = uid,
        displayName = displayName,
        photoName = photoName,
        photoUrl = photoUrl,
        provider = provider,
        todayMemoryCount = todayMemoryCount,
        scrapCount = scrapCount,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )
}

fun DocumentSnapshot.toSystemConfig(): FirebaseSystemConfig {
    val appVersion = getString("appVersion") ?: BuildConfig.VERSION_NAME
    val underMaintenance = getBoolean("underMaintenance") ?: false
    val updateRequired = getBoolean("updateRequired") ?: false

    return FirebaseSystemConfig(
        appVersion = appVersion.trim(),
        underMaintenance = underMaintenance,
        updateRequired = updateRequired,
    )
}

fun DocumentSnapshot.toSystemConfigV2(): FirebaseSystemConfigV2 {
    val minAppVersion = getString("minAppVersion") ?: BuildConfig.VERSION_NAME
    val underMaintenance = getBoolean("underMaintenance") ?: false

    return FirebaseSystemConfigV2(
        minAppVersion = minAppVersion.trim(),
        underMaintenance = underMaintenance,
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

class UpdateDocumentOperation(
    private val documentRef: DocumentReference,
    private val data: Map<String, Any?>
) : BatchOperation {
    override fun execute(batch: WriteBatch) {
        batch.update(documentRef, data)
    }
}

class DeleteDocumentOperation(
    private val documentRef: DocumentReference,
) : BatchOperation {
    override fun execute(batch: WriteBatch) {
        batch.delete(documentRef)
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

/**
 * Firestore 쿼리 결과를 모두 가져오는 확장 함수
 * 1000개 제한을 우회하여 모든 결과를 반환
 */
suspend fun <T> Query.getAll(
    transform: (DocumentSnapshot) -> T
): List<T> = withContext(Dispatchers.IO) {
    val resultList = mutableListOf<T>()
    var query = this@getAll
    var lastDocumentSnapshot: DocumentSnapshot? = null
    val firestoreLimit = 1000L // Firestore의 고정 제한

    do {
        // 마지막 문서 이후부터 쿼리 수행
        if (lastDocumentSnapshot != null) {
            query = query.startAfter(lastDocumentSnapshot)
        }

        // 결과 가져오기 (최대 1000개)
        val querySnapshot = query.limit(firestoreLimit).get().await()

        // 결과가 없으면 종료
        if (querySnapshot.isEmpty) {
            break
        }

        // 결과 변환하여 추가
        querySnapshot.documents.forEach { document ->
            resultList.add(transform(document))
        }

        // 마지막 문서 저장
        lastDocumentSnapshot = querySnapshot.documents.lastOrNull()

        // 결과가 1000개 미만이면 모든 결과를 가져온 것
    } while (querySnapshot.size() >= firestoreLimit)

    resultList
}

/**
 * 모든 문서 참조를 가져오는 확장 함수
 */
suspend fun Query.getAllReferences(): List<DocumentReference> {
    return getAll { it.reference }
}

/**
 * Firestore의 whereIn 10개 제한을 우회하는 확장 함수
 *
 * @param field 필터링할 필드 이름
 * @param values 필드와 비교할 값 목록 (10개 이상도 처리 가능)
 * @return 모든 조건에 맞는 문서를 포함하는 결과 목록
 */
suspend fun <T> CollectionReference.whereInAll(
    field: String,
    values: List<T>
): List<DocumentSnapshot> = withContext(Dispatchers.IO) {
    // 결과를 저장할 목록
    val allResults = mutableListOf<DocumentSnapshot>()

    // values가 비어있으면 빈 목록 반환
    if (values.isEmpty()) {
        return@withContext allResults
    }

    // values를 10개씩 청크로 나누기 (Firestore whereIn 제한)
    val chunks = values.chunked(10)

    // 각 청크에 대해 별도의 쿼리 실행
    val queryResults = chunks.map { chunk ->
        async {
            this@whereInAll.whereIn(field, chunk).get().await().documents
        }
    }.awaitAll()

    // 모든 결과 병합 (중복 제거)
    val documentMap = mutableMapOf<String, DocumentSnapshot>()

    queryResults.forEach { documents ->
        documents.forEach { document ->
            documentMap[document.id] = document
        }
    }

    // Map의 값들을 리스트로 변환
    allResults.addAll(documentMap.values)

    allResults
}

/**
 * 문서 참조를 반환하는 whereInAll 버전
 */
suspend fun <T> CollectionReference.whereInAllReferences(
    field: String,
    values: List<T>
): List<DocumentReference> {
    return whereInAll(field, values).map { it.reference }
}

/**
 * Firestore의 whereIn 10개 제한을 우회하는 확장 함수 (복합 조건 지원)
 *
 * @param inField whereIn으로 필터링할 필드 이름
 * @param inValues 해당 필드와 비교할 값 목록
 * @param queryModifier 추가 쿼리 조건을 적용하는 함수
 * @return 모든 조건에 맞는 문서를 포함하는 결과 목록
 */
suspend fun <T> CollectionReference.whereInWithFilters(
    inField: String,
    inValues: List<T>,
    queryModifier: (Query) -> Query
): List<DocumentSnapshot> = withContext(Dispatchers.IO) {
    // 결과를 저장할 목록
    val allResults = mutableListOf<DocumentSnapshot>()

    // inValues가 비어있으면 빈 목록 반환
    if (inValues.isEmpty()) {
        return@withContext allResults
    }

    // inValues를 10개씩 청크로 나누기 (Firestore whereIn 제한)
    val chunks = inValues.chunked(10)

    // 각 청크에 대해 별도의 쿼리 실행
    val queryResults = chunks.map { chunk ->
        async {
            val baseQuery = this@whereInWithFilters.whereIn(inField, chunk)
            val modifiedQuery = queryModifier(baseQuery)
            modifiedQuery.get().await().documents
        }
    }.awaitAll()

    // 모든 결과 병합 (중복 제거)
    val documentMap = mutableMapOf<String, DocumentSnapshot>()

    queryResults.forEach { documents ->
        documents.forEach { document ->
            documentMap[document.id] = document
        }
    }

    // Map의 값들을 리스트로 변환
    allResults.addAll(documentMap.values)

    allResults
}

/**
 * 문서 참조를 반환하는 whereInWithFilters 버전
 */
suspend fun <T> CollectionReference.whereInWithFiltersReferences(
    inField: String,
    inValues: List<T>,
    queryModifier: (Query) -> Query
): List<DocumentReference> {
    return whereInWithFilters(inField, inValues, queryModifier).map { it.reference }
}

/**
 * 모든 문서 ID를 가져오는 확장 함수
 */
suspend fun Query.getAllIds(): List<String> {
    return getAll { it.id }
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

/*
suspend fun validateSystemConfig() {
    val systemRef = db.collection(FirebaseConstants.COLLECTION_SYSTEMS)
        .document(FirebaseConstants.DOCUMENT_CONFIG)

    val systemConfig = systemRef.get().await().toSystemConfig()

    if(systemConfig.isUpdateRequired()) {
        throw UpdateRequiredException()
    }
    if(systemConfig.isUnderMaintenance()) {
        throw UnderMaintenanceException()
    }
}
*/

suspend fun validateSystemConfigV2() {
    val systemRef = db.collection(FirebaseConstants.COLLECTION_SYSTEMS)
        .document(FirebaseConstants.DOCUMENT_CONFIG_V2)

    val systemConfig = systemRef.get().await().toSystemConfigV2()

    val version = Version(BuildConfig.VERSION_NAME)
    val minAppVersion = Version(systemConfig.minAppVersion)

    if(version < minAppVersion) {
        throw UpdateRequiredException()
    }
    if(systemConfig.isUnderMaintenance()) {
        throw UnderMaintenanceException()
    }
}

fun BaseViewModel.handleSystemConfigException(exception: SystemConfigException) {
    when(exception) {
        is UpdateRequiredException -> {
            viewEvent(CommonSystemViewEvent.UpdateRequired)
        }
        is UnderMaintenanceException -> {
            viewEvent(CommonSystemViewEvent.UnderMaintenance)
        }
    }
}

fun Fragment.handleSystemConfigException(exception: SystemConfigException) {
    when(exception) {
        is UpdateRequiredException -> {
            mainActivity.vm.viewEvent(GlobalViewEvent.UpdateRequired)
        }
        is UnderMaintenanceException -> {
            mainActivity.vm.viewEvent(GlobalViewEvent.UnderMaintenance)
        }
    }
}

fun Fragment.handleCommonSystemViewEvents(event: CommonSystemViewEvent) {
    when(event) {
        CommonSystemViewEvent.UpdateRequired -> {
            mainActivity.vm.viewEvent(GlobalViewEvent.UpdateRequired)
        }
        CommonSystemViewEvent.UnderMaintenance -> {
            mainActivity.vm.viewEvent(GlobalViewEvent.UnderMaintenance)
        }
    }
}