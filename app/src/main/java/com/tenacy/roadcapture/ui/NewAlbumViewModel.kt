package com.tenacy.roadcapture.ui

import android.content.Context
import androidx.lifecycle.viewModelScope
import com.tenacy.roadcapture.data.db.LocationDao
import com.tenacy.roadcapture.data.db.MemoryDao
import com.tenacy.roadcapture.di.InputModule
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class NewAlbumViewModel @Inject constructor(
) : BaseViewModel() {

    val albumTitle = MutableStateFlow("")

    val albumTitleLength = albumTitle.map { it.length }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000L),
        initialValue = 0,
    )

    private val _albumTitleFocus = MutableStateFlow(false)

    private val _albumTitleInputAttemptOverflow = MutableStateFlow(false)

    val albumTitleState = combine(
        albumTitle,
        _albumTitleFocus,
        _albumTitleInputAttemptOverflow,
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

    fun setAlbumTitleFocus(hasFocus: Boolean) {
        _albumTitleFocus.update { hasFocus }
    }

    fun onAlbumTitleInputAttempt(currentLength: Int) {
        _albumTitleInputAttemptOverflow.update { currentLength >= InputModule.MAX_LENGTH_ALBUM_TITLE }
    }

    fun onCompleteClick() {
        viewModelScope.launch(Dispatchers.Default) {
            viewEvent(NewAlbumViewEvent.ShowCompleteBefore)
        }
    }
}