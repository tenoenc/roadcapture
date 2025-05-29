package com.tenacy.roadcapture.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class UserMemoryViewerViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
) : BaseViewModel() {

    val currentMemory = UserMemoryViewerFragmentArgs.fromSavedStateHandle(savedStateHandle).memory

    val photoUrl = currentMemory.photoUrl
    val tags = currentMemory.addressTags
    val placeName = currentMemory.placeName
    val content = currentMemory.content

    fun onLocationClick() {
        viewModelScope.launch(Dispatchers.Default) {
            viewEvent(UserMemoryViewerViewEvent.ShowLocation(currentMemory.formattedAddress))
        }
    }

    fun onMoreClick() {
        viewModelScope.launch(Dispatchers.Default) {
            viewEvent(UserMemoryViewerViewEvent.ShowMore)
        }
    }
}