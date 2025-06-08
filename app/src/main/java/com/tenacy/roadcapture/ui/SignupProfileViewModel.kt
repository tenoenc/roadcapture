package com.tenacy.roadcapture.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.tenacy.roadcapture.util.FirebaseConstants
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SignupProfileViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
): BaseViewModel() {
    private val args = SignupProfileFragmentArgs.fromSavedStateHandle(savedStateHandle)
    private var selectedDefaultProfile: DefaultProfile = DefaultProfile.App(FirebaseConstants.DEFAULT_PROFILE_URL)

    val socialProfileUrl = args.socialProfileUrl

    private val _socialProfileSelected = MutableStateFlow(false)
    val socialProfileSelected = _socialProfileSelected.onEach { selected ->
        if(selected) selectedDefaultProfile = DefaultProfile.Social(args.socialProfileUrl)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), false)

    private val _appProfileSelected = MutableStateFlow(false)
    val appProfileSelected = _appProfileSelected.onEach { selected ->
        if(selected) selectedDefaultProfile = DefaultProfile.App(FirebaseConstants.DEFAULT_PROFILE_URL)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), false)

    val anySelected = combine(_socialProfileSelected, _appProfileSelected) { socialProfileSelected, appProfileSelected ->
        socialProfileSelected || appProfileSelected
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), false)

    fun onSocialProfileClick() {
        _socialProfileSelected.update { true }
        _appProfileSelected.update { false }
    }

    fun onAppProfileClick() {
        _appProfileSelected.update { true }
        _socialProfileSelected.update { false }
    }

    fun onNextClick() {
        viewModelScope.launch(Dispatchers.IO) {
            viewEvent(SignupProfileViewEvent.Next(selectedDefaultProfile))
        }
    }
}