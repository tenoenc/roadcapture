package com.tenacy.roadcapture.data.firebase

import android.util.Log
import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.Query
import com.tenacy.roadcapture.ui.dto.Album
import com.tenacy.roadcapture.util.db
import com.tenacy.roadcapture.util.toAlbum
import com.tenacy.roadcapture.util.toUser
import com.tenacy.roadcapture.util.user
import kotlinx.coroutines.tasks.await

class AlbumPagingSource(
    private val userRef: DocumentReference? = null,
    private val isPublicOnly: Boolean = false,
    private val filter: AlbumFilter = AlbumFilter.ALL,
): PagingSource<DocumentSnapshot, Album>() {

    companion object {
        const val PAGE_SIZE = 3
        private const val TAG = "AlbumPagingSource"
    }

    override fun getRefreshKey(state: PagingState<DocumentSnapshot, Album>): DocumentSnapshot? {
        Log.d(TAG, "getRefreshKey 호출됨")

        // null을 반환하여 새로고침 시 처음부터 다시 로드하도록 함
        return null

        // 또는 이전 구현을 사용하지만 초기 상태를 명확히 함
        // return state.anchorPosition?.let { anchorPosition ->
        //     state.closestPageToPosition(anchorPosition)?.prevKey
        // }
    }

    override suspend fun load(params: LoadParams<DocumentSnapshot>): LoadResult<DocumentSnapshot, Album> {
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

            // 필터 타입에 따라 다른 로직 적용
            when (filter) {
                AlbumFilter.ALL -> {
                    // 기존 ALL 필터 로직
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
                    val albumsSnapshot = query.get().await()
                    val albumDocuments = albumsSnapshot.documents
                    val albumIds = albumDocuments.map { it.id }
                    Log.d(TAG, "쿼리 결과: ${albumDocuments.size}개 문서 로드됨")

                    // 결과가 비어있는지 확인
                    if (albumDocuments.isEmpty()) {
                        Log.d(TAG, "쿼리 결과가 비어있음")
                        return LoadResult.Page(
                            data = emptyList(),
                            prevKey = null,
                            nextKey = null
                        )
                    }

                    // 다음 페이지 키 설정
                    val lastDocument = albumDocuments.lastOrNull()
                    Log.d(TAG, "마지막 문서 ID: ${lastDocument?.id}")

                    val scrapedByAlbumId = if (albumIds.isNotEmpty()) {
                        // 특정 앨범들에 대한 스크랩 상태만 조회
                        val scrapQuery = db.collection("scraps")
                            .whereIn("albumRef", albumIds.map { db.collection("albums").document(it) })
                            .whereEqualTo("userRef", db.collection("users").document(user!!.uid))

                        val scrapSnapshot = scrapQuery.get().await()
                        scrapSnapshot.documents.mapNotNull { doc ->
                            val albumRef = doc.getDocumentReference("albumRef")
                            albumRef?.id
                        }.toSet()
                    } else {
                        emptySet()
                    }

                    val albums = albumDocuments.map { doc ->
                        val album = doc.toAlbum()
                        Album.from(album, scrapedByAlbumId.contains(album.id))
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
                }

                AlbumFilter.SCRAP -> {
                    // SCRAP 필터 로직 구현
                    Log.d(TAG, "SCRAP 필터 로드 요청")

                    // 현재 사용자의 스크랩 문서 가져오기
                    val userDocument = db.collection("users").document(user!!.uid)

                    // 스크랩 쿼리 기본 설정 (정렬 없이 먼저 가져옴)
                    var scrapQuery = db.collection("scraps")
                        .whereEqualTo("userRef", userDocument)
                        .limit(PAGE_SIZE.toLong() * 3) // 충분한 스크랩을 가져오기 위해 더 큰 limit 설정

                    // 페이징 키 적용
                    val key = params.key
                    if (key != null) {
                        // 여기서 key는 앨범 문서임
                        // 해당 앨범의 생성 시간을 가져와서 그보다 이전 시간의 앨범들을 조회하도록 수정
                        val keyAlbumRef = db.collection("albums").document(key.id)
                        val keyAlbumDoc = keyAlbumRef.get().await()
                        val keyCreatedAt = keyAlbumDoc.getTimestamp("createdAt")

                        if (keyCreatedAt != null) {
                            // 쿼리를 직접 앨범에 적용 (나중에 스크랩만 필터링)
                            Log.d(TAG, "앨범 생성시간 이전 기준 적용: ${keyCreatedAt}")
                        } else {
                            Log.d(TAG, "키 문서에서 createdAt을 찾을 수 없음")
                        }
                    } else {
                        Log.d(TAG, "처음부터 로드 (startAfter 없음)")
                    }

                    // 스크랩 쿼리 실행
                    val scrapsSnapshot = scrapQuery.get().await()
                    val scrapDocuments = scrapsSnapshot.documents

                    // 결과가 비어있는지 확인
                    if (scrapDocuments.isEmpty()) {
                        Log.d(TAG, "스크랩 쿼리 결과가 비어있음")
                        return LoadResult.Page(
                            data = emptyList(),
                            prevKey = null,
                            nextKey = null
                        )
                    }

                    // 스크랩된 앨범 참조 목록 추출
                    val albumRefs = scrapDocuments.mapNotNull { doc ->
                        doc.getDocumentReference("albumRef")
                    }

                    // 앨범 참조가 없으면 빈 결과 반환
                    if (albumRefs.isEmpty()) {
                        Log.d(TAG, "스크랩된 앨범 참조가 없음")
                        return LoadResult.Page(
                            data = emptyList(),
                            prevKey = null,
                            nextKey = null
                        )
                    }

                    // 스크랩된 앨범 ID 집합
                    val scrapedAlbumIds = albumRefs.map { it.id }.toSet()

                    // 앨범 쿼리 생성 - 스크랩된 앨범들만 가져오되 createdAt 기준으로 정렬
                    var albumQuery = db.collection("albums")
                        .orderBy("createdAt", Query.Direction.DESCENDING)
                        .limit(PAGE_SIZE.toLong())

                    // 시작 지점 설정 (key가 있는 경우)
                    if (key != null) {
                        albumQuery = albumQuery.startAfter(key)
                    }

                    // isPublicOnly 필터 적용
                    if (isPublicOnly) {
                        albumQuery = albumQuery.whereEqualTo("isPublic", true)
                    }

                    // 앨범 쿼리 실행
                    val albumsSnapshot = albumQuery.get().await()
                    val albumDocuments = albumsSnapshot.documents

                    // 스크랩된 앨범들만 필터링
                    val filteredAlbumDocs = albumDocuments.filter { doc ->
                        scrapedAlbumIds.contains(doc.id)
                    }

                    // 결과가 비어있으면 다음 페이지 가져오기 시도
                    if (filteredAlbumDocs.isEmpty() && albumDocuments.isNotEmpty()) {
                        // 마지막 문서를 키로 사용하여 재귀적으로 다음 페이지 로드
                        val lastDoc = albumDocuments.last()
                        val nextParams = LoadParams.Append(lastDoc, PAGE_SIZE, false)
                        return load(nextParams)
                    }

                    // 앨범 변환
                    val albums = filteredAlbumDocs.map { doc ->
                        val album = doc.toAlbum()
                        Album.from(album, true) // 스크랩된 앨범
                    }

                    // 앨범 ID 출력
                    albums.forEachIndexed { index, album ->
                        Log.d(TAG, "스크랩된 앨범[$index]: ID=${album.id}, createdAt=${album.createdAt}")
                    }

                    // 다음 페이지 키 설정
                    val lastAlbumDoc = if (filteredAlbumDocs.isNotEmpty()) {
                        filteredAlbumDocs.last()
                    } else if (albumDocuments.isNotEmpty()) {
                        albumDocuments.last()
                    } else {
                        null
                    }

                    // 결과 반환
                    LoadResult.Page(
                        data = albums,
                        prevKey = null,
                        nextKey = lastAlbumDoc
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "데이터 로드 중 오류 발생: ${e.message}", e)
            LoadResult.Error(e)
        }
    }
}