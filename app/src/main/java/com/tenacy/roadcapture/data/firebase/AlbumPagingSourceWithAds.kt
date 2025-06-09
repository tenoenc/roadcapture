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
import com.tenacy.roadcapture.ui.dto.AlbumItemWithAds
import com.tenacy.roadcapture.util.FirebaseConstants
import com.tenacy.roadcapture.util.db
import com.tenacy.roadcapture.util.toAlbum
import com.tenacy.roadcapture.util.whereInWithFilters
import kotlinx.coroutines.tasks.await
import kotlin.random.Random

class AlbumPagingSourceWithAds(
    private val filter: AlbumFilter = AlbumFilter.All,
    private val minAdPosition: Int = 3,      // 첫 광고가 나타날 수 있는 최소 위치
    private val maxAdPosition: Int = 5,      // 첫 광고가 나타날 수 있는 최대 위치
    private val minAdInterval: Int = 4,      // 광고 간격 최소값
    private val maxAdInterval: Int = 8,      // 광고 간격 최대값
    private val adDensity: Float = 0.4f,     // 광고 밀도 (0.0 ~ 1.0, 높을수록 광고가 더 자주 등장)
    private val showAds: Boolean = true      // 구독 상태를 전달받는 파라미터
) : PagingSource<DocumentSnapshot, AlbumItemWithAds>() {

    // 랜덤 간격을 계산할 난수 생성기
    private val random = Random(System.currentTimeMillis())

    // 광고 위치를 저장할 캐시 (같은 데이터 로드에 대해 일관된 광고 위치 유지)
    private val adPositionsCache = mutableListOf<Int>()

    companion object {
        // 광고가 포함될 예정이므로 페이지 크기를 약간 늘려 효율성 확보
        const val PAGE_SIZE = 5
        private const val TAG = "AlbumPagingSource"
    }

    // refresh 시 광고 위치 재설정을 위해 override
    override fun getRefreshKey(state: PagingState<DocumentSnapshot, AlbumItemWithAds>): DocumentSnapshot? {
        // 새로고침 시 광고 위치 캐시 초기화
        adPositionsCache.clear()
        return null
    }

    override suspend fun load(params: LoadParams<DocumentSnapshot>): LoadResult<DocumentSnapshot, AlbumItemWithAds> {
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

            // 필터에 따라 앨범 로드
            val albumsResult = when (filter) {
                is AlbumFilter.All -> loadAllAlbums(params.key)
                is AlbumFilter.Scrap -> loadScrapAlbums(params.key)
                is AlbumFilter.User -> loadUserAlbums(filter, params.key)
            }

            if (albumsResult.albums.isEmpty()) {
                return LoadResult.Page(
                    data = emptyList(),
                    prevKey = null,
                    nextKey = null
                )
            }

            // 앨범과 광고를 혼합한 리스트 생성
            val mixedItems = createMixedList(albumsResult.albums)

            LoadResult.Page(
                data = mixedItems,
                prevKey = null,
                nextKey = albumsResult.nextKey
            )
        } catch (e: Exception) {
            Log.e(TAG, "데이터 로드 중 오류 발생: ${e.message}", e)
            LoadResult.Error(e)
        }
    }

    // 앨범 및 광고 아이템 혼합 리스트 생성 함수 수정
    private fun createMixedList(albums: List<Album>): List<AlbumItemWithAds> {
        val result = mutableListOf<AlbumItemWithAds>()

        // 광고 위치 계산 (캐시가 비어있을 경우에만)
        if (adPositionsCache.isEmpty() && showAds) {
            generateAdPositions(albums.size)
        }

        albums.forEachIndexed { index, album ->
            // 앨범 항목 추가
            when(filter) {
                AlbumFilter.All, AlbumFilter.Scrap -> result.add(AlbumItemWithAds.Album.General(album))
                is AlbumFilter.User -> result.add(AlbumItemWithAds.Album.User(album))
            }

            // 구독자가 아니고 현재 위치가 광고 위치인 경우 광고 추가
            if (showAds && adPositionsCache.contains(index)) {
                result.add(AlbumItemWithAds.Ad(position = index))
            }
        }

        return result
    }

    // 광고 위치를 생성하는 함수
    private fun generateAdPositions(totalItems: Int) {
        // 이미 위치가 생성된 경우 중복 생성 방지
        if (adPositionsCache.isNotEmpty()) return

        // 광고 밀도에 따른 광고 수 계산
        val maxPossibleAds = (totalItems / minAdInterval.toFloat()).toInt()
        val targetAdCount = (maxPossibleAds * adDensity).toInt().coerceAtLeast(1)

        // 첫 번째 광고 위치 결정
        val firstAdPos = random.nextInt(maxAdPosition - minAdPosition + 1) + minAdPosition
        adPositionsCache.add(firstAdPos)

        // 나머지 광고 위치 결정
        var currentPos = firstAdPos
        for (i in 1 until targetAdCount) {
            // 다음 광고 간격 랜덤으로 결정
            val interval = random.nextInt(maxAdInterval - minAdInterval + 1) + minAdInterval
            currentPos += interval

            // 전체 아이템 수를 초과하지 않는 경우에만 추가
            if (currentPos < totalItems) {
                adPositionsCache.add(currentPos)
            } else {
                break
            }
        }

        Log.d(TAG, "생성된 광고 위치: $adPositionsCache")
    }

    // 모든 앨범 로드 로직
    private suspend fun loadAllAlbums(startAfterDoc: DocumentSnapshot?): AlbumQueryResult {
        // 기본 쿼리 생성
        var query = db.collection(FirebaseConstants.COLLECTION_ALBUMS)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .whereEqualTo("isPublic", true)
            .limit(PAGE_SIZE.toLong())

        // 페이징 키 적용
        if (startAfterDoc != null) {
            query = query.startAfter(startAfterDoc)
        }

        // 쿼리 실행
        val albumDocuments = query.get().await().documents
        val albumIds = albumDocuments.map { it.id }

        if (albumDocuments.isEmpty()) {
            return AlbumQueryResult(emptyList(), null)
        }

        // 스크랩 상태 확인
        val scrapedByAlbumId = getScrapedAlbumIds(albumIds)

        // 앨범 객체 변환
        val albums = albumDocuments.map { doc ->
            val album = doc.toAlbum()
            Album.from(album, scrapedByAlbumId.contains(album.id))
        }

        // 다음 페이지의 광고 위치도 미리 계산해둠
        if (showAds && adPositionsCache.isEmpty()) {
            // 현재 페이지 + 다음 페이지의 아이템 수를 기준으로 광고 위치 미리 계산
            generateAdPositions(albums.size * 2)
        }

        return AlbumQueryResult(albums, albumDocuments.lastOrNull())
    }

    // 스크랩된 앨범 로드 로직
    private suspend fun loadScrapAlbums(startAfterDoc: DocumentSnapshot?): AlbumQueryResult {
        // 현재 사용자 참조
        val userRef = db.collection(FirebaseConstants.COLLECTION_USERS).document(UserPref.id)

        // 스크랩 쿼리 생성
        var scrapQuery = db.collection(FirebaseConstants.COLLECTION_SCRAPS)
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
            return AlbumQueryResult(emptyList(), null)
        }

        // 앨범 참조 추출
        val albumRefs = scrapDocs.mapNotNull { it.getDocumentReference("albumRef") }

        if (albumRefs.isEmpty()) {
            return AlbumQueryResult(emptyList(), null)
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

        return AlbumQueryResult(albums, nextKey)
    }

    // 사용자별 앨범 로드 로직
    private suspend fun loadUserAlbums(filter: AlbumFilter.User, startAfterDoc: DocumentSnapshot?): AlbumQueryResult {
        // 기본 쿼리 생성
        var query = db.collection(FirebaseConstants.COLLECTION_ALBUMS)
            .orderBy("createdAt", Query.Direction.DESCENDING)

        val userRef = db.collection(FirebaseConstants.COLLECTION_USERS).document(filter.id)

        filter.isPublic?.let {
            query = query.whereEqualTo("isPublic", it)
        }

        query = query.whereEqualTo("userRef", userRef)
            .limit(PAGE_SIZE.toLong())

        // 페이징 키 적용
        if (startAfterDoc != null) {
            query = query.startAfter(startAfterDoc)
        }

        // 쿼리 실행
        val albumDocuments = query.get().await().documents
        val albumIds = albumDocuments.map { it.id }

        if (albumDocuments.isEmpty()) {
            return AlbumQueryResult(emptyList(), null)
        }

        // 스크랩 상태 확인
        val scrapedByAlbumId = getScrapedAlbumIds(albumIds, userRef)

        // 앨범 객체 변환
        val albums = albumDocuments.map { doc ->
            val album = doc.toAlbum()
            Album.from(album, scrapedByAlbumId.contains(album.id))
        }

        return AlbumQueryResult(albums, albumDocuments.lastOrNull())
    }

    // 앨범 ID 목록에 대한 스크랩 상태 조회
    private suspend fun getScrapedAlbumIds(albumIds: List<String>, userRef: DocumentReference? = null): Set<String> {
        if (albumIds.isEmpty()) return emptySet()

        // 앨범 참조 생성
        val albumRefs = albumIds.map { db.collection(FirebaseConstants.COLLECTION_ALBUMS).document(it) }

        // 사용자 참조 (지정되지 않은 경우 현재 사용자)
        val userReference = userRef ?: db.collection(FirebaseConstants.COLLECTION_USERS).document(UserPref.id)

        // 스크랩 쿼리 실행
        val scrapRefs = db.collection(FirebaseConstants.COLLECTION_SCRAPS)
            .whereInWithFilters("albumRef", albumRefs) { query ->
                query.whereEqualTo("userRef", userReference)
            }

        // 스크랩된 앨범 ID 추출
        return scrapRefs.mapNotNull { it.getDocumentReference("albumRef")?.id }.toSet()
    }

    // 앨범 쿼리 결과를 저장하는 내부 클래스
    private data class AlbumQueryResult(
        val albums: List<Album>,
        val nextKey: DocumentSnapshot?
    )
}