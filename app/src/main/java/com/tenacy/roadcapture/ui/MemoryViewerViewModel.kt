package com.tenacy.roadcapture.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.tenacy.roadcapture.ui.dto.MemoryViewerArguments
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MemoryViewerViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
) : BaseViewModel() {

    private val _currentMemoryIndex = MutableStateFlow(0)
    val currentMemoryIndex = _currentMemoryIndex.asStateFlow()

    private val _memories = MutableStateFlow<List<MemoryViewerArguments.Memory>>(emptyList())

    val totalPageCount = _memories.map { it.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), 0)

    private val _viewScope = MutableStateFlow<ViewScope?>(null)
    val viewScope = _viewScope.asStateFlow()

    val currentMemory = combine(_memories, _currentMemoryIndex) { memories, currentPage ->
        if(memories.isEmpty()) return@combine null
        memories[currentPage]
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), null)

    val isThumbnail = currentMemory.map { it?.isThumbnail }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), false)

    val tags = currentMemory.mapNotNull { currentMemory ->
        currentMemory?.addressTags
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val placeName = currentMemory.map { currentMemory ->
        currentMemory?.placeName ?: ""
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), "")

    val photoUrls = _memories.map { memories -> memories.map { it.photoUrl } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val content = currentMemory.map { currentMemory ->
        currentMemory?.content ?: ""
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), "")

    init {
        fetchData()
    }

    fun updateCurrentMemoryIndex(index: Int) {
        _currentMemoryIndex.update { index }
    }

    private fun fetchData() {
        viewModelScope.launch(Dispatchers.IO) {
            val argument: MemoryViewerArguments = MemoryViewerFragmentArgs.fromSavedStateHandle(savedStateHandle).args
            val viewScope = argument.viewScope
            val memories = argument.memories

            when(viewScope) {
                ViewScope.AROUND -> {
                    _currentMemoryIndex.emit(0)
                }
                else -> {
                    val selectedMemoryId = argument.selectedMemoryId ?: argument.memories[0].id
                    val currentMemoryIndex = argument.memories.indexOfFirst { memory ->  memory.id == selectedMemoryId }
                    _currentMemoryIndex.emit(currentMemoryIndex)
                }
            }

            _memories.emit(memories)
            _viewScope.emit(viewScope)
        }
    }

    fun onLocationClick() {
        viewModelScope.launch(Dispatchers.Default) {
            val currentMemory = currentMemory.value ?: return@launch
            viewEvent(MemoryViewerViewEvent.ShowLocation(currentMemory.formattedAddress))
        }
    }

    fun onPrevPageClick() {
        viewModelScope.launch(Dispatchers.Default) {
            viewEvent(MemoryViewerViewEvent.MoveToPrevPage)
        }
    }

    fun onNextPageClick() {
        viewModelScope.launch(Dispatchers.Default) {
            viewEvent(MemoryViewerViewEvent.MoveToNextPage)
        }
    }

    fun onInfoClick() {
        viewModelScope.launch(Dispatchers.Default) {
            viewEvent(MemoryViewerViewEvent.ShowInfo)
        }
    }

    fun onBackClick() {
        viewModelScope.launch(Dispatchers.Default) {
            val currentMemory = currentMemory.value ?: return@launch
            val coordinates = currentMemory.coordinates
            viewEvent(MemoryViewerViewEvent.ResultBack(coordinates))
        }
    }

}