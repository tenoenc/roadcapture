package com.tenacy.roadcapture.ui

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.tenacy.roadcapture.data.db.LocationDao
import com.tenacy.roadcapture.data.db.LocationEntity
import com.tenacy.roadcapture.data.db.MemoryDao
import com.tenacy.roadcapture.data.db.MemoryEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import javax.inject.Inject

@HiltViewModel
class NewMemoryViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val memoryDao: MemoryDao,
    private val locationDao: LocationDao,
) : BaseViewModel() {

    val tags: List<String>
    val photoUri: Uri

    val content = MutableStateFlow("")

    val contentLength = content.map { it.length }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000L),
        initialValue = 0,
    )

    private val _contentFocus = MutableStateFlow(false)

    private val _contentInputAttemptOverflow = MutableStateFlow(false)

    val contentState = combine(
        content,
        _contentFocus,
        _contentInputAttemptOverflow,
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

    init {
        photoUri = NewMemoryFragmentArgs.fromSavedStateHandle(savedStateHandle).photoUri
        val placeLocation: TripFragment.PlaceLocation = NewMemoryFragmentArgs.fromSavedStateHandle(savedStateHandle).placeLocation
        tags = listOfNotNull(
            placeLocation.country,
            placeLocation.name,
            placeLocation.region,
            placeLocation.city,
            placeLocation.district,
            placeLocation.street,
        )
    }

    fun setContentFocus(hasFocus: Boolean) {
        _contentFocus.update { hasFocus }
    }

    fun onContentInputAttempt(currentLength: Int) {
        _contentInputAttemptOverflow.update{ currentLength >= 20 }
    }

    fun onNewClick() {
        viewModelScope.launch(Dispatchers.IO) {
            val currentContent = content.value
            val placeLocation: TripFragment.PlaceLocation = NewMemoryFragmentArgs.fromSavedStateHandle(savedStateHandle).placeLocation

            val locationEntity = LocationEntity(
                latitude = placeLocation.coordinates.latitude,
                longitude = placeLocation.coordinates.longitude,
                createdAt = LocalDateTime.now(),
            )

            val locationId = locationDao.insert(locationEntity)

            val memoryEntity = MemoryEntity(
                content = currentContent,
                photoUri = photoUri,
                locationName = placeLocation.name,
                country = placeLocation.country,
                region = placeLocation.region,
                city = placeLocation.city,
                district = placeLocation.district,
                street = placeLocation.street,
                details = placeLocation.detail,
                formattedAddress = placeLocation.formattedAddress,
                locationId = locationId,
                createdAt = LocalDateTime.now(),
            )

            val memoryId = memoryDao.insert(memoryEntity)

            viewEvent(NewMemoryViewEvent.ResultBack(memoryId))
        }
    }
}