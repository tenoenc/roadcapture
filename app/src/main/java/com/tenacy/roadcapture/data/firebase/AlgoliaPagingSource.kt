package com.tenacy.roadcapture.data.firebase

import android.util.Log
import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.tenacy.roadcapture.manager.AlgoliaManager
import com.tenacy.roadcapture.ui.dto.Album

class AlgoliaPagingSource(
    private val algoliaManager: AlgoliaManager,
    private val query: String,
    private val filter: AlbumFilter,
) : PagingSource<Int, Album>() {

    companion object {
        const val PAGE_SIZE = 3
        private const val TAG = "AlgoliaPagingSource"
    }

    override fun getRefreshKey(state: PagingState<Int, Album>): Int? {
        return null
    }

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, Album> {
        return try {
            val page = params.key ?: 0
            val loadSize = params.loadSize

            Log.d(TAG, "로드 시작: page=$page, loadSize=$loadSize")

            val albums = algoliaManager.searchAndFetchAlbums(
                query = query,
                filter = filter,
                page = page,
                loadSize = loadSize,
            )

            Log.d(TAG, "로드 결과: ${albums.size}개 항목 받음")

            val endOfPaginationReached = albums.isEmpty()

            LoadResult.Page(
                data = albums,
                prevKey = if (page == 0) null else page - 1,
                nextKey = if (endOfPaginationReached) null else page + 1
            )
        } catch (e: Exception) {
            Log.e(TAG, "로드 실패", e)
            LoadResult.Error(e)
        }
    }
}