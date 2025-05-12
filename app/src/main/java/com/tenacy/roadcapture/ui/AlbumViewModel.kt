package com.tenacy.roadcapture.ui

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Polyline
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.Query
import com.tenacy.roadcapture.data.firebase.dto.FirebaseLocation
import com.tenacy.roadcapture.data.firebase.dto.FirebaseMemory
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

    val loaded = MutableStateFlow(false)

    private val routePolylines = mutableListOf<Polyline>()
    private val clusterItems = mutableMapOf<String, ClusterMarkerItem>()

    private val _locations = MutableStateFlow<List<FirebaseLocation>>(emptyList())
    private val _memories = MutableStateFlow<List<FirebaseMemory>>(emptyList())

    val markers = combine(_locations, _memories) { locations, memories ->
        val memoryByLocationId = memories.associateBy { it.locationRefId }

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

    val profileUrl = _album.mapNotNull { it?.user?.photoUrl }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), "")

    private val albumId = AlbumFragmentArgs.fromSavedStateHandle(savedStateHandle).albumId
    private val userId = AlbumFragmentArgs.fromSavedStateHandle(savedStateHandle).userId

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
                val userRef = db.collection("users").document(user!!.uid)
                val albumRef = db.collection("albums").document(albumId)
                val firebaseAlbum = if(userId == user!!.uid) {
                    albumRef.get().await().toAlbum()
                } else {
                    db.collection("albums")
                        .whereEqualTo(FieldPath.documentId(), albumId)
                        .whereEqualTo("isPublic", true).get().await().documents.firstOrNull()?.toAlbum() ?: throw RuntimeException("403")
                }
                val userScrapRef = userRef.collection("scraps").document(albumId)
                val userScrap = userScrapRef.get().await()

                val album = Album.from(firebaseAlbum, userScrap.exists())
                _album.emit(album)
                _scraped.emit(album.isScraped)
                _scrapCount.emit(album.scrapCount)

                val memoriesQuerySnapshot = db.collection("memories")
                    .whereEqualTo("albumRef", albumRef)
                    .orderBy("createdAt", Query.Direction.ASCENDING)
                    .get()
                    .await()
                val memoryDocuments = memoriesQuerySnapshot.documents
                val memories = memoryDocuments.map(DocumentSnapshot::toMemory)

                val locationsQuerySnapshot = db.collection("locations")
                    .whereEqualTo("albumRef", albumRef)
                    .orderBy("createdAt", Query.Direction.ASCENDING)
                    .get()
                    .await()
                val locationDocuments = locationsQuerySnapshot.documents
                val locations = locationDocuments.map(DocumentSnapshot::toLocation)

                emit(memories to locations)
            }
                .catch { exception ->
                    Log.e("AlbumViewModel", "에러", exception)
                    
                    if(exception.message == "403") {
                        viewEvent(AlbumViewEvent.Forbidden("접근할 수 없어요"))
                    }
                }
                .collect { (memories, locations) ->
                    locations.getOrNull(0)?.let { viewEvent(AlbumViewEvent.SetCamera(LatLng(it.latitude, it.longitude), zoom = 30f)) }

                    _memories.emit(memories)
                    _locations.emit(locations)

                    loaded.emit(true)
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

                val userId = user!!.uid

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

                        if (scrapToDelete?.exists() == true) {
                            // 스크랩 취소
                            transaction.delete(scrapToDelete.reference)

                            transaction.update(albumRef, "scrapCount", FieldValue.increment(-1))
                            transaction.update(userRef, "scrapCount", FieldValue.increment(-1))

                            false
                        } else {
                            // 스크랩하기
                            val newScrapRef = db.collection("scraps").document()
                            transaction.set(newScrapRef, mapOf(
                                "albumRef" to albumRef,
                                "userRef" to userRef,
                                "createdAt" to FieldValue.serverTimestamp(),
                            ))

                            transaction.update(albumRef, "scrapCount", FieldValue.increment(1))
                            transaction.update(userRef, "scrapCount", FieldValue.increment(1))

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

    fun getMemories() = _memories.value

    fun getMemoriesIn(items: List<ClusterMarkerItem>): List<FirebaseMemory> {
        val currentMemories = _memories.value
        val associateBy = currentMemories.associateBy { it.locationRefId }
        return items.mapNotNull { associateBy[it.id] }
    }

    fun getMemoryIdBy(item: ClusterMarkerItem): String {
        val currentMemories = _memories.value
        val memoryIdsByLocationId = currentMemories.associate { it.locationRefId to it.id }
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
                val totalMemoryCount = _memories.value.size
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
}