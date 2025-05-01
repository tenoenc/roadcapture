package com.tenacy.roadcapture.ui

import android.content.Context
import android.net.Uri
import android.os.Parcelable
import android.util.Log
import androidx.lifecycle.viewModelScope
import com.google.firebase.Timestamp
import com.tenacy.roadcapture.data.db.LocationDao
import com.tenacy.roadcapture.data.db.MemoryDao
import com.tenacy.roadcapture.data.pref.Album
import com.tenacy.roadcapture.di.InputModule
import com.tenacy.roadcapture.util.*
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.tasks.await
import kotlinx.parcelize.Parcelize
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.*
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject

@HiltViewModel
class NewAlbumViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val memoryDao: MemoryDao,
    private val locationDao: LocationDao,
) : BaseViewModel() {

    val albumTitle = MutableStateFlow("")

    val albumTitleLength = albumTitle.map { it.length }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000L),
        initialValue = 0,
    )

    private val _albumTitleFocus = MutableStateFlow(false)

    private val _albumTitleInputAttemptOverflow = MutableStateFlow(false)

    val albumTitleState = combine(
        albumTitle,
        _albumTitleFocus,
        _albumTitleInputAttemptOverflow,
    ) { _, hasFocus, overflow ->
        when {
            !hasFocus -> EditTextState.Normal
            overflow -> EditTextState.Error
            else -> EditTextState.Focused
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000L),
        initialValue = EditTextState.Normal,
    )

    private val _saveState = MutableStateFlow<AlbumSaveState>(AlbumSaveState.Loading)
    val saveState: StateFlow<AlbumSaveState> = _saveState

    fun setAlbumTitleFocus(hasFocus: Boolean) {
        _albumTitleFocus.update { hasFocus }
    }

    fun onAlbumTitleInputAttempt(currentLength: Int) {
        _albumTitleInputAttemptOverflow.update { currentLength >= InputModule.MAX_LENGTH_ALBUM_TITLE }
    }

    fun onCompleteClick() {
        viewModelScope.launch(Dispatchers.Default) {
            viewEvent(NewAlbumViewEvent.ShowCompleteBefore)
        }
    }

    fun saveAlbum(isPublic: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            channelFlow {
                // 초기 로딩 상태
                send(AlbumSaveState.Loading)

                // 1. 데이터 가져오기
                send(AlbumSaveState.FetchingData)
                val albumTitle = albumTitle.value
                val memories = memoryDao.selectAll()
                val locations = locationDao.selectAll()
                val startTime = Album.createdAt.toLocalDateTime()
                val endTime = LocalDateTime.now()
                val userId = user!!.uid

                val albumRef = db.collection("albums").document()
                val albumId = albumRef.id

                // 2. 지역 태그 생성
                send(AlbumSaveState.CreatingTags)
                val regionTags = memories.mapIndexedNotNull { index, memoryWithLocation ->
                    val top3 = memoryWithLocation.memory.addressTags.take(3)
                    if(index > 0 && top3 == memories[index-1].memory.addressTags.take(3)) {
                        return@mapIndexedNotNull null
                    }
                    mapOf(
                        "country" to top3[0],
                        "depth1" to top3[1],
                        "depth2" to top3[2],
                    )
                }
                    .distinct()

                // 3. 위치 데이터 처리
                send(AlbumSaveState.ProcessingLocations)
                val locationsData = locations.map { location ->
                    mapOf(
                        "id" to location.id.toString(),
                        "latitude" to location.latitude,
                        "longitude" to location.longitude,
                        "createdAt" to Timestamp(location.createdAt.toEpochSecond(ZoneOffset.UTC), 0)
                    )
                }

                // 4. 이미지 업로드 (병렬 처리)
                // 업로드 시작 알림
                send(AlbumSaveState.UploadingImages(0, memories.size))

                // 진행 상황 추적을 위한 카운터
                val uploadCounter = AtomicInteger(0)
                val totalUploads = memories.size

                val memoryUploadTasks = memories.map { memoryWithLocation ->
                    val memory = memoryWithLocation.memory
                    val storagePath = "images/$userId/albums/$albumId/${memory.id}-${UUID.randomUUID()}.jpg"
                    async {
                        try {
                            val downloadUrl = uploadImageToStorage(
                                uri = memory.photoUri,
                                storagePath = storagePath,
                            )

                            // 업로드 완료 후 카운터 증가 및 상태 업데이트
                            val completed = uploadCounter.incrementAndGet()

                            // channelFlow에서는 다른 코루틴에서도 send 가능 (withContext 필요 없음)
                            send(AlbumSaveState.UploadingImages(completed, totalUploads))

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

                // 5. 메모리 데이터 처리
                send(AlbumSaveState.ProcessingMemories)
                val memoriesData = memories.map { memoryWithLocation ->
                    val memory = memoryWithLocation.memory
                    val (storagePath, uploadedPhotoUrl) = photoUrlMap[memory.id]
                        ?: throw IllegalStateException("Missing uploaded URL for memory ${memory.id}")

                    mapOf(
                        "id" to memory.id.toString(),
                        "content" to memory.content,
                        "photoUrl" to uploadedPhotoUrl,
                        "photoName" to storagePath,
                        "placeName" to memory.placeName,
                        "addressTags" to memory.addressTags,
                        "formattedAddress" to memory.formattedAddress,
                        "locationRefId" to memory.locationId.toString(),
                        "createdAt" to Timestamp(memory.createdAt.toEpochSecond(ZoneOffset.UTC), 0)
                    )
                }

                // 6. 앨범 데이터 생성 및 저장
                send(AlbumSaveState.SavingToFirestore)
                val thumbnailUrl = photoUrlMap[memories.firstOrNull()?.memory?.id] ?: ""

                val albumData = hashMapOf(
                    "title" to albumTitle,
                    "createdAt" to Timestamp(startTime.toEpochSecond(ZoneOffset.UTC), 0),
                    "endedAt" to Timestamp(endTime.toEpochSecond(ZoneOffset.UTC), 0),
                    "thumbnailUrl" to thumbnailUrl,
                    "viewCount" to 0,
                    "likeCount" to 0,
                    "regionTags" to regionTags,
                    "userId" to userId,
                    "isPublic" to isPublic,
                    "locations" to locationsData,
                    "memories" to memoriesData
                )

                albumRef.set(albumData).await()

                // 7. 로컬 데이터 초기화
                send(AlbumSaveState.ClearingLocalData)
                Album.clear()
                memoryDao.clear()
                locationDao.clear()
                context.clearCacheDirectory()

                // 8. 완료
                send(AlbumSaveState.Completed)
            }
                .catch { exception ->
                    Log.e("AlbumSave", "에러", exception)
                    emit(AlbumSaveState.Error(exception.message ?: "알 수 없는 오류 발생"))
                }
                .collect { state ->
                    // 상태를 사용하여 UI 업데이트
                    _saveState.value = state

                    // 로그 출력 (디버깅용)
                    Log.d("AlbumSave", "현재 상태: $state")
                }
        }
    }

    private suspend fun uploadImageToStorage(
        uri: Uri,
        storagePath: String
    ): String = withContext(Dispatchers.IO) {
        try {
            // 이미지를 위한 고유 경로 생성
            val storageRef = storage.reference.child(storagePath)

            // 콘텐츠 URI에서 입력 스트림 열기
            val inputStream = context.contentResolver.openInputStream(uri)
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
}

@Parcelize
sealed class AlbumSaveState : Parcelable {
    data object Loading : AlbumSaveState()
    data object FetchingData : AlbumSaveState()
    data object CreatingTags : AlbumSaveState()
    data object ProcessingLocations : AlbumSaveState()
    data class UploadingImages(val current: Int, val total: Int) : AlbumSaveState()
    data object ProcessingMemories : AlbumSaveState()
    data object SavingToFirestore : AlbumSaveState()
    data object ClearingLocalData : AlbumSaveState()
    data object Completed : AlbumSaveState()
    data class Error(val message: String) : AlbumSaveState()
}