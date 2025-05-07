package com.tenacy.roadcapture.ui

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Polyline
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.Query
import com.tenacy.roadcapture.data.firebase.dto.FirebaseLocation
import com.tenacy.roadcapture.data.firebase.dto.FirebaseMemory
import com.tenacy.roadcapture.ui.dto.Marker
import com.tenacy.roadcapture.util.db
import com.tenacy.roadcapture.util.toLocation
import com.tenacy.roadcapture.util.toMemory
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

@HiltViewModel
class AlbumViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
) : BaseViewModel() {

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

    init {
        fetchData()
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
        val album = AlbumFragmentArgs.fromSavedStateHandle(savedStateHandle).value
        viewModelScope.launch(Dispatchers.IO) {
            flow {
                val albumRef = db.collection("albums").document(album.id)
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
                    Log.e("AlbumViewModel", "에러 발생", exception)
                }
                .collect { (memories, locations) ->
                    locations.getOrNull(0)?.let { viewEvent(AlbumViewEvent.SetCamera(LatLng(it.latitude, it.longitude), zoom = 30f)) }

                    _memories.emit(memories)
                    _locations.emit(locations)
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
            viewEvent(TripViewEvent.ResetCameraPosition)
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
}