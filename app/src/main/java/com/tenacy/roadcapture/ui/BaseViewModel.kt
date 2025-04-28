package com.tenacy.roadcapture.ui

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.*

open class BaseViewModel : ViewModel() {

    private val _viewEvent = MutableSharedFlow<Event<ViewEvent>?>()
    val viewEvent: SharedFlow<Event<ViewEvent>?>
        get() = _viewEvent.asSharedFlow()

    suspend fun viewEvent(content: ViewEvent) {
        _viewEvent.emit(Event(content))
    }
}