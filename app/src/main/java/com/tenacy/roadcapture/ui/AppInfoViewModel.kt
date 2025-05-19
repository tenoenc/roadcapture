package com.tenacy.roadcapture.ui

import androidx.lifecycle.viewModelScope
import com.tenacy.roadcapture.util.user
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AppInfoViewModel @Inject constructor(

) : BaseViewModel() {

    val profilePhotoUrl = MutableStateFlow(user!!.photoUrl)
    val profileDisplayName = MutableStateFlow(user!!.displayName)

    fun refreshStates() {
        profilePhotoUrl.update { user!!.photoUrl }
        profileDisplayName.update { user!!.displayName }
    }

    fun onServiceTermsAndConditionsClick() {
        viewModelScope.launch(Dispatchers.Default) {
            viewEvent(AppInfoViewEvent.NavigateToHtml(HtmlType.ServiceTermsAndConditions))
        }
    }

    fun onPersonalInfoPolicyClick() {
        viewModelScope.launch(Dispatchers.Default) {
            viewEvent(AppInfoViewEvent.NavigateToHtml(HtmlType.PersonalInfoPolicy))
        }
    }

}