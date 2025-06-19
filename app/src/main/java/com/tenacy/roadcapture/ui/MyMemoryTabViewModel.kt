package com.tenacy.roadcapture.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.tenacy.roadcapture.data.firebase.MemoryFilter
import com.tenacy.roadcapture.data.firebase.MemoryPagingSource
import com.tenacy.roadcapture.ui.dto.Memory
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import javax.inject.Inject

@HiltViewModel
class MyMemoryTabViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
) : BaseViewModel() {

    private val params: MyMemoryTabFragment.ParamsIn? = savedStateHandle.get<MyMemoryTabFragment.ParamsIn>(MyMemoryTabFragment.KEY_PARAMS)
    private val userId: String = params?.userId ?: ""

    // 페이징 소스 팩토리를 변수로 분리하여 항상 새로운 인스턴스 생성
    private val pagingSourceFactory = {
        MemoryPagingSource(filter = MemoryFilter.User(id = userId))
    }

    // 페이징 설정 최적화
    private val pager = Pager(
        config = PagingConfig(
            pageSize = MemoryPagingSource.PAGE_SIZE,
            enablePlaceholders = false,
            maxSize = MemoryPagingSource.PAGE_SIZE * 5,
            prefetchDistance = MemoryPagingSource.PAGE_SIZE,
            initialLoadSize = MemoryPagingSource.PAGE_SIZE
        ),
        pagingSourceFactory = pagingSourceFactory
    )

    // 추억 데이터 Flow
    val memories: Flow<PagingData<Memory>> = pager.flow
        .flowOn(Dispatchers.IO)
        .cachedIn(viewModelScope)
}