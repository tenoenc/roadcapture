package com.tenacy.roadcapture.ui

import androidx.lifecycle.viewModelScope
import com.tenacy.roadcapture.data.firebase.exception.SystemConfigException
import com.tenacy.roadcapture.di.InputModule
import com.tenacy.roadcapture.util.handleSystemConfigException
import com.tenacy.roadcapture.util.validateSystemConfigV2
import dagger.hilt.android.lifecycle.HiltViewModel
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
            try {
                // [VALIDATE_SYSTEM_CONFIG]
                validateSystemConfigV2()
            } catch (exception: Exception) {
                // [VALIDATE_SYSTEM_CONFIG]
                if(exception is SystemConfigException) {
                    handleSystemConfigException(exception)
                    return@launch
                }
            }
            viewEvent(NewAlbumViewEvent.ShowCompleteBefore)
        }
    }
}