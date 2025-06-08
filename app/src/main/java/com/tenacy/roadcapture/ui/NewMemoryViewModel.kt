package com.tenacy.roadcapture.ui

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.tenacy.roadcapture.di.InputModule
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class NewMemoryViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
) : BaseViewModel() {

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

    fun onNewClick() {
        viewModelScope.launch(Dispatchers.Main) {
            viewEvent(NewMemoryViewEvent.ShowAd)
        }
    }
}