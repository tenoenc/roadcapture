package com.tenacy.roadcapture.ui

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.google.android.gms.maps.model.LatLng
import com.navercorp.nid.oauth.NidOAuthPreferencesManager.state
import com.tenacy.roadcapture.data.db.LocationDao
import com.tenacy.roadcapture.data.db.MemoryDao
import com.tenacy.roadcapture.data.db.MemoryWithLocation
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ModifiableMemoryViewerViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val memoryDao: MemoryDao,
    private val locationDao: LocationDao,
) : BaseViewModel() {

    // 통합 상태 객체 정의
    data class ViewerState(
        val memories: List<MemoryWithLocation> = emptyList(),
        val currentIndex: Int = 0,
        val viewScope: ViewScope? = null
    ) {
        val totalPageCount: Int = memories.size
        val currentMemory: MemoryWithLocation? = memories.getOrNull(currentIndex)
        val photoUris: List<Uri> = memories.map { it.memory.photoUri }
    }

    private val _state = MutableStateFlow(ViewerState())

    // 파생 상태들 (UI에 노출할 상태들)
    val currentMemoryIndex = _state.map { it.currentIndex }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), 0)

    val totalPageCount = _state.map { it.totalPageCount }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), 0)

    val viewScope = _state.map { it.viewScope }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), null)

    val currentMemory = _state.map { it.currentMemory }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val tags = _state.mapNotNull { it.currentMemory?.memory?.addressTags }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val placeName = _state.map { it.currentMemory?.memory?.placeName ?: "" }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), "")

    val photoUris = _state.map { it.photoUris }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val content = _state.map { it.currentMemory?.memory?.content ?: "" }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), "")

    init {
        fetchData()
    }

    // 상태 업데이트 함수
    fun updateCurrentMemoryIndex(index: Int) {
        _state.update { currentState ->
            currentState.copy(currentIndex = index)
        }
    }

    private fun fetchData() {
        viewModelScope.launch(Dispatchers.IO) {
            val argument: TripFragment.ClusterMarkerItems = MemoryViewerFragmentArgs.fromSavedStateHandle(savedStateHandle).clusterMarkerItems
            val viewScope = argument.viewScope

            when(viewScope) {
                ViewScope.AROUND -> {
                    val ids = argument.items!!.map { it.id }
                    val memories = memoryDao.selectByLocationIds(ids)
                    _state.update {
                        it.copy(
                            memories = memories,
                            currentIndex = 0,
                            viewScope = viewScope
                        )
                    }
                }
                else -> {
                    val memories = memoryDao.selectAll()
                    val selectedMemoryId = argument.selectedMemoryId ?: argument.items!![0].id
                    val currentMemoryIndex = memories.indexOfFirst { memoryWithLocation ->
                        memoryWithLocation.location.id == selectedMemoryId
                    }
                    _state.update {
                        it.copy(
                            memories = memories,
                            currentIndex = currentMemoryIndex,
                            viewScope = viewScope
                        )
                    }
                }
            }
        }
    }

    fun deleteCurrentMemory() {
        viewModelScope.launch(Dispatchers.IO) {
            val currentState = _state.value
            val currentMemory = currentState.currentMemory ?: return@launch

            memoryDao.deleteById(currentMemory.memory.id)
            locationDao.deleteById(currentMemory.location.id)

            if(currentState.totalPageCount > 1) {
                // 메모리가 1개 이상 남아있는 경우
                val newIndex = (currentState.currentIndex - 1).coerceAtLeast(0)

                val argument: TripFragment.ClusterMarkerItems = MemoryViewerFragmentArgs.fromSavedStateHandle(savedStateHandle).clusterMarkerItems
                val viewScope = argument.viewScope

                val memories = when(viewScope) {
                    ViewScope.AROUND -> {
                        val ids = argument.items!!.map { it.id }.filterNot { it == currentMemory.location.id }
                        memoryDao.selectByLocationIds(ids)
                    }
                    else -> memoryDao.selectAll()
                }

                _state.update {
                    it.copy(
                        memories = memories,
                        currentIndex = newIndex,
                        viewScope = if(memories.size == 1) ViewScope.WHOLE else viewScope,
                    )
                }
                viewEvent(ModifiableMemoryViewerViewEvent.MoveToPrevPage)
            } else {
                // 더 이상 남은 메모리가 없는 경우
                viewEvent(ModifiableMemoryViewerViewEvent.ResultBack())
            }
        }
    }

    fun onLocationClick() {
        viewModelScope.launch(Dispatchers.Default) {
            val currentMemory = _state.value.currentMemory ?: return@launch
            viewEvent(ModifiableMemoryViewerViewEvent.ShowLocation(currentMemory.memory.formattedAddress))
        }
    }

    fun onPrevPageClick() {
        viewModelScope.launch(Dispatchers.Default) {
            viewEvent(ModifiableMemoryViewerViewEvent.MoveToPrevPage)
        }
    }

    fun onNextPageClick() {
        viewModelScope.launch(Dispatchers.Default) {
            viewEvent(ModifiableMemoryViewerViewEvent.MoveToNextPage)
        }
    }

    fun onMoreClick() {
        viewModelScope.launch(Dispatchers.Default) {
            viewEvent(ModifiableMemoryViewerViewEvent.ShowMore)
        }
    }

    fun onBackClick() {
        viewModelScope.launch(Dispatchers.Default) {
            val currentMemory = _state.value.currentMemory ?: return@launch
            val coordinates = LatLng(currentMemory.location.latitude, currentMemory.location.longitude)
            viewEvent(ModifiableMemoryViewerViewEvent.ResultBack(coordinates))
        }
    }
}