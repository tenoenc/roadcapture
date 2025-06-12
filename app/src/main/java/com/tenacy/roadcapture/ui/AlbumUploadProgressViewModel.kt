package com.tenacy.roadcapture.ui

import android.content.Context
import android.os.Parcelable
import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.tenacy.roadcapture.data.db.LocationDao
import com.tenacy.roadcapture.data.db.MemoryDao
import com.tenacy.roadcapture.data.pref.TravelPref
import com.tenacy.roadcapture.data.pref.UserPref
import com.tenacy.roadcapture.manager.TravelingStateManager
import com.tenacy.roadcapture.util.*
import com.tenacy.roadcapture.worker.PhotoCacheWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.parcelize.Parcelize
import java.time.LocalDateTime
import java.util.*
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject

@HiltViewModel
class AlbumUploadProgressViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    @ApplicationContext private val context: Context,
    private val memoryDao: MemoryDao,
    private val locationDao: LocationDao,
    private val travelingStateManager: TravelingStateManager,
) : BaseViewModel() {

    private val title = AlbumUploadProgressFragmentArgs.fromSavedStateHandle(savedStateHandle).title
    private val isPublic = AlbumUploadProgressFragmentArgs.fromSavedStateHandle(savedStateHandle).isPublic

    private val _saveState = MutableSharedFlow<AlbumSaveState>()
    val saveState = _saveState.asSharedFlow()

    init {
        saveAlbum()
    }

    private fun saveAlbum() {
        viewModelScope.launch(Dispatchers.IO) {
            _saveState.emit(AlbumSaveState.Loading)
            channelFlow {
                // 초기 로딩 상태
                sendWithDelay(AlbumSaveState.Loading)

                // 1. 데이터 가져오기
                sendWithDelay(AlbumSaveState.FetchingData)
                val albumTitle = title
                val memories = memoryDao.selectAll()
                val locations = locationDao.selectAll()
                val startTime = TravelPref.createdAt.toFirebaseTimestamp()
                val endTime = LocalDateTime.now().toFirebaseTimestamp()
                val userId = UserPref.id
                val userRef = db.collection(FirebaseConstants.COLLECTION_USERS).document(userId)
                val user = userRef.get().await().toUser()

                val albumRef = db.collection(FirebaseConstants.COLLECTION_ALBUMS).document()
                val albumId = albumRef.id

                // 2. 지역 태그 생성
                sendWithDelay(AlbumSaveState.CreatingTags)
                val regionTags = memories.mapIndexedNotNull { index, memoryWithLocation ->
                    val top3 = memoryWithLocation.memory.addressTags.take(3)
                    if(index > 0 && top3 == memories[index-1].memory.addressTags.take(3)) {
                        return@mapIndexedNotNull null
                    }
                    mapOf(
                        "country" to top3[0],
                        "depth1" to if(top3.size >= 2) top3[1] else "undefined",
                        "depth2" to if(top3.size >= 3) top3[2] else "undefined",
                    )
                }
                    .distinct()

                // 3. 이미지 업로드 (병렬 처리)
                // 업로드 시작 알림
                sendWithDelay(AlbumSaveState.UploadingImages(0, memories.size))

                // 진행 상황 추적을 위한 카운터
                val uploadCounter = AtomicInteger(0)
                val totalUploads = memories.size

                val memoryUploadTasks = memories.map { memoryWithLocation ->
                    val memory = memoryWithLocation.memory
                    val storagePath = "images/albums/$userId/$albumId/${memory.id}-${UUID.randomUUID()}.jpg"
                    async {
                        try {
                            val downloadUrl = context.uploadImageToStorage(
                                uri = memory.photoUri,
                                storagePath = storagePath,
                            )

                            // 업로드 완료 후 카운터 증가 및 상태 업데이트
                            val completed = uploadCounter.incrementAndGet()

                            // channelFlow에서는 다른 코루틴에서도 send 가능 (withContext 필요 없음)
                            sendWithDelay(AlbumSaveState.UploadingImages(completed, totalUploads))

                            memory.id to (storagePath to downloadUrl)
                        } catch (e: Exception) {
                            // 업로드 실패 처리
                            Log.e("AlbumSave", "이미지 업로드 실패: ${e.message}", e)
                            throw e
                        }
                    }
                }

                val uploadResults = memoryUploadTasks.awaitAll()
                val photoUrlMap = uploadResults.toMap()
                
                // 이미지 캐싱
                val uploadedPhotoUrls = photoUrlMap.values.map { (_, downloadUrl) -> downloadUrl }
                PhotoCacheWorker.enqueueOneTimeWork(context, uploadedPhotoUrls)
                Log.d("AlbumSave", "이미지 캐싱 작업 예약 완료: ${uploadedPhotoUrls.size}개")
                
                // 3 - 4. 오픈 그래프 이미지 업로드
                sendWithDelay(AlbumSaveState.UploadingThumbnail)
                val ogImageUri = memories.firstOrNull()?.memory?.photoUri
                val resizedThumbnailUri = ogImageUri!!.resizeCenterCrop(context)
                val storagePath = "og-images-$userId-$albumId.jpg"
                val ogImageUrl = context.uploadImageToStorage(
                    uri = resizedThumbnailUri,
                    storagePath = storagePath,
                )

                // 4. 데이터 생성 및 저장 (배치)
                sendWithDelay(AlbumSaveState.SavingToFirestore)

                val thumbnailUrlId = memories.find { it.memory.id == TravelPref.thumbnailMemoryId }?.memory?.id ?: memories.firstOrNull()?.memory?.id
                val (_, thumbnailUrl) = photoUrlMap[thumbnailUrlId]!!
                val memoryAddressTags = hashSetOf<String>()
                val memoryPlaceNames = hashSetOf<String>()

                val locationsData = locations.map { location ->
                    hashMapOf(
                        "localId" to location.id.toString(),
                        "latitude" to location.coordinates.latitude,
                        "longitude" to location.coordinates.longitude,
                        "albumRef" to albumRef,
                        "createdAt" to location.createdAt.toFirebaseTimestamp(),
                    )
                }

                val locationRefByMemoryId = locations.associateBy { it.id }.mapValues { db.collection(FirebaseConstants.COLLECTION_LOCATIONS).document() }
                val memoriesData = memories.map { memoryWithLocation ->
                    val memory = memoryWithLocation.memory
                    val (storagePath, uploadedPhotoUrl) = photoUrlMap[memory.id]
                        ?: throw IllegalStateException("Missing uploaded URL for memory ${memory.id}")

                    memoryAddressTags.addAll(memory.addressTags)
                    memory.placeName?.let(memoryPlaceNames::add)

                    mapOf(
                        "isThumbnail" to (memory.id == TravelPref.thumbnailMemoryId),
                        "content" to memory.content,
                        "photoUrl" to uploadedPhotoUrl,
                        "photoName" to storagePath,
                        "placeName" to memory.placeName,
                        "addressTags" to memory.addressTags,
                        "formattedAddress" to memory.formattedAddress,
                        "isPublic" to isPublic,
                        "albumRef" to albumRef,
                        "userRef" to userRef,
                        "locationRef" to locationRefByMemoryId[memoryWithLocation.location.id]!!,
                        "createdAt" to memory.createdAt.toFirebaseTimestamp()
                    )
                }

                val albumData = hashMapOf(
                    "title" to albumTitle,
                    "createdAt" to startTime,
                    "endedAt" to endTime,
                    "thumbnailUrl" to thumbnailUrl,
                    "ogImageUrl" to ogImageUrl,
                    "viewCount" to 0,
                    "scrapCount" to 0,
                    "regionTags" to regionTags,
                    "isPublic" to isPublic,
                    "userRef" to userRef,
                    "userDisplayName" to user.displayName,
                    "userPhotoUrl" to user.photoUrl,
                    "memoryAddressTags" to memoryAddressTags.toList(),
                    "memoryPlaceNames" to memoryPlaceNames.toList(),
                )

                // 모든 배치 작업 목록 생성
                val allOperations = mutableListOf<BatchOperation>()

                // 앨범 작업 추가 (가장 중요하므로 첫 번째로 추가)
                allOperations.add(SetDocumentOperation(albumRef, albumData))

                // 메모리 작업 추가
                memoriesData.forEach { memoryData ->
                    val memoryRef = db.collection(FirebaseConstants.COLLECTION_MEMORIES).document()
                    allOperations.add(SetDocumentOperation(memoryRef, memoryData))
                }

                // 위치 작업 추가
                locationsData.forEach { locationData ->
                    val locationId = (locationData["localId"] as String).toLong()
                    allOperations.add(SetDocumentOperation(locationRefByMemoryId[locationId]!!, locationData.apply { remove("localId") }))
                }

                // 배치 실행
                db.executeInBatches(allOperations)

                // 5. 로컬 데이터 초기화
                sendWithDelay(AlbumSaveState.ClearingLocalData)
                travelingStateManager.stopTraveling()
                TravelPref.clear()
                memoryDao.clear()
                locationDao.clear()
                context.clearCacheDirectory()

                // 6. 완료
                sendWithDelay(AlbumSaveState.Completed)
            }
                .catch { exception ->
                    Log.e("AlbumSave", "에러", exception)
                    emit(AlbumSaveState.Error(exception.message ?: "알 수 없는 오류 발생"))
                }
                .collect { state ->
                    // 상태를 사용하여 UI 업데이트
                    _saveState.emit(state)

                    // 로그 출력 (디버깅용)
                    Log.d("AlbumSave", "현재 상태: $state")
                }
        }
    }
}

@Parcelize
sealed class AlbumSaveState : Parcelable {
    data object Loading : AlbumSaveState()
    data object FetchingData : AlbumSaveState()
    data object CreatingTags : AlbumSaveState()
    data class UploadingImages(val current: Int, val total: Int) : AlbumSaveState()
    data object UploadingThumbnail: AlbumSaveState()
    data object SavingToFirestore : AlbumSaveState()
    data object ClearingLocalData : AlbumSaveState()
    data object Completed : AlbumSaveState()
    data class Error(val message: String) : AlbumSaveState()
}