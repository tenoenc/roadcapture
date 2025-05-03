package com.tenacy.roadcapture.ui

import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.cachedIn
import com.tenacy.roadcapture.data.firebase.AlbumPagingSource
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(

) : BaseViewModel() {

    val albums = Pager(
        config = PagingConfig(
            pageSize = AlbumPagingSource.PAGE_SIZE,
            enablePlaceholders = false,
            maxSize = 100,
        ),
        pagingSourceFactory = { AlbumPagingSource(isPublicOnly = true) }
    ).flow.cachedIn(viewModelScope)
}