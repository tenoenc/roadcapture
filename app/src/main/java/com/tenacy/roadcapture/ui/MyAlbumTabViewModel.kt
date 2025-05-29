package com.tenacy.roadcapture.ui

import android.content.Context
import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.work.ExistingWorkPolicy
import androidx.work.WorkManager
import com.google.firebase.firestore.FieldValue
import com.tenacy.roadcapture.data.firebase.AlbumFilter
import com.tenacy.roadcapture.data.firebase.AlbumPagingSource
import com.tenacy.roadcapture.ui.dto.Album
import com.tenacy.roadcapture.util.*
import com.tenacy.roadcapture.worker.DeleteAlbumWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MyAlbumTabViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    @ApplicationContext private val context: Context,
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
                val scrapRefs = db.collection("scraps")
                    .whereEqualTo("albumRef", albumRef).getAllReferences()

                val allOperations = mutableListOf<BatchOperation>()
                scrapRefs.forEach {
                    allOperations.add(UpdateDocumentOperation(it, mapOf("albumPublic" to !isPublic)))
                }
                allOperations.add(UpdateDocumentOperation(albumRef, mapOf("isPublic" to !isPublic)))
                db.executeInBatches(allOperations)

                emit(Unit)
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
                val memoryRefs = db.collection("memories")
                    .whereEqualTo("albumRef", albumRef).getAllReferences()

                val locationRefs = db.collection("locations")
                    .whereEqualTo("albumRef", albumRef).getAllReferences()

                val scrapRefs = db.collection("scraps")
                    .whereEqualTo("albumRef", albumRef).getAllReferences()

                val allOperations = mutableListOf<BatchOperation>()
                allOperations.add(UpdateDocumentOperation(userRef, mapOf("scrapCount" to FieldValue.increment(-scrapRefs.size.toLong()))))
                allOperations.add(DeleteDocumentOperation(albumRef))
                memoryRefs.forEach { ref ->
                    allOperations.add(DeleteDocumentOperation(ref))
                }
                locationRefs.forEach { ref ->
                    allOperations.add(DeleteDocumentOperation(ref))
                }
                scrapRefs.forEach { ref ->
                    allOperations.add(DeleteDocumentOperation(ref))
                }
                db.executeInBatches(allOperations)

                DeleteAlbumWorker.enqueueOneTimeWork(context, userId, albumId)

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