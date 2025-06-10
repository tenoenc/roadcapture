package com.tenacy.roadcapture.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.tenacy.roadcapture.R
import com.tenacy.roadcapture.data.SocialType
import com.tenacy.roadcapture.util.FirebaseConstants
import com.tenacy.roadcapture.util.ResourceProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SignupProfileViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val resourceProvider: ResourceProvider,
): BaseViewModel() {
    private val args = SignupProfileFragmentArgs.fromSavedStateHandle(savedStateHandle)
    private var selectedDefaultProfile: DefaultProfile = DefaultProfile.App(FirebaseConstants.DEFAULT_PROFILE_URL)

    val socialProfileUrl = args.socialProfileUrl

    val providerName = resourceProvider.configurationContextFlow.map { context ->
        when(args.socialType) {
            SocialType.Google -> context.getString(R.string.google)
            SocialType.Facebook -> context.getString(R.string.facebook)
            SocialType.Kakao -> context.getString(R.string.kakao)
            else -> ""
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), "")

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