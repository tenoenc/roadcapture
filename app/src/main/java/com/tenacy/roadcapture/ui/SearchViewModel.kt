package com.tenacy.roadcapture.ui

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.tenacy.roadcapture.data.firebase.AlbumFilter
import com.tenacy.roadcapture.data.firebase.AlgoliaPagingSource
import com.tenacy.roadcapture.manager.AlgoliaManager
import com.tenacy.roadcapture.ui.dto.Album
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SearchViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val algoliaManager: AlgoliaManager,
) : BaseViewModel() {

    data class State(
        val pagingDataFlow: Flow<PagingData<Album>> = emptyFlow(),
        val shouldLoad: Boolean = false,
    )

    private val albumFilter = SearchFragmentArgs.fromSavedStateHandle(savedStateHandle).albumFilter
    val title = when(albumFilter) {
        AlbumFilter.ALL -> "홈"
        else -> "북마크"
    }

    val searchQuery = MutableStateFlow("")

    private val _state = MutableStateFlow(State())
    val _pagingData = MutableStateFlow<PagingData<Album>>(PagingData.empty())
    val pagingData = _pagingData.asStateFlow()

    // 검색 실행
    fun performSearch() {
        val searchQuery = searchQuery.value
        if (searchQuery.isBlank()) {
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            Pager(
                config = PagingConfig(
                    pageSize = AlgoliaPagingSource.PAGE_SIZE,
                    enablePlaceholders = false,
                    maxSize = AlgoliaPagingSource.PAGE_SIZE * 5,
                    prefetchDistance = AlgoliaPagingSource.PAGE_SIZE,
                    initialLoadSize = AlgoliaPagingSource.PAGE_SIZE
                ),
                pagingSourceFactory = {
                    AlgoliaPagingSource(algoliaManager, searchQuery, albumFilter)
                }
            ).flow
                .flowOn(Dispatchers.IO)
                .catch { exception ->
                    Log.e("SearchViewModel", "Search error", exception)
                    viewEvent(SearchViewEvent.SearchError(exception.message ?: "검색 중 오류가 발생했습니다"))
                }
                .cachedIn(viewModelScope)
                .collect {
                    _pagingData.emit(it)
                }
        }
    }
}