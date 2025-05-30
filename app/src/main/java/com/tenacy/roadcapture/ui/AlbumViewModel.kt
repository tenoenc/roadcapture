package com.tenacy.roadcapture.ui

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Polyline
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.tenacy.roadcapture.data.ReportReason
import com.tenacy.roadcapture.data.firebase.dto.FirebaseLocation
import com.tenacy.roadcapture.data.firebase.dto.FirebaseMemory
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
import javax.inject.Inject
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@HiltViewModel
class AlbumViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
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
            .map { LatLng(it.latitude, it.longitude) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), null)

    private val _album = MutableStateFlow<Album?>(null)

    val title = _album.mapNotNull { it?.title }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), "")

    private val _scrapCount = MutableStateFlow(0)
    val scrapCountText = _scrapCount.map {
        val (value, unit) = it.toLong().toReadableUnit()
        "${value.toFormattedDecimalText()}${unit}"
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), "")

    private val _scraped = MutableStateFlow(false)
    val scraped = _scraped.asStateFlow()

    val profileDisplayName = _album.mapNotNull { it?.user?.displayName }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), "")

    val profileUrl = _album.mapNotNull { it?.user?.photoUrl }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), "")

    private val albumId = AlbumFragmentArgs.fromSavedStateHandle(savedStateHandle).albumId
    private val albumUserId = AlbumFragmentArgs.fromSavedStateHandle(savedStateHandle).userId

    init {
        fetchData()
        countView()
    }

    /*private fun fetchData() {
        viewModelScope.launch(Dispatchers.IO) {
            val locations = locationDao.selectAll()
            val memories = memoryDao.selectAll()
            // 마지막 저장 위치 설정
            if (locations.isNotEmpty()) {
                val last = locations.maxByOrNull { it.createdAt }
                last?.let {
                    _lastLocation.emit(LatLng(it.latitude, it.longitude))
                }
            }
            _memories.emit(memories)
            _locations.emit(locations)
        }
    }*/

    private fun fetchData() {
        viewModelScope.launch(Dispatchers.IO) {
            flow {
                val albumUserRef = db.collection("users").document(albumUserId)
                if(!albumUserRef.get().await().exists()) {
                    throw FirebaseFirestoreException("존재하지 않는 사용자예요", FirebaseFirestoreException.Code.NOT_FOUND)
                }

                val userRef = db.collection("users").document(UserPref.id)
                val albumRef = db.collection("albums").document(albumId)
                val firebaseAlbum = (albumRef.get().await()?.takeIf { it.exists() }?.toAlbum()
                    ?: throw FirebaseFirestoreException("존재하지 않는 앨범이에요", FirebaseFirestoreException.Code.NOT_FOUND))

                if(albumUserId != UserPref.id && !firebaseAlbum.isPublic) {
                    throw FirebaseFirestoreException("접근할 수 없어요",FirebaseFirestoreException.Code.PERMISSION_DENIED)
                }

                val scrapRef = db.collection("scraps")
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

                val memories = db.collection("memories")
                    .whereEqualTo("albumRef", albumRef)
                    .orderBy("createdAt", Query.Direction.ASCENDING).getAll { it.toMemory() }

                val locations = db.collection("locations")
                    .whereEqualTo("albumRef", albumRef)
                    .orderBy("createdAt", Query.Direction.ASCENDING).getAll { it.toLocation() }

                emit(memories to locations)
            }
                .catch { exception ->
                    Log.e("AlbumViewModel", "에러", exception)

                    (exception as? FirebaseFirestoreException)?.let { firestoreException ->
                        viewEvent(AlbumViewEvent.Forbidden(firestoreException.message ?: "예기치 않은 오류가 발생했어요"))
                    }
                }
                .collect { (memories, locations) ->
                    locations.getOrNull(0)?.let { viewEvent(AlbumViewEvent.SetCamera(LatLng(it.latitude, it.longitude), zoom = 30f)) }

                    _memories.emit(memories)
                    _locations.emit(locations)
                }
        }
    }

    private fun countView() {
        viewModelScope.launch(Dispatchers.IO) {
            flow {
                val albumRef = db.collection("albums").document(albumId)
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

        scrapJob?.cancel()

        scrapJob = viewModelScope.launch(Dispatchers.IO) {
            flow {
                isScrapProcessing = true

                val userId = UserPref.id

                // 참조 생성
                val albumRef = db.collection("albums").document(albumId)
                val userRef = db.collection("users").document(userId)

                // 트랜잭션 밖에서 먼저 scraps 문서 ID를 찾아두기
                val scrapToDelete = db.collection("scraps")
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
                        val albumUserRef = db.collection("users").document(album.userId)

                        if (scrapToDelete?.exists() == true) {
                            // 스크랩 취소
                            transaction.delete(scrapToDelete.reference)

                            transaction.update(albumRef, "scrapCount", FieldValue.increment(-1))
                            transaction.update(albumUserRef, "scrapCount", FieldValue.increment(-1))

                            false
                        } else {
                            // 스크랩하기
                            val newScrapRef = db.collection("scraps").document()
                            transaction.set(newScrapRef, mapOf(
                                "albumRef" to albumRef,
                                "userRef" to userRef,
                                "albumTitle" to album.title,
                                "albumUserDisplayName" to album.userDisplayName,
                                "albumMemoryAddressTags" to album.memoryAddressTags,
                                "albumMemoryPlaceNames" to album.memoryPlaceNames,
                                "albumPublic" to album.isPublic,
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
                Log.e("AlbumViewModel", "에러", exception)
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
                val userId = UserPref.id
                val userRef = db.collection("users").document(userId)
                val albumRef = db.collection("albums").document(albumId)

                val reportData = mapOf(
                    "userRef" to userRef,
                    "albumRef" to albumRef,
                    "reason" to reason.name,
                    "createdAt" to FieldValue.serverTimestamp(),
                )

                db.collection("reports")
                    .document()
                    .set(reportData)
                    .await()

                emit(Unit)
            }
                .catch { exception ->
                    Log.e("AlbumViewModel", "에러", exception)
                }
                .collect {
                    viewEvent(AlbumViewEvent.ReportComplete)
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

    fun getCoordinatesByLocationId(id: String): LatLng {
        val currentLocations = _locations.value
        val coordinatesByLocationId = currentLocations.associate { it.id to LatLng(it.latitude, it.longitude) }
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
            _album.value?.let {
                val totalMemoryCount = _memories.value?.size ?: 0
                viewEvent(AlbumViewEvent.ShowInfo(it, totalMemoryCount))
            }
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
            viewEvent(AlbumViewEvent.Share)
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
            viewEvent(AlbumViewEvent.ShowReport(albumId))
        }
    }
}