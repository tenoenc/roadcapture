package com.tenacy.roadcapture.ui

import android.location.Location
import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.google.android.gms.maps.model.Polyline
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.tenacy.roadcapture.R
import com.tenacy.roadcapture.data.ReportReason
import com.tenacy.roadcapture.data.db.*
import com.tenacy.roadcapture.data.firebase.dto.FirebaseLocation
import com.tenacy.roadcapture.data.firebase.dto.FirebaseMemory
import com.tenacy.roadcapture.data.firebase.exception.SystemConfigException
import com.tenacy.roadcapture.data.pref.AppPrefs
import com.tenacy.roadcapture.data.pref.UserPref
import com.tenacy.roadcapture.ui.dto.Album
import com.tenacy.roadcapture.ui.dto.Marker
import com.tenacy.roadcapture.util.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.tasks.await
import java.time.Instant
import javax.inject.Inject
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@HiltViewModel
class AlbumViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val resourceProvider: ResourceProvider,
    private val memoryCacheDao: MemoryCacheDao,
    private val locationCacheDao: LocationCacheDao,
    private val cacheDao: CacheDao,
) : BaseViewModel() {

    private var scrapJob: Job? = null
    private var isScrapProcessing = false

    private val routePolylines = mutableListOf<Polyline>()
    private val clusterItems = mutableMapOf<String, ClusterMarkerItem>()

    private val _locations = MutableStateFlow<List<FirebaseLocation>>(emptyList())
    private val _memories = MutableStateFlow<List<FirebaseMemory>?>(null)

    val memoryLoaded = _memories.map { it != null }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), false)

    val markers = combine(_locations, _memories) { locations, memories ->
        val memoryByLocationId = memories?.associateBy { it.locationRefId } ?: emptyMap()

        locations.map { location ->
            val memory = memoryByLocationId[location.id]
            if (memory != null) {
                Marker.from(memory, location)
            } else {
                Marker.of(location)
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), null)

    val routePoints = _locations.map { locations ->
        locations.sortedBy { it.createdAt }
            .map { getCustomLocationFrom(it.latitude, it.longitude) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), null)

    private val _album = MutableStateFlow<Album?>(null)

    val title = _album.mapNotNull { it?.title }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), "")

    private val _scrapCount = MutableStateFlow(0)
    val scrapCountText = combine(_scrapCount, resourceProvider.configurationContextFlow) { scrapCount, context ->
        scrapCount.toLocalizedString(context).takeUnless { it == "0" } ?: context.getString(R.string.none)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), "")

    private val _scraped = MutableStateFlow(false)
    val scraped = _scraped.asStateFlow()

    val profileDisplayName = _album.mapNotNull { it?.user?.displayName }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), "")

    val profileUrl = _album.mapNotNull { it?.user?.photoUrl }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), "")

    private val _albumId = MutableStateFlow(AlbumFragmentArgs.fromSavedStateHandle(savedStateHandle).albumId ?: "")
    private val albumId: StateFlow<String> = _albumId.asStateFlow()
    private val _albumUserId = MutableStateFlow(AlbumFragmentArgs.fromSavedStateHandle(savedStateHandle).userId ?: "")
    private val albumUserId: StateFlow<String> = _albumUserId.asStateFlow()

    init {
        checkDeepLinkAndLoad()
    }

    private fun checkDeepLinkAndLoad() {
        viewModelScope.launch {
            if (AppPrefs.pendingDeepLinkShareId != null) {
                handleShareIdDeepLink(AppPrefs.pendingDeepLinkShareId!!)
                AppPrefs.clear()
            } else {
                // 일반 진입이면 바로 데이터 로드
                fetchData()
                countView()
            }
        }
    }

    private fun fetchData() {
        viewModelScope.launch(Dispatchers.IO) {
            val currentAlbumId = albumId.value
            val currentUserId = albumUserId.value

            flow {
                val albumUserRef = db.collection(FirebaseConstants.COLLECTION_USERS).document(currentUserId)
                if(!albumUserRef.get().await().exists()) {
                    throw FirebaseFirestoreException(resourceProvider.getString(R.string.user_not_exist), FirebaseFirestoreException.Code.NOT_FOUND)
                }

                val userRef = db.collection(FirebaseConstants.COLLECTION_USERS).document(UserPref.id)
                val albumRef = db.collection(FirebaseConstants.COLLECTION_ALBUMS).document(currentAlbumId)
                val firebaseAlbum = (albumRef.get().await()?.takeIf { it.exists() }?.toAlbum()
                    ?: throw FirebaseFirestoreException(resourceProvider.getString(R.string.album_not_exist), FirebaseFirestoreException.Code.NOT_FOUND))

                if(currentUserId != UserPref.id && !firebaseAlbum.isPublic) {
                    throw FirebaseFirestoreException(resourceProvider.getString(R.string.album_switched_private), FirebaseFirestoreException.Code.PERMISSION_DENIED)
                }

                val scrapRef = db.collection(FirebaseConstants.COLLECTION_SCRAPS)
                val isScraped = scrapRef
                    .whereEqualTo("userRef", userRef)
                    .whereEqualTo("albumRef", albumRef)
                    .limit(1)
                    .get().await()
                    .documents.firstOrNull()?.exists() ?: false

                val album = Album.from(firebaseAlbum, isScraped)
                _album.emit(album)
                _scraped.emit(album.isScraped)
                _scrapCount.emit(album.scrapCount)

                val cached = cacheDao.cachedBy(CacheType.Album, album.id)
                val (memories, locations) = if (cached) {
                    val memories = memoryCacheDao.selectByAlbumId(album.id).map(MemoryCacheEntity::toFirebaseMemory)
                    val locations =
                        locationCacheDao.selectByAlbumId(album.id).map(LocationCacheEntity::toFirebaseLocation)
                    memories to locations
                } else {
                    val memories = db.collection(FirebaseConstants.COLLECTION_MEMORIES)
                        .whereEqualTo("albumRef", albumRef)
                        .orderBy("createdAt", Query.Direction.ASCENDING).getAll { it.toMemory() }

                    // 추억 캐싱
                    val memoryCaches = memories.map(MemoryCacheEntity::of)
                    memoryCacheDao.insertAll(memoryCaches)

                    val locations = db.collection(FirebaseConstants.COLLECTION_LOCATIONS)
                        .whereEqualTo("albumRef", albumRef)
                        .orderBy("createdAt", Query.Direction.ASCENDING).getAll { it.toLocation() }

                    // 위치 정보 캐싱
                    val locationCaches = locations.map { LocationCacheEntity.from(it, album.id) }
                    locationCacheDao.insertAll(locationCaches)

                    // 캐싱 정보 저장
                    val cache = CacheEntity(type = CacheType.Album, targetId = album.id, createdAt = Instant.now().toEpochMilli())
                    cacheDao.insert(cache)

                    memories to locations
                }
                emit(memories to locations)
            }
                .catch { exception ->
                    Log.e("AlbumViewModel", "에러", exception)

                    (exception as? FirebaseFirestoreException)?.let { firestoreException ->
                        viewEvent(AlbumViewEvent.Forbidden(firestoreException.message ?: resourceProvider.getString(R.string.unexpected_error)))
                    }
                }
                .collect { (memories, locations) ->
                    locations.getOrNull(0)?.let {
                        val customLocation = getCustomLocationFrom(it.latitude, it.longitude)
                        viewEvent(AlbumViewEvent.SetCamera(customLocation, zoom = 30f))
                    }
                    _memories.emit(memories)
                    _locations.emit(locations)
                }
        }
    }

    private fun countView() {
        val currentAlbumId = albumId.value

        viewModelScope.launch(Dispatchers.IO) {
            flow {
                val albumRef = db.collection(FirebaseConstants.COLLECTION_ALBUMS).document(currentAlbumId)
                albumRef.update("viewCount", FieldValue.increment(1)).await()
                emit(Unit)
            }.catch { exception ->
                Log.e("AlbumViewModel", "에러", exception)
            }.collect {

            }
        }
    }

    private fun scrap() {
        if (isScrapProcessing) return

        val capturedScraped = _scraped.value
        val capturedScrapCount = _scrapCount.value

        scrapJob?.cancel()

        scrapJob = viewModelScope.launch(Dispatchers.IO) {
            val currentAlbumId = albumId.value

            flow {
                isScrapProcessing = true

                // [VALIDATE_SYSTEM_CONFIG]
                validateSystemConfig()

                val userId = UserPref.id

                // 참조 생성
                val albumRef = db.collection(FirebaseConstants.COLLECTION_ALBUMS).document(currentAlbumId)
                val userRef = db.collection(FirebaseConstants.COLLECTION_USERS).document(userId)

                // 트랜잭션 밖에서 먼저 scraps 문서 ID를 찾아두기
                val scrapToDelete = db.collection(FirebaseConstants.COLLECTION_SCRAPS )
                    .whereEqualTo("userRef", userRef)
                    .whereEqualTo("albumRef", albumRef)
                    .limit(1)
                    .get()
                    .await()
                    .documents
                    .firstOrNull()

                val isScraped = suspendCancellableCoroutine<Boolean> { continuation ->
                    db.runTransaction { transaction ->
                        val album = transaction.get(albumRef).toAlbum()
                        val albumUserRef = db.collection(FirebaseConstants.COLLECTION_USERS).document(album.userId)

                        if (scrapToDelete?.exists() == true) {
                            // 스크랩 취소
                            transaction.delete(scrapToDelete.reference)

                            transaction.update(albumRef, "scrapCount", FieldValue.increment(-1))
                            transaction.update(albumUserRef, "scrapCount", FieldValue.increment(-1))

                            false
                        } else {
                            // 스크랩하기
                            val newScrapRef = db.collection(FirebaseConstants.COLLECTION_SCRAPS).document()
                            transaction.set(newScrapRef, mapOf(
                                "albumRef" to albumRef,
                                "userRef" to userRef,
                                "albumTitle" to album.title,
                                "albumUserDisplayName" to album.userDisplayName,
                                "albumMemoryAddressTags" to album.memoryAddressTags,
                                "albumMemoryPlaceNames" to album.memoryPlaceNames,
                                "albumPublic" to album.isPublic,
                                "albumCreatedAt" to album.createdAt.toFirebaseTimestamp(),
                                "createdAt" to FieldValue.serverTimestamp(),
                            ))

                            transaction.update(albumRef, "scrapCount", FieldValue.increment(1))
                            transaction.update(albumUserRef, "scrapCount", FieldValue.increment(1))

                            true
                        }
                    }.addOnSuccessListener { result ->
                        continuation.resume(result)
                    }.addOnFailureListener { exception ->
                        continuation.resumeWithException(exception)
                    }
                }

                emit(isScraped)
            }.catch { exception ->
                _scraped.update {
                    _scrapCount.update { (capturedScrapCount + if(capturedScraped) -1 else 1).coerceAtLeast(0) }
                    !capturedScraped
                }
                isScrapProcessing = false
                Log.e("AlbumViewModel", "에러", exception)
                // [VALIDATE_SYSTEM_CONFIG]
                if(exception is SystemConfigException) {
                    handleSystemConfigException(exception)
                    return@catch
                }
            }
                .collectLatest { nextScraped ->
                    _scraped.update { currentScraped ->
                        _scrapCount.update { count ->
                            if(currentScraped != nextScraped) {
                                (count + if(nextScraped) 1 else -1).coerceAtLeast(0)
                            } else {
                                count
                            }
                        }
                        nextScraped
                    }

                    isScrapProcessing = false
                }
        }
    }

    fun report(albumId: String, reason: ReportReason) {
        viewModelScope.launch(Dispatchers.IO) {
            flow {
                // [VALIDATE_SYSTEM_CONFIG]
                validateSystemConfig()

                val userId = UserPref.id
                val userRef = db.collection(FirebaseConstants.COLLECTION_USERS).document(userId)
                val albumRef = db.collection(FirebaseConstants.COLLECTION_ALBUMS).document(albumId)

                val reportData = mapOf(
                    "userRef" to userRef,
                    "albumRef" to albumRef,
                    "reason" to reason.name,
                    "createdAt" to FieldValue.serverTimestamp(),
                )

                db.collection(FirebaseConstants.COLLECTION_REPORTS)
                    .document()
                    .set(reportData)
                    .await()

                emit(Unit)
            }
                .catch { exception ->
                    Log.e("AlbumViewModel", "에러", exception)
                    // [VALIDATE_SYSTEM_CONFIG]
                    if(exception is SystemConfigException) {
                        handleSystemConfigException(exception)
                        return@catch
                    }
                }
                .collect {
                    viewEvent(AlbumViewEvent.ReportComplete)
                }
        }
    }

    private fun handleShareIdDeepLink(shareId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // shareId로 앨범 정보 가져오기
                val snapshot = db.collection(FirebaseConstants.COLLECTION_ALBUMS)
                    .whereEqualTo("shareId", shareId)
                    .limit(1)
                    .get()
                    .await()

                if(snapshot.isEmpty) {
                    throw FirebaseFirestoreException(resourceProvider.getString(R.string.album_not_exist), FirebaseFirestoreException.Code.NOT_FOUND)
                }

                val albumDoc = snapshot.documents[0]
                val albumId = albumDoc.id
                val userId = albumDoc.getDocumentReference("userRef")?.id

                if (userId != null) {
                    // 기존 fetchData 로직이 자동으로 실행되도록 필드 업데이트
                    _albumId.value = albumId
                    _albumUserId.value = userId

                    // 데이터 다시 로드
                    fetchData()
                    countView()
                }
            } catch (exception: Exception) {
                Log.e("AlbumViewModel", "shareId 처리 오류", exception)
                viewEvent(AlbumViewEvent.Forbidden(exception.message ?: resourceProvider.getString(R.string.share_link_access_error)))
            }
        }
    }

    fun getMemories() = _memories.value ?: emptyList()

    fun getMemoriesIn(items: List<ClusterMarkerItem>): List<FirebaseMemory> {
        val currentMemories = _memories.value
        val associateBy = currentMemories?.associateBy { it.locationRefId } ?: emptyMap()
        return items.mapNotNull { associateBy[it.id] }
    }

    fun getMemoryIdBy(item: ClusterMarkerItem): String {
        val currentMemories = _memories.value
        val memoryIdsByLocationId = currentMemories?.associate { it.locationRefId to it.id } ?: emptyMap()
        return memoryIdsByLocationId[item.id]!!
    }

    fun getCoordinatesByLocationId(id: String): Location {
        val currentLocations = _locations.value
        val coordinatesByLocationId = currentLocations.associate {
            val customLocation = getCustomLocationFrom(it.latitude, it.longitude)
            it.id to customLocation
        }
        return coordinatesByLocationId[id]!!
    }

    fun clearRoutePolylines() {
        routePolylines.forEach { it.remove() }
        routePolylines.clear()
    }

    fun addRoutePolyline(polyline: Polyline) {
        routePolylines.add(polyline)
    }

    fun getMarkerIds() = clusterItems.keys.toSet()

    fun containsMarkerId(markerId: String) = markerId in clusterItems

    fun getClusterItem(markerId: String) = clusterItems[markerId]

    fun addClusterItem(markerId: String, clusterItem: ClusterMarkerItem) {
        clusterItems[markerId] = clusterItem
    }

    fun removeClusterItem(markerId: String) {
        clusterItems.remove(markerId)
    }

    fun onResetCameraPositionClick() {
        viewModelScope.launch(Dispatchers.Default) {
            viewEvent(AlbumViewEvent.ResetCameraPosition)
        }
    }

    fun onZoomInClick() {
        viewModelScope.launch(Dispatchers.Default) {
            viewEvent(AlbumViewEvent.ZoomIn)
        }
    }

    fun onZoomOutClick() {
        viewModelScope.launch(Dispatchers.Default) {
            viewEvent(AlbumViewEvent.ZoomOut)
        }
    }

    fun onInfoClick() {
        viewModelScope.launch(Dispatchers.Default) {
            val currentAlbum = _album.value ?: return@launch
            val totalMemoryCount = _memories.value?.size ?: 0
            viewEvent(AlbumViewEvent.ShowInfo(currentAlbum, totalMemoryCount))
        }
    }

    fun onScrapClick() {
        _scraped.update {
            _scrapCount.update { count ->
                count + if(!it) 1 else -1
            }
            !it
        }
        scrap()
    }

    fun onShareClick() {
        viewModelScope.launch(Dispatchers.Default) {
            val currentAlbum = _album.value ?: return@launch
            val shareLink = currentAlbum.branchLink
            viewEvent(AlbumViewEvent.Share(shareLink))
        }
    }

    fun onProfileClick() {
        viewModelScope.launch(Dispatchers.Default) {
            _album.value?.let {
                val userId = it.user.id
                viewEvent(AlbumViewEvent.NavigateToStudio(userId))
            }
        }
    }

    fun onReportClick() {
        viewModelScope.launch(Dispatchers.Default) {
            val currentAlbumId = albumId.value
            viewEvent(AlbumViewEvent.ShowReport(currentAlbumId))
        }
    }
}