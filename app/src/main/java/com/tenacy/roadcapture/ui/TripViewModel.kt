package com.tenacy.roadcapture.ui

import android.location.Location
import android.net.Uri
import android.util.Log
import androidx.lifecycle.viewModelScope
import com.google.android.gms.maps.model.LatLng
import com.tenacy.roadcapture.BuildConfig
import com.tenacy.roadcapture.data.db.*
import com.tenacy.roadcapture.data.pref.Album
import com.tenacy.roadcapture.ui.TripFragment.Marker
import com.tenacy.roadcapture.util.RetrofitInstance
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

    val albumCreatedAt = Album.createdAt

    private val _lastSavedLocation = MutableStateFlow<LatLng?>(null)

    init {
        loadData()
        observeData()
    }

    private fun observeData() {
        viewModelScope.launch(Dispatchers.IO) {
            _lastSavedLocation.collect(Album::saveLastLocation)
        }
    }

    private fun loadData() {
        viewModelScope.launch(Dispatchers.IO) {
            val locations = locationDao.selectAll()
            val memories = memoryDao.selectAll()
            // 마지막 저장 위치 설정
            if (locations.isNotEmpty()) {
                val last = locations.maxByOrNull { it.createdAt }
                last?.let {
                    _lastSavedLocation.emit(LatLng(it.latitude, it.longitude))
                }
            }
            _memories.emit(memories)
            _locations.emit(locations)
        }
    }

    fun isAlbumCreated() = Album.createdAt > 0L

    fun saveCurrentLocation(latLng: LatLng) {
        viewModelScope.launch(Dispatchers.IO) {
            if (shouldSaveLocation(latLng)) {
                val locationEntity = LocationEntity(
                    latitude = latLng.latitude,
                    longitude = latLng.longitude,
                    createdAt = LocalDateTime.now()
                )

                val locationId = locationDao.insert(locationEntity)

                _locations.update {
                    it.toMutableList().apply { add(locationEntity.copy(id = locationId)) }
                }

                _lastSavedLocation.update { latLng }
            }
        }
    }

    private fun shouldSaveLocation(currentLatLng: LatLng): Boolean {
        val lastSavedLocation = _lastSavedLocation.value ?: return true

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

    companion object {
        const val MIN_DISTANCE_TO_SAVE = 30f
    }
}