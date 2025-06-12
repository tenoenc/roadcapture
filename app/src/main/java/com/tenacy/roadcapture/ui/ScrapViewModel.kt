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
import com.tenacy.roadcapture.data.firebase.exception.SystemConfigException
import com.tenacy.roadcapture.data.pref.UserPref
import com.tenacy.roadcapture.ui.dto.Album
import com.tenacy.roadcapture.util.FirebaseConstants
import com.tenacy.roadcapture.util.db
import com.tenacy.roadcapture.util.handleSystemConfigException
import com.tenacy.roadcapture.util.validateSystemConfig
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

@HiltViewModel
class ScrapViewModel @Inject constructor(

) : BaseViewModel() {

    // 페이징 소스 팩토리를 변수로 분리하여 항상 새로운 인스턴스를 생성하도록 합니다
    private val pagingSourceFactory = {
        AlbumPagingSource(filter = AlbumFilter.Scrap)
    }

    // 페이징 설정 최적화
    private val pager = Pager(
        config = PagingConfig(
            pageSize = AlbumPagingSource.PAGE_SIZE,
            enablePlaceholders = false,
            maxSize = AlbumPagingSource.PAGE_SIZE * 5,
            prefetchDistance = AlbumPagingSource.PAGE_SIZE,
            initialLoadSize = AlbumPagingSource.PAGE_SIZE
        ),
        pagingSourceFactory = pagingSourceFactory
    )

    // 앨범 데이터 Flow
    val albums: Flow<PagingData<Album>> = pager.flow
        .flowOn(Dispatchers.IO)
        .cachedIn(viewModelScope)

    fun report(albumId: String, reason: ReportReason) {
        viewModelScope.launch(Dispatchers.IO) {
            flow {
                // [VALIDATE_SYSTEM_CONFIG]
                validateSystemConfig()

                val userId = UserPref.id
                val userRef = db.collection(FirebaseConstants.COLLECTION_USERS).document(userId)
                val albumRef = db.collection(FirebaseConstants.COLLECTION_ALBUMS).document(albumId)

                val reportData = mapOf(
                    "userRef" to userRef,
                    "albumRef" to albumRef,
                    "reason" to reason.name,
                    "createdAt" to FieldValue.serverTimestamp(),
                )

                db.collection(FirebaseConstants.COLLECTION_REPORTS)
                    .document()
                    .set(reportData)
                    .await()

                emit(Unit)
            }
                .catch { exception ->
                    Log.e("ScrapViewModel", "에러", exception)
                    // [VALIDATE_SYSTEM_CONFIG]
                    if(exception is SystemConfigException) {
                        handleSystemConfigException(exception)
                        return@catch
                    }
                }
                .collect {
                    viewEvent(ScrapViewEvent.ReportComplete)
                }
        }
    }

    fun onSearchClick() {
        viewModelScope.launch(Dispatchers.Default) {
            viewEvent(ScrapViewEvent.Search)
        }
    }
}