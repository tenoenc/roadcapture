package com.tenacy.roadcapture.data.firebase

import android.util.Log
import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.Query
import com.tenacy.roadcapture.data.pref.UserPref
import com.tenacy.roadcapture.ui.dto.Album
import com.tenacy.roadcapture.util.FirebaseConstants
import com.tenacy.roadcapture.util.db
import com.tenacy.roadcapture.util.toAlbum
import com.tenacy.roadcapture.util.whereInWithFilters
import kotlinx.coroutines.tasks.await

class AlbumPagingSource(
    private val filter: AlbumFilter = AlbumFilter.All,
): PagingSource<DocumentSnapshot, Album>() {

    companion object {
        const val PAGE_SIZE = 20
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
                is AlbumFilter.All -> {
                    // 기존 ALL 필터 로직
                    // 기본 쿼리 생성
                    var query = db.collection(FirebaseConstants.COLLECTION_ALBUMS)
                        .orderBy("createdAt", Query.Direction.DESCENDING)

                    query = query.whereEqualTo("isPublic", true)

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
                    val albumDocuments = query.get().await().documents
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
                        val albumRefs = albumIds.map { db.collection(FirebaseConstants.COLLECTION_ALBUMS).document(it) }
                        val userRef = db.collection(FirebaseConstants.COLLECTION_USERS).document(UserPref.id)

                        val scrapRefs = db.collection(FirebaseConstants.COLLECTION_SCRAPS)
                            .whereInWithFilters("albumRef", albumRefs) { query ->
                                query.whereEqualTo("userRef", userRef)
                            }

                        scrapRefs.mapNotNull { it.getDocumentReference("albumRef")?.id }.toSet()
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

                is AlbumFilter.Scrap -> {
                    Log.d(TAG, "SCRAP 필터 로드 요청")

                    // 현재 사용자 참조
                    val userRef = db.collection(FirebaseConstants.COLLECTION_USERS).document(UserPref.id)

                    // 커서 기반 페이징을 위한 시작점 설정
                    val startAfterDoc = params.key

                    // 먼저 사용자의 스크랩 목록 가져오기 (createdAt 기준 정렬)
                    var scrapQuery = db.collection(FirebaseConstants.COLLECTION_REPORTS)
                        .whereEqualTo("userRef", userRef)
                        .whereEqualTo("albumPublic", true)
                        .orderBy("createdAt", Query.Direction.DESCENDING)
                        .limit(PAGE_SIZE.toLong())

                    // 시작점 설정
                    if (startAfterDoc != null) {
                        scrapQuery = scrapQuery.startAfter(startAfterDoc)
                    }

                    // 스크랩 문서 가져오기
                    val scrapSnapshot = scrapQuery.get().await()
                    val scrapDocs = scrapSnapshot.documents

                    if (scrapDocs.isEmpty()) {
                        Log.d(TAG, "스크랩 쿼리 결과가 비어있음")
                        return LoadResult.Page(
                            data = emptyList(),
                            prevKey = null,
                            nextKey = null
                        )
                    }

                    // 앨범 참조 추출
                    val albumRefs = scrapDocs.mapNotNull { it.getDocumentReference("albumRef") }

                    if (albumRefs.isEmpty()) {
                        Log.d(TAG, "유효한 앨범 참조가 없음")
                        return LoadResult.Page(
                            data = emptyList(),
                            prevKey = null,
                            nextKey = null
                        )
                    }

                    // 배치로 앨범 문서 가져오기
                    val albums = mutableListOf<Album>()
                    albumRefs.chunked(10).forEach { chunk ->
                        // whereIn으로 앨범 문서 가져오기
                        val albumDocs = db.collection(FirebaseConstants.COLLECTION_ALBUMS)
                            .whereIn(FieldPath.documentId(), chunk.map { it.id })
                            .get().await().documents

                        // albumId -> 앨범 객체 매핑 생성
                        val albumMap = albumDocs.associateBy { it.id }

                        // 스크랩 순서를 유지하며 앨범 객체 변환
                        chunk.forEach { albumRef ->
                            albumMap[albumRef.id]?.let { albumDoc ->
                                val album = albumDoc.toAlbum()
                                albums.add(Album.from(album, true)) // 스크랩된 상태로 설정
                            }
                        }
                    }

                    // 다음 키 설정 (마지막 스크랩 문서)
                    val nextKey = if (scrapDocs.size >= PAGE_SIZE) scrapDocs.last() else null

                    Log.d(TAG, "로드된 스크랩된 앨범: ${albums.size}개")
                    albums.forEachIndexed { index, album ->
                        Log.d(TAG, "스크랩된 앨범[$index]: ID=${album.id}")
                    }

                    LoadResult.Page(
                        data = albums,
                        prevKey = null,
                        nextKey = nextKey
                    )
                }

                is AlbumFilter.User -> {
                    // 기존 ALL 필터 로직
                    // 기본 쿼리 생성
                    var query = db.collection(FirebaseConstants.COLLECTION_ALBUMS)
                        .orderBy("createdAt", Query.Direction.DESCENDING)

                    val userRef = db.collection(FirebaseConstants.COLLECTION_USERS).document(filter.id)

                    filter.isPublic?.let {
                        query = query.whereEqualTo("isPublic", it)
                    }

                    query = query.whereEqualTo("userRef", userRef)

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
                        val albumRefs = albumIds.map { db.collection(FirebaseConstants.COLLECTION_ALBUMS).document(it) }

                        val scrapRefs = db.collection(FirebaseConstants.COLLECTION_SCRAPS)
                            .whereInWithFilters("albumRef", albumRefs) { query ->
                                query.whereEqualTo("userRef", userRef)
                            }

                        scrapRefs.mapNotNull { it.getDocumentReference("albumRef")?.id }.toSet()
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
            }
        } catch (e: Exception) {
            Log.e(TAG, "데이터 로드 중 오류 발생: ${e.message}", e)
            LoadResult.Error(e)
        }
    }
}