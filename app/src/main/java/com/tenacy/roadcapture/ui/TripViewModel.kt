package com.tenacy.roadcapture.ui

import android.content.Context
import android.location.Location
import androidx.lifecycle.viewModelScope
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Polyline
import com.tenacy.roadcapture.data.db.LocationDao
import com.tenacy.roadcapture.data.db.LocationEntity
import com.tenacy.roadcapture.data.db.MemoryDao
import com.tenacy.roadcapture.data.db.MemoryWithLocation
import com.tenacy.roadcapture.data.pref.Album
import com.tenacy.roadcapture.data.pref.SubscriptionPref
import com.tenacy.roadcapture.manager.SubscriptionManager
import com.tenacy.roadcapture.ui.dto.Marker
import com.tenacy.roadcapture.util.SubscriptionValues
import com.tenacy.roadcapture.util.clearCacheDirectory
import com.tenacy.roadcapture.util.getDurationFormattedText
import com.tenacy.roadcapture.util.toTimestamp
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import javax.inject.Inject

@HiltViewModel
class TripViewModel @Inject constructor(
    subscriptionManager: SubscriptionManager,
    @ApplicationContext private val context: Context,
    private val locationDao: LocationDao,
    private val memoryDao: MemoryDao,
) : BaseViewModel() {

    val isSubscriptionActive: StateFlow<Boolean> = subscriptionManager.isSubscriptionActive
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = SubscriptionPref.isSubscriptionActive
        )

    var initialGuideShown = false

    private val routePolylines = mutableListOf<Polyline>()
    private val clusterItems = mutableMapOf<String, ClusterMarkerItem>()

    private val _locations = MutableStateFlow<List<LocationEntity>>(emptyList())
    private val _memories = MutableStateFlow<List<MemoryWithLocation>>(emptyList())

    private val _durationText = MutableStateFlow<String?>(null)
    val durationText = _durationText.asStateFlow()

    val memoryCountText = _memories.map { memories ->
        val maxMemorySize = SubscriptionValues.memoryMaxSize
        "추억 ${memories.size} / $maxMemorySize"
    }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), null)

    val exceedLimitedMemorySize = _memories.map {
        it.size >= SubscriptionValues.memoryMaxSize
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), false)

    val markers = combine(_locations, _memories) { locations, memories ->
        val memoryByLocationId = memories.associateBy { it.location.id }

        locations.map { location ->
            val memory = memoryByLocationId[location.id]
            if (memory != null) {
                Marker.of(memory)
            } else {
                Marker.of(location)
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), null)

    val routePoints = _locations.map { locations ->
        locations.sortedBy { it.createdAt }
            .map { LatLng(it.latitude, it.longitude) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), null)

    private var _lastLocation: LatLng? = null

    fun fetchData() {
        viewModelScope.launch(Dispatchers.IO) {
            val locations = locationDao.selectAll()
            val memories = memoryDao.selectAll()
            // 마지막 저장 위치 설정
            if (locations.isNotEmpty()) {
                val last = locations.maxByOrNull { it.createdAt }
                last?.let {
                    val coordinates = LatLng(it.latitude, it.longitude)
                    _lastLocation = coordinates
                }
            }
            _memories.emit(memories)
            _locations.emit(locations)
        }
    }

    fun getMemories() = _memories.value

    fun getMemoriesIn(items: List<ClusterMarkerItem>): List<MemoryWithLocation> {
        val currentMemories = _memories.value
        val associateBy = currentMemories.associateBy { it.location.id.toString() }
        return items.mapNotNull { associateBy[it.id] }
    }

    fun getMemoryIdBy(item: ClusterMarkerItem): String {
        val currentMemories = _memories.value
        val memoryIdsByLocationId = currentMemories.associate { it.location.id.toString() to it.memory.id }
        return memoryIdsByLocationId[item.id]!!.toString()
    }

    fun startTraveling() {
        if(Album.createdAt == 0L) {
            Album.createdAt = LocalDateTime.now().toTimestamp()
        }
        fetchData()
        updateDurationText()
        viewModelScope.launch(Dispatchers.Default) {
            viewEvent(TripViewEvent.SetCamera(zoom = 30f))
        }
    }

    fun updateDurationText() {
        _durationText.update { getDurationFormattedText(Album.createdAt, LocalDateTime.now().toTimestamp()) }
    }

    fun stopTraveling() {
        viewModelScope.launch(Dispatchers.IO) {
            Album.clear()
            memoryDao.clear()
            locationDao.clear()
            context.clearCacheDirectory()

            viewEvent(TripViewEvent.Back)
        }
    }

    fun saveLocation(latLng: LatLng, insertable: Boolean = true) {
        viewModelScope.launch(Dispatchers.IO) {
            if (shouldSaveLocation(latLng)) {
                if(insertable) {
                    val locationEntity = LocationEntity(
                        latitude = latLng.latitude,
                        longitude = latLng.longitude,
                        createdAt = LocalDateTime.now()
                    )

                    val locationId = locationDao.insert(locationEntity)

                    _locations.update {
                        it.toMutableList().apply { add(locationEntity.copy(id = locationId)) }
                    }
                }
                _lastLocation = latLng
            }
        }
    }

    private fun shouldSaveLocation(currentLatLng: LatLng): Boolean {
        val lastSavedLocation = _lastLocation ?: return true

        val lastLat = lastSavedLocation.latitude
        val lastLng = lastSavedLocation.longitude
        val results = FloatArray(1)

        Location.distanceBetween(
            lastLat, lastLng,
            currentLatLng.latitude, currentLatLng.longitude,
            results
        )

        return results[0] >= MIN_DISTANCE_TO_SAVE
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

    fun onResetCameraClick() {
        viewModelScope.launch(Dispatchers.Default) {
            viewEvent(TripViewEvent.SetCamera())
        }
    }

    fun onCaptureClick() {
        viewModelScope.launch(Dispatchers.Default) {
            viewEvent(TripViewEvent.Capture)
        }
    }

    fun onZoomInClick() {
        viewModelScope.launch(Dispatchers.Default) {
            viewEvent(TripViewEvent.ZoomIn)
        }
    }

    fun onZoomOutClick() {
        viewModelScope.launch(Dispatchers.Default) {
            viewEvent(TripViewEvent.ZoomOut)
        }
    }

    fun onDurationClick() {
        viewModelScope.launch(Dispatchers.Default) {
            viewEvent(TripViewEvent.ShowGuide)
        }
    }

    fun onMemoryCountClick() {
        viewModelScope.launch(Dispatchers.Default) {
            viewEvent(TripViewEvent.ShowSubscription)
        }
    }

    fun onCheckClick() {
        viewModelScope.launch(Dispatchers.Default) {
            viewEvent(TripViewEvent.ShowAfter)
        }
    }

    fun onDeleteClick() {
        viewModelScope.launch(Dispatchers.Default) {
            viewEvent(TripViewEvent.ShowStopBefore)
        }
    }

    companion object {
        const val MIN_DISTANCE_TO_SAVE = 30f
    }
}