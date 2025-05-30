package com.tenacy.roadcapture.ui

import android.util.Log
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.google.firebase.firestore.FieldValue
import com.tenacy.roadcapture.data.ReportReason
import com.tenacy.roadcapture.data.firebase.AlbumFilter
import com.tenacy.roadcapture.data.firebase.AlbumPagingSource
import com.tenacy.roadcapture.data.firebase.AlbumPagingSourceWithAds
import com.tenacy.roadcapture.data.pref.UserPref
import com.tenacy.roadcapture.manager.SubscriptionManager
import com.tenacy.roadcapture.ui.dto.AlbumItemWithAds
import com.tenacy.roadcapture.util.db
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    subscriptionManager: SubscriptionManager,
) : BaseViewModel() {

    private val isSubscriptionActive: StateFlow<Boolean> = subscriptionManager.isSubscriptionActive
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val subscriptionState = subscriptionManager.subscriptionState
        .distinctUntilChanged { old, new ->
            old.isActive == new.isActive // 활성 상태가 변경된 경우만 처리
        }

    // 앨범 데이터 Flow
    val albums: Flow<PagingData<AlbumItemWithAds>> = isSubscriptionActive.flatMapLatest { isActive ->
        Pager(
            config = PagingConfig(
                pageSize = AlbumPagingSource.PAGE_SIZE,
                enablePlaceholders = false,
                maxSize = AlbumPagingSource.PAGE_SIZE * 5,
                prefetchDistance = AlbumPagingSource.PAGE_SIZE,
                initialLoadSize = AlbumPagingSource.PAGE_SIZE
            ),
            pagingSourceFactory = {
                AlbumPagingSourceWithAds(
                    filter = AlbumFilter.All,
                    minAdPosition = 2,
                    maxAdPosition = 4,
                    minAdInterval = 3,
                    maxAdInterval = 6,
                    adDensity = 0.7f,
                    showAds = !isActive
                )
            }
        ).flow
    }.cachedIn(viewModelScope)

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
                    Log.e("HomeViewModel", "에러", exception)
                }
                .collect {
                    viewEvent(HomeViewEvent.ReportComplete)
                }
        }
    }

    fun onSearchClick() {
        viewModelScope.launch(Dispatchers.Default) {
            viewEvent(HomeViewEvent.Search)
        }
    }
}