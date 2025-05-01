package com.tenacy.roadcapture.ui

import android.location.Location
import androidx.lifecycle.viewModelScope
import com.google.android.gms.maps.model.LatLng
import com.tenacy.roadcapture.data.db.LocationDao
import com.tenacy.roadcapture.data.db.LocationEntity
import com.tenacy.roadcapture.data.db.MemoryDao
import com.tenacy.roadcapture.data.db.MemoryWithLocation
import com.tenacy.roadcapture.data.pref.Album
import com.tenacy.roadcapture.ui.TripFragment.Marker
import com.tenacy.roadcapture.util.getDurationFormattedText
import com.tenacy.roadcapture.util.toTimestamp
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import javax.inject.Inject

@HiltViewModel
class TripViewModel @Inject constructor(
    private val locationDao: LocationDao,
    private val memoryDao: MemoryDao,
) : BaseViewModel() {

    private val _locations = MutableStateFlow<List<LocationEntity>>(emptyList())
    private val _memories = MutableStateFlow<List<MemoryWithLocation>>(emptyList())

    private val _durationText = MutableStateFlow<String?>(null)
    val durationText = _durationText.asStateFlow()

    val memoryCountText = _memories.map { "추억 ${it.size} / 10" }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), null)

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

    private val _lastLocation = MutableStateFlow<LatLng?>(null)

    init {
        fetchData()
        observeData()
    }

    private fun observeData() {
        viewModelScope.launch(Dispatchers.IO) {
            _lastLocation.collect(Album::saveLastLocation)
        }
    }

    fun fetchData() {
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
    }

    fun startTraveling() {
        if(Album.createdAt == 0L) {
            Album.createdAt = LocalDateTime.now().toTimestamp()
        }
        updateDurationText()
    }

    fun updateDurationText() {
        _durationText.update { getDurationFormattedText(Album.createdAt, LocalDateTime.now().toTimestamp()) }
    }

    fun stopTraveling() {
        viewModelScope.launch(Dispatchers.IO) {
            Album.clear()
            memoryDao.clear()
            locationDao.clear()
            _durationText.update { null }

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
                _lastLocation.update { latLng }
            }
        }
    }

    private fun shouldSaveLocation(currentLatLng: LatLng): Boolean {
        val lastSavedLocation = _lastLocation.value ?: return true

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

    fun onResetCameraPositionClick() {
        viewModelScope.launch(Dispatchers.Default) {
            viewEvent(TripViewEvent.ResetCameraPosition)
        }
    }

    fun onResetCameraClick() {
        viewModelScope.launch(Dispatchers.Default) {
            viewEvent(TripViewEvent.ResetCamera)
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