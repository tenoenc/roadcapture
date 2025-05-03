package com.tenacy.roadcapture.data.firebase

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
        const val PAGE_SIZE = 20
    }

    override fun getRefreshKey(state: PagingState<DocumentSnapshot, FirebaseAlbum>): DocumentSnapshot? {
        // 새로고침 키 정의 (일반적으로 가장 최근에 접근한 위치)
        return state.anchorPosition?.let { anchorPosition ->
            state.closestPageToPosition(anchorPosition)?.prevKey
                ?: state.closestPageToPosition(anchorPosition)?.nextKey
        }
    }

    override suspend fun load(params: LoadParams<DocumentSnapshot>): LoadResult<DocumentSnapshot, FirebaseAlbum> {
        return try {
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
            query = query.limit(params.loadSize.toLong())

            // 페이징 키 적용 (다음 페이지 시작 지점)
            val startAfterDocument = params.key
            if (startAfterDocument != null) {
                query = query.startAfter(startAfterDocument)
            }

            // 쿼리 실행
            val querySnapshot = query.get().await()
            val documents = querySnapshot.documents

            // 다음 페이지 키 설정
            val lastDocument = documents.lastOrNull()

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

            LoadResult.Page(
                data = albums,
                prevKey = null,
                nextKey = if (albums.size < params.loadSize) null else lastDocument
            )
        } catch (e: Exception) {
            LoadResult.Error(e)
        }
    }
}