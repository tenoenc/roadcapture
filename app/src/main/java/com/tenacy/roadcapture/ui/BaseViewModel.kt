package com.tenacy.roadcapture.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

open class BaseViewModel : ViewModel() {

//    private val _viewEvent = MutableSharedFlow<Event<ViewEvent>?>()
//    val viewEvent: SharedFlow<Event<ViewEvent>?>
//        get() = _viewEvent.asSharedFlow()

    private val _viewEvent = Channel<Event<ViewEvent>>(Channel.UNLIMITED)
    val viewEvent = _viewEvent.receiveAsFlow()

    fun viewEvent(content: ViewEvent) {
        viewModelScope.launch(Dispatchers.Default) {
    //        _viewEvent.emit(Event(content))
            _viewEvent.send(Event(content))
        }
    }
}