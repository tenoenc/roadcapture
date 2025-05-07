package com.tenacy.roadcapture.ui

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow

open class BaseViewModel : ViewModel() {

//    private val _viewEvent = MutableSharedFlow<Event<ViewEvent>?>()
//    val viewEvent: SharedFlow<Event<ViewEvent>?>
//        get() = _viewEvent.asSharedFlow()

    private val _viewEvent = Channel<Event<ViewEvent>>(Channel.UNLIMITED)
    val viewEvent = _viewEvent.receiveAsFlow()

    suspend fun viewEvent(content: ViewEvent) {
//        _viewEvent.emit(Event(content))
        _viewEvent.send(Event(content))
    }
}