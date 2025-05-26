package com.tenacy.roadcapture.ui

import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
) : BaseViewModel() {

    fun onNewClick() {
        viewModelScope.launch(Dispatchers.IO) {
            viewEvent(MainViewEvent.ShowTripBefore)
        }
    }

}