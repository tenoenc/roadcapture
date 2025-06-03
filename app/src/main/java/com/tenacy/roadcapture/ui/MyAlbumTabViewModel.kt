package com.tenacy.roadcapture.ui

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.google.firebase.firestore.FirebaseFirestoreException
import com.tenacy.roadcapture.data.firebase.AlbumFilter
import com.tenacy.roadcapture.data.firebase.AlbumPagingSource
import com.tenacy.roadcapture.ui.dto.Album
import com.tenacy.roadcapture.util.RetrofitInstance
import com.tenacy.roadcapture.util.functions
import com.tenacy.roadcapture.util.user
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

    fun generateShareLink(albumId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            flow {
                val idToken = user!!.getIdToken(false).await().token ?: throw Exception("토큰을 가져올 수 없습니다.")
                val response = RetrofitInstance.firebaseApi.shareAlbum(
                    authToken = "Bearer $idToken",
                    albumId = albumId,
                )
                if(!response.isSuccessful) {
                    throw Exception(response.errorBody()?.string())
                }
                val responseDto = response.body() ?: throw Exception()
                emit(responseDto.shareLink)
            }
                .catch { excpetion ->
                    Log.e("MyAlbumTabViewModel", "에러", excpetion)
                    viewEvent(MyAlbumTabViewEvent.Error.GenerateShareLink("공유 링크를 생성하는 중\n 문제가 발생했어요"))
                }
                .collect {
                    viewEvent(MyAlbumTabViewEvent.ShareComplete(it))
                }
        }
    }
}