package com.tenacy.roadcapture.ui

import android.content.Context
import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.tenacy.roadcapture.R
import com.tenacy.roadcapture.data.firebase.AlbumFilter
import com.tenacy.roadcapture.data.firebase.AlbumPagingSource
import com.tenacy.roadcapture.data.firebase.exception.SystemConfigException
import com.tenacy.roadcapture.ui.dto.Album
import com.tenacy.roadcapture.util.ResourceProvider
import com.tenacy.roadcapture.util.handleSystemConfigException
import com.tenacy.roadcapture.util.user
import com.tenacy.roadcapture.util.validateSystemConfig
import com.tenacy.roadcapture.worker.CreateShareLinkWorker
import com.tenacy.roadcapture.worker.DeleteAlbumWorker
import com.tenacy.roadcapture.worker.UpdateAlbumPublicWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
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
    private val resourceProvider: ResourceProvider,
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

    fun updateAlbumPublic(albumId: String, isPublic: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            flow {
                // [VALIDATE_SYSTEM_CONFIG]
                validateSystemConfig()
                UpdateAlbumPublicWorker.enqueueOneTimeWork(context, albumId, isPublic)
                emit(Unit)
            }
                .catch { exception ->
                    Log.e("MyAlbumTabViewModel", "에러", exception)
                    // [VALIDATE_SYSTEM_CONFIG]
                    if(exception is SystemConfigException) {
                        handleSystemConfigException(exception)
                        return@catch
                    }
                }
                .collect {
                    val publicText = if(!isPublic) resourceProvider.getString(R.string.visibility_public) else resourceProvider.getString(R.string.visibility_private)
                    viewEvent(MyAlbumTabViewEvent.EnqueueComplete.UpdateAlbumPublic(publicText))
                }
        }
    }

    fun deleteAlbum(userId: String, albumId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            flow {
                // [VALIDATE_SYSTEM_CONFIG]
                validateSystemConfig()
                DeleteAlbumWorker.enqueueOneTimeWork(context, userId, albumId)
                emit(Unit)
            }
                .catch { exception ->
                    Log.e("MyAlbumTabViewModel", "에러", exception)
                    // [VALIDATE_SYSTEM_CONFIG]
                    if(exception is SystemConfigException) {
                        handleSystemConfigException(exception)
                        return@catch
                    }
                }
                .collect {
                    viewEvent(MyAlbumTabViewEvent.EnqueueComplete.DeleteAlbum)
                }
        }
    }

    fun createShareLink(albumId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            flow {
                // [VALIDATE_SYSTEM_CONFIG]
                validateSystemConfig()
                val idToken = user!!.getIdToken(false).await().token ?: throw Exception("토큰을 가져올 수 없습니다.")
                CreateShareLinkWorker.enqueueOneTimeWork(context, albumId, idToken)
                emit(Unit)
            }
                .catch { exception ->
                    Log.e("MyAlbumTabViewModel", "에러", exception)
                    // [VALIDATE_SYSTEM_CONFIG]
                    if(exception is SystemConfigException) {
                        handleSystemConfigException(exception)
                        return@catch
                    }
                }
                .collect {
                    viewEvent(MyAlbumTabViewEvent.EnqueueComplete.CreateShareLink)
                }
        }
    }
}