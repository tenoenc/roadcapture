package com.tenacy.roadcapture.ui

import com.tenacy.roadcapture.util.user
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
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

}