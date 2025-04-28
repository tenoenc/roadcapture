package com.tenacy.roadcapture.ui

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import javax.inject.Inject

@HiltViewModel
class NewMemoryViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
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

    fun setContentFocus(hasFocus: Boolean) {
        _contentFocus.update { hasFocus }
    }

    fun onContentInputAttempt(currentLength: Int) {
        _contentInputAttemptOverflow.update{ currentLength >= 20 }
    }

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
}