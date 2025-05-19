package com.tenacy.roadcapture.data.firebase

import android.util.Log
import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.Query
import com.tenacy.roadcapture.ui.dto.Memory
import com.tenacy.roadcapture.util.db
import com.tenacy.roadcapture.util.toMemory
import kotlinx.coroutines.tasks.await

class MemoryPagingSource(
    private val filter: MemoryFilter,
): PagingSource<DocumentSnapshot, Memory>() {

    companion object {
        const val PAGE_SIZE = 10
        private const val TAG = "MemoryPagingSource"
    }

    override fun getRefreshKey(state: PagingState<DocumentSnapshot, Memory>): DocumentSnapshot? {
        Log.d(TAG, "getRefreshKey 호출됨")

        // null을 반환하여 새로고침 시 처음부터 다시 로드하도록 함
        return null

        // 또는 이전 구현을 사용하지만 초기 상태를 명확히 함
        // return state.anchorPosition?.let { anchorPosition ->
        //     state.closestPageToPosition(anchorPosition)?.prevKey
        // }
    }

    override suspend fun load(params: LoadParams<DocumentSnapshot>): LoadResult<DocumentSnapshot, Memory> {
        return try {
            // 로드 타입에 따른 로그 출력
            when (params) {
                is LoadParams.Refresh -> {
                    Log.d(TAG, "REFRESH 로드 요청: key=${params.key}, loadSize=${params.loadSize}")
                }
                is LoadParams.Append -> {
                    Log.d(TAG, "APPEND 로드 요청: key=${params.key}, loadSize=${params.loadSize}")
                }
                is LoadParams.Prepend -> {
                    Log.d(TAG, "PREPEND 로드 요청: key=${params.key}, loadSize=${params.loadSize}")
                    // Prepend는 지원하지 않으므로 빈 리스트 반환
                    return LoadResult.Page(
                        data = emptyList(),
                        prevKey = null,
                        nextKey = null
                    )
                }
            }


            when(filter) {
                is MemoryFilter.User -> {
                    var query = db.collection("memories")
                        .orderBy("createdAt", Query.Direction.DESCENDING)

                    filter.isPublic?.let {
                        query = query.whereEqualTo("isPublic", it)
                    }

                    // 페이지 크기 제한
                    query = query.limit(PAGE_SIZE.toLong())

                    // 페이징 키 적용 (다음 페이지 시작 지점)
                    val key = params.key
                    if (key != null) {
                        query = query.startAfter(key)
                        Log.d(TAG, "startAfter 적용: ${key.id}")
                    } else {
                        Log.d(TAG, "처음부터 로드 (startAfter 없음)")
                    }

                    // 쿼리 실행
                    val memoriesSnapshot = query.get().await()
                    val memoryDocuments = memoriesSnapshot.documents
                    val memoryIds = memoryDocuments.map { it.id }
                    Log.d(TAG, "쿼리 결과: ${memoryDocuments.size}개 문서 로드됨")

                    // 결과가 비어있는지 확인
                    if (memoryDocuments.isEmpty()) {
                        Log.d(TAG, "쿼리 결과가 비어있음")
                        return LoadResult.Page(
                            data = emptyList(),
                            prevKey = null,
                            nextKey = null
                        )
                    }

                    // 다음 페이지 키 설정
                    val lastDocument = memoryDocuments.lastOrNull()
                    Log.d(TAG, "마지막 문서 ID: ${lastDocument?.id}")

                    val memories = memoryDocuments.map { doc ->
                        val memory = doc.toMemory()
                        Memory.of(memory)
                    }

                    memories.forEachIndexed { index, memory ->
                        Log.d(TAG, "로드된 추억[$index]: ID=${memory.id}")
                    }

                    LoadResult.Page(
                        data = memories,
                        prevKey = null, // 이전 페이지는 지원하지 않음
                        nextKey = lastDocument // 다음 페이지 키는 마지막 문서
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "데이터 로드 중 오류 발생: ${e.message}", e)
            LoadResult.Error(e)
        }
    }
}