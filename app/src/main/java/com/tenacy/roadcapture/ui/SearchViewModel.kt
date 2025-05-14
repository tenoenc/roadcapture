package com.tenacy.roadcapture.ui

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.tenacy.roadcapture.data.firebase.AlgoliaPagingSource
import com.tenacy.roadcapture.data.firebase.SearchFilter
import com.tenacy.roadcapture.manager.AlgoliaManager
import com.tenacy.roadcapture.ui.dto.Album
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SearchViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val algoliaManager: AlgoliaManager,
) : BaseViewModel() {

    private val filter = SearchFragmentArgs.fromSavedStateHandle(savedStateHandle).albumFilter
    val title = when(filter) {
        SearchFilter.All -> "홈"
        SearchFilter.Scrap -> "스크랩"
    }

    val searchQuery = MutableStateFlow("")

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
                    AlgoliaPagingSource(algoliaManager, searchQuery, filter)
                }
            ).flow
                .flowOn(Dispatchers.IO)
                .catch { exception ->
                    Log.e("SearchViewModel", "Search error", exception)
                }
                .cachedIn(viewModelScope)
                .collect {
                    _pagingData.emit(it)
                }
        }
    }
}