package com.tenacy.roadcapture.data.firebase

import android.util.Log
import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.Query
import com.tenacy.roadcapture.data.firebase.dto.FirebaseAlbum
import com.tenacy.roadcapture.util.db
import com.tenacy.roadcapture.util.toAlbum
import kotlinx.coroutines.tasks.await

class AlbumPagingSource(
    private val userRef: DocumentReference? = null,
    private val isPublicOnly: Boolean = false,
): PagingSource<DocumentSnapshot, FirebaseAlbum>() {

    companion object {
        const val PAGE_SIZE = 3
        private const val TAG = "AlbumPagingSource"
    }

    override fun getRefreshKey(state: PagingState<DocumentSnapshot, FirebaseAlbum>): DocumentSnapshot? {
        Log.d(TAG, "getRefreshKey 호출됨")

        // null을 반환하여 새로고침 시 처음부터 다시 로드하도록 함
        return null

        // 또는 이전 구현을 사용하지만 초기 상태를 명확히 함
        // return state.anchorPosition?.let { anchorPosition ->
        //     state.closestPageToPosition(anchorPosition)?.prevKey
        // }
    }

    override suspend fun load(params: LoadParams<DocumentSnapshot>): LoadResult<DocumentSnapshot, FirebaseAlbum> {
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

            // 기본 쿼리 생성
            var query = db.collection("albums")
                .orderBy("createdAt", Query.Direction.DESCENDING)

            // 필터 조건 추가
            if(userRef != null) {
                query = query.whereEqualTo("userRef", userRef)
            }

            if (isPublicOnly) {
                query = query.whereEqualTo("isPublic", true)
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
            val querySnapshot = query.get().await()
            val documents = querySnapshot.documents
            Log.d(TAG, "쿼리 결과: ${documents.size}개 문서 로드됨")

            // 결과가 비어있는지 확인
            if (documents.isEmpty()) {
                Log.d(TAG, "쿼리 결과가 비어있음")
                return LoadResult.Page(
                    data = emptyList(),
                    prevKey = null,
                    nextKey = null
                )
            }

            // 다음 페이지 키 설정
            val lastDocument = documents.lastOrNull()
            Log.d(TAG, "마지막 문서 ID: ${lastDocument?.id}")

            // 결과 매핑 및 반환
            val userIds = documents.mapNotNull { doc ->
                doc.getDocumentReference("userRef")?.id
            }.distinct()

            val usersSnapshot = if (userIds.isNotEmpty()) {
                // whereIn은 최대 10개 값으로 제한되므로 필요한 경우 여러 쿼리로 분할해야 함
                val userChunks = userIds.chunked(10) // Firestore의 whereIn은 최대 10개 값으로 제한됨

                // 각 청크마다 쿼리 실행 후 결과 병합
                val userDocs = userChunks.flatMap { chunk ->
                    db.collection("users")
                        .whereIn(FieldPath.documentId(), chunk)
                        .get()
                        .await()
                        .documents
                }

                userDocs
            } else {
                emptyList()
            }

            val userMap = usersSnapshot.associate { doc ->
                doc.id to FirebaseAlbum.User(
                    id = doc.id,
                    name = doc.getString("displayName") ?: "",
                    photoUrl = doc.getString("photoUrl") ?: ""
                )
            }

            val albums = documents.map { doc ->
                val userRefId = doc.getDocumentReference("userRef")?.id
                val user = userMap[userRefId]
                doc.toAlbum(user!!)
            }

            // 앨범 ID 출력
            albums.forEachIndexed { index, album ->
                Log.d(TAG, "로드된 앨범[$index]: ID=${album.id}")
            }

            // 중요: 다음 페이지 키로 마지막 문서 반환
            LoadResult.Page(
                data = albums,
                prevKey = null, // 이전 페이지는 지원하지 않음
                nextKey = lastDocument // 다음 페이지 키는 마지막 문서
            )
        } catch (e: Exception) {
            Log.e(TAG, "데이터 로드 중 오류 발생: ${e.message}", e)
            LoadResult.Error(e)
        }
    }
}