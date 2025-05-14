package com.tenacy.roadcapture.ui

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.google.firebase.firestore.FieldValue
import com.tenacy.roadcapture.data.firebase.AlbumFilter
import com.tenacy.roadcapture.data.firebase.AlbumPagingSource
import com.tenacy.roadcapture.ui.dto.Album
import com.tenacy.roadcapture.util.db
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

@HiltViewModel
class MyAlbumTabViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
) : BaseViewModel() {

    private val params: MyAlbumTabFragment.ParamsIn? = savedStateHandle.get<MyAlbumTabFragment.ParamsIn>(MyAlbumTabFragment.KEY_PARAMS)
    private val userId: String = params?.userId ?: ""

    // 페이징 소스 팩토리를 변수로 분리하여 항상 새로운 인스턴스를 생성하도록 합니다
    private val pagingSourceFactory = {
        AlbumPagingSource(filter = AlbumFilter.User(id = userId))
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

    fun togglePublic(albumId: String, isPublic: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            flow {
                val albumRef = db.collection("albums").document(albumId)
                emit(albumRef.update("isPublic", !isPublic).await())
            }
                .catch { exception ->
                    Log.e("TAG", "에러", exception)
                }
                .collect {
                    viewEvent(MyAlbumTabViewEvent.Refresh)
                }
        }
    }

    fun deletePublic(albumId: String, userId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            flow {
                // 먼저 삭제할 문서들의 참조를 모두 가져옵니다
                val albumRef = db.collection("albums").document(albumId)

                val userRef = db.collection("users").document(userId)

                // 관련 문서들의 참조를 모두 가져옵니다
                val memoryDocs = db.collection("memories")
                    .whereEqualTo("albumRef", albumRef)
                    .get().await().documents

                val locationDocs = db.collection("locations")
                    .whereEqualTo("albumRef", albumRef)
                    .get().await().documents

                val scrapDocs = db.collection("scraps")
                    .whereEqualTo("albumRef", albumRef)
                    .get().await().documents

                // 이제 트랜잭션에서 모든 문서를 삭제합니다
                db.runTransaction { transaction ->
                    transaction.update(userRef, "scrapCount", FieldValue.increment(-scrapDocs.size.toLong()))

                    // 앨범 삭제
                    transaction.delete(albumRef)

                    // 연관된 모든 문서 삭제
                    memoryDocs.forEach { doc ->
                        transaction.delete(doc.reference)
                    }

                    locationDocs.forEach { doc ->
                        transaction.delete(doc.reference)
                    }

                    scrapDocs.forEach { doc ->
                        transaction.delete(doc.reference)
                    }
                }.await()

                emit(Unit)
            }
                .catch { exception ->
                    Log.e("TAG", "에러", exception)
                }
                .collect {
                    viewEvent(MyAlbumTabViewEvent.RefreshAll)
                }
        }
    }
}