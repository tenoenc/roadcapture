package com.tenacy.roadcapture.ui

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.tenacy.roadcapture.data.db.LocationDao
import com.tenacy.roadcapture.data.db.LocationEntity
import com.tenacy.roadcapture.data.db.MemoryDao
import com.tenacy.roadcapture.data.db.MemoryEntity
import com.tenacy.roadcapture.data.pref.TravelPref.createdAt
import com.tenacy.roadcapture.di.InputModule
import com.tenacy.roadcapture.ui.dto.Address
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

    val addressTags: List<String>
    val photoUri: Uri = NewMemoryFragmentArgs.fromSavedStateHandle(savedStateHandle).photoUri

    val placeName = MutableStateFlow("")

    val placeNameLength = placeName.map { it.length }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000L),
        initialValue = 0,
    )

    private val _placeNameFocus = MutableStateFlow(false)

    private val _placeNameInputAttemptOverflow = MutableStateFlow(false)

    val placeNameState = combine(
        placeName,
        _placeNameFocus,
        _placeNameInputAttemptOverflow,
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
        val address: Address = NewMemoryFragmentArgs.fromSavedStateHandle(savedStateHandle).address
        addressTags = listOfNotNull(address.country) + address.components
    }

    fun setPlaceNameFocus(hasFocus: Boolean) {
        _placeNameFocus.update { hasFocus }
    }

    fun onPlaceNameInputAttempt(currentLength: Int) {
        _placeNameInputAttemptOverflow.update{ currentLength >= InputModule.MAX_LENGTH_PLACE_NAME }
    }

    fun setContentFocus(hasFocus: Boolean) {
        _contentFocus.update { hasFocus }
    }

    fun onContentInputAttempt(currentLength: Int) {
        _contentInputAttemptOverflow.update{ currentLength >= InputModule.MAX_LENGTH_CONTENT }
    }

    fun onLocationClick() {
        viewModelScope.launch(Dispatchers.Default) {
            val address: Address = NewMemoryFragmentArgs.fromSavedStateHandle(savedStateHandle).address
            address.formattedAddress?.let { viewEvent(NewMemoryViewEvent.ShowLocation(it)) }
        }
    }

    fun onNewClick() {
        viewModelScope.launch(Dispatchers.Main) {
            viewEvent(NewMemoryViewEvent.ShowAd)
        }
    }

    fun saveMemory() {
        viewModelScope.launch(Dispatchers.IO) {
            val currentPlaceName = placeName.value
            val currentContent = content.value
            val address: Address = NewMemoryFragmentArgs.fromSavedStateHandle(savedStateHandle).address

            val locationEntity = LocationEntity(
                coordinates = address.coordinates,
                createdAt = LocalDateTime.now(),
            )

            val locationId = locationDao.insert(locationEntity)

            val memoryEntity = MemoryEntity(
                placeName = currentPlaceName.takeIf { it.isNotBlank() },
                content = currentContent.takeIf { it.isNotBlank() },
                photoUri = photoUri,
                addressTags = addressTags,
                formattedAddress = address.formattedAddress ?: "",
                locationId = locationId,
                createdAt = LocalDateTime.now(),
            )

            val memoryId = memoryDao.insert(memoryEntity)

            viewEvent(NewMemoryViewEvent.ResultBack(memoryId, address.coordinates))
        }
    }
}