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
class ModifyUsernameViewModel @Inject constructor(
) : BaseViewModel() {

    val username = MutableStateFlow("")

    val usernameLength = username.map { it.length }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000L),
        initialValue = 0,
    )

    private val _usernameFocus = MutableStateFlow(false)

    private val _usernameInputAttemptOverflow = MutableStateFlow(false)

    val usernameState = combine(
        username,
        _usernameFocus,
        _usernameInputAttemptOverflow,
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

    fun setUsernameFocus(hasFocus: Boolean) {
        _usernameFocus.update { hasFocus }
    }

    fun onUsernameInputAttempt(currentLength: Int) {
        _usernameInputAttemptOverflow.update { currentLength >= InputModule.MAX_LENGTH_USERNAME }
    }

    fun onCompleteClick() {
        viewModelScope.launch(Dispatchers.Default) {

            viewEvent(ModifyUsernameViewEvent.Complete(username.value))
        }
    }
}