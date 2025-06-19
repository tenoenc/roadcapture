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
import com.tenacy.roadcapture.data.firebase.exception.AlbumLockedException
import com.tenacy.roadcapture.data.firebase.exception.SystemConfigException
import com.tenacy.roadcapture.ui.dto.Album
import com.tenacy.roadcapture.util.*
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

    // 페이징 소스 팩토리를 변수로 분리하여 항상 새로운 인스턴스 생성
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

    val albums: Flow<PagingData<Album>> = pager.flow
        .flowOn(Dispatchers.IO)
        .cachedIn(viewModelScope)

    fun updateAlbumPublic(albumId: String, isPublic: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            flow {
                // [VALIDATE_SYSTEM_CONFIG]
                validateSystemConfigV2()
                val albumRef = db.collection(FirebaseConstants.COLLECTION_ALBUMS).document(albumId)
                val album = albumRef.get().await().toAlbum()
                if(album.isLocked) {
                    throw AlbumLockedException(album.lockReason, album.lockedAt!!)
                }
                UpdateAlbumPublicWorker.enqueueOneTimeWork(context, albumId, isPublic)
                emit(Unit)
            }
                .catch { exception ->
                    Log.e("MyAlbumTabViewModel", "에러", exception)
                    // [VALIDATE_SYSTEM_CONFIG]
                    when(exception) {
                        is SystemConfigException -> {
                            handleSystemConfigException(exception)
                        }
                        is AlbumLockedException -> {
                            viewEvent(MyAlbumTabViewEvent.ShowAlbumLocked(exception.lockReason, exception.lockedAt))
                        }
                    }
                }
                .collect {
                    val publicText = if(isPublic) resourceProvider.getString(R.string.visibility_public) else resourceProvider.getString(R.string.visibility_private)
                    viewEvent(MyAlbumTabViewEvent.EnqueueComplete.UpdateAlbumPublic(publicText))
                }
        }
    }

    fun deleteAlbum(userId: String, albumId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            flow {
                // [VALIDATE_SYSTEM_CONFIG]
                validateSystemConfigV2()
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
                validateSystemConfigV2()
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