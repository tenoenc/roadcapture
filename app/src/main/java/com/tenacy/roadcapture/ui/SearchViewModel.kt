package com.tenacy.roadcapture.ui

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.google.firebase.firestore.FieldValue
import com.tenacy.roadcapture.data.ReportReason
import com.tenacy.roadcapture.data.firebase.AlgoliaPagingSource
import com.tenacy.roadcapture.data.firebase.SearchFilter
import com.tenacy.roadcapture.data.pref.UserPref
import com.tenacy.roadcapture.manager.AlgoliaManager
import com.tenacy.roadcapture.ui.dto.Album
import com.tenacy.roadcapture.util.db
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
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

    fun report(albumId: String, reason: ReportReason) {
        viewModelScope.launch(Dispatchers.IO) {
            flow {
                val userId = UserPref.id
                val userRef = db.collection("users").document(userId)
                val albumRef = db.collection("albums").document(albumId)

                val reportData = mapOf(
                    "userRef" to userRef,
                    "albumRef" to albumRef,
                    "reason" to reason.name,
                    "createdAt" to FieldValue.serverTimestamp(),
                )

                db.collection("reports")
                    .document()
                    .set(reportData)
                    .await()

                emit(Unit)
            }
                .catch { exception ->
                    Log.e("ScrapViewModel", "에러", exception)
                }
                .collect {
                    viewEvent(SearchViewEvent.ReportComplete)
                }
        }
    }
}