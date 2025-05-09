// com/tenacy/roadcapture/manager/AlgoliaManager.kt
package com.tenacy.roadcapture.manager

import android.util.Log
import com.algolia.search.saas.Client
import com.algolia.search.saas.Index
import com.algolia.search.saas.Query
import com.google.firebase.firestore.FieldPath
import com.tenacy.roadcapture.BuildConfig
import com.tenacy.roadcapture.data.firebase.SearchFilter
import com.tenacy.roadcapture.data.firebase.dto.SearchResponse
import com.tenacy.roadcapture.ui.dto.Album
import com.tenacy.roadcapture.util.db
import com.tenacy.roadcapture.util.toAlbum
import com.tenacy.roadcapture.util.user
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@Singleton
class AlgoliaManager @Inject constructor() {

    private val client: Client by lazy {
        Client(
            BuildConfig.ALGOLIA_APP_ID,
            BuildConfig.ALGOLIA_API_KEY
        )
    }

    private val index: Index by lazy {
        client.getIndex("albums")
    }

    private suspend fun searchAlbums(
        query: String,
        page: Int = 0,
        hitsPerPage: Int = 20
    ): SearchResponse {
        return withContext(Dispatchers.IO) {
            val algoliaQuery = Query(query).apply {
                // 공개 앨범만 필터링
                filters = "isPublic:true"

                // 페이지 설정
                setPage(page)
                setHitsPerPage(hitsPerPage)
            }

            suspendCancellableCoroutine { continuation ->
                index.searchAsync(algoliaQuery, { jsonObject, error ->
                    if (error != null) {
                        continuation.resumeWithException(Exception(error.message))
                    } else {
                        val response = SearchResponse.fromJson(jsonObject!!)
                        continuation.resume(response)
                    }
                })
            }
        }
    }

    private suspend fun searchAllAlbum(query: String): List<JSONObject> {
        var currentPage = 0
        val hitsPerPage = 1000 // 최대값 사용
        val allResults = mutableListOf<JSONObject>()
        var hasMoreResults = true

        return withContext(Dispatchers.IO) {
            while (hasMoreResults) {
                val algoliaQuery = Query(query).apply {
                    page = currentPage
                    setHitsPerPage(hitsPerPage)
                }

                val results = suspendCancellableCoroutine { continuation ->
                    index.searchAsync(algoliaQuery, { jsonObject, error ->
                        if (error != null) {
                            continuation.resumeWithException(Exception(error.message))
                        } else {
                            val response = SearchResponse.fromJson(jsonObject!!)
                            continuation.resume(response)
                        }
                    })
                }

                allResults.addAll(results.hits)

                // 페이지 증가 및 계속 조회 여부 결정
                currentPage++
                hasMoreResults = results.hits.size == hitsPerPage &&
                        currentPage * hitsPerPage < results.nbHits
            }
            allResults
        }
    }

    suspend fun searchAndFetchAlbums(
        query: String,
        filter: SearchFilter,
        page: Int = 0,
        loadSize: Int = 0,
    ): List<Album> {
        return when(filter) {
            is SearchFilter.All -> {
                val response = searchAlbums(query, page, loadSize)
                // 로그 추가
                Log.d("AlgoliaManager", "검색 결과: 총 ${response.nbHits}개, 페이지 ${response.page}/${response.nbPages}")
                val albumIds = response.hits.map { hit -> hit.getString("objectID") }
                fetchAlbums(albumIds, loadSize)
            }
            is SearchFilter.Scrap -> {
                val hits = searchAllAlbum(query)
                val albumIds = hits.map { hit -> hit.getString("objectID") }
                fetchScrapedAlbums(albumIds, page, loadSize)
            }
        }
    }

    private suspend fun fetchAlbums(albumIds: List<String>, loadSize: Int): List<Album> {
        return withContext(Dispatchers.IO) {
            if (albumIds.isEmpty()) return@withContext emptyList()

            // 최대 10개씩 나누어 처리 (Firestore whereIn 제한)
            val chunkedAlbumIds = albumIds.take(loadSize).chunked(10)
            val allAlbums = mutableListOf<Album>()

            for (chunk in chunkedAlbumIds) {
                // Firebase에서 앨범 데이터 가져오기
                val albumsSnapshot = db.collection("albums")
                    .whereIn(FieldPath.documentId(), chunk)
                    .get()
                    .await()

                // 앨범 ID 순서 유지를 위한 매핑
                val albumMap = albumsSnapshot.documents.associate { doc ->
                    doc.id to doc.toAlbum()
                }

                // 스크랩 정보 가져오기
                val scrapedIds = if (chunk.isNotEmpty()) {
                    val scrapSnapshot = db.collection("scraps")
                        .whereIn("albumRef", chunk.map { db.collection("albums").document(it) })
                        .whereEqualTo("userRef", db.collection("users").document(user!!.uid))
                        .get()
                        .await()

                    scrapSnapshot.documents
                        .mapNotNull { it.getDocumentReference("albumRef")?.id }
                        .toSet()
                } else {
                    emptySet()
                }

                // 결과 매핑
                val chunkAlbums = chunk.mapNotNull { albumId ->
                    val album = albumMap[albumId]

                    if (album != null) {
                        Album.from(album, scrapedIds.contains(albumId))
                    } else {
                        null
                    }
                }

                allAlbums.addAll(chunkAlbums)
            }

            // 원래 albumIds 순서대로 결과 재정렬
            val albumIdToIndex = albumIds.withIndex().associate { it.value to it.index }
            allAlbums.sortedBy { albumIdToIndex[it.id] ?: Int.MAX_VALUE }
        }
    }

    private suspend fun fetchScrapedAlbums(albumIds: List<String>, page: Int, loadSize: Int): List<Album> {
        return withContext(Dispatchers.IO) {
            if (albumIds.isEmpty()) return@withContext emptyList()

            // 스크랩된 앨범 ID 캐시 (메모리 또는 로컬 DB에 저장)
            val scrapedAlbumIds = mutableListOf<String>()
            // 현재까지 처리된 앨범 수
            var processedAlbums = 0
            // 현재 페이지에 필요한 스크랩된 앨범 수
            val neededAlbums = loadSize
            // 최종 결과
            val resultAlbums = mutableListOf<Album>()
            // 시작 인덱스 (이전 페이지에서 처리한 스크랩된 앨범 수)
            val startScrapedIndex = page * loadSize

            // 앨범 ID를 10개씩 나누어 처리
            val chunkedAlbumIds = albumIds.chunked(10)

            // 각 청크를 순회하며 필요한 개수의 스크랩된 앨범을 찾을 때까지 처리
            for (chunk in chunkedAlbumIds) {
                if (resultAlbums.size >= neededAlbums) break

                // 스크랩 정보 먼저 확인
                val scrapedIds = if (chunk.isNotEmpty()) {
//                    val scrapSnapshot = db.collection("scraps")
//                        .whereIn("albumRef", chunk.map { db.collection("albums").document(it) })
//                        .whereEqualTo("userRef", db.collection("users").document(user!!.uid))
//                        .get()
//                        .await()

//                scrapSnapshot.documents
//                    .mapNotNull { it.getDocumentReference("albumRef")?.id }
//                    .toSet()

                    val scrapSnapshot = db.collection("users").document(user!!.uid)
                        .collection("scraps")
                        .whereIn(FieldPath.documentId(), chunk)
                        .get()
                        .await()

                    scrapSnapshot.documents
                        .mapNotNull { it.id }
                        .toSet()

                } else {
                    emptySet()
                }

                // 스크랩된 앨범이 없으면 다음 청크로
                if (scrapedIds.isEmpty()) continue

                // 스크랩된 앨범 ID 캐시에 추가
                scrapedAlbumIds.addAll(scrapedIds)

                // 현재 페이지에 필요한 스크랩된 앨범만 가져오기
                val scrapedChunkIds = chunk.filter { scrapedIds.contains(it) }

                // 이전 페이지의 스크랩된 앨범은 건너뛰기
                val skipCount = maxOf(0, startScrapedIndex - processedAlbums)
                processedAlbums += scrapedChunkIds.size

                // 현재 페이지에 해당하는 스크랩된 앨범만 처리
                val pageScrapedIds = if (skipCount >= scrapedChunkIds.size) {
                    emptyList()
                } else {
                    scrapedChunkIds.drop(skipCount).take(neededAlbums - resultAlbums.size)
                }

                if (pageScrapedIds.isEmpty()) continue

                // Firebase에서 필요한 앨범 데이터만 가져오기
                val albumsSnapshot = db.collection("albums")
                    .whereIn(FieldPath.documentId(), pageScrapedIds)
                    .get()
                    .await()

                // 앨범 매핑 및 결과 추가
                val pageAlbums = albumsSnapshot.documents.mapNotNull { doc ->
                    val albumId = doc.id
                    val album = doc.toAlbum()
                    if (album != null) {
                        Album.from(album, true) // 스크랩된 앨범만 가져오므로 항상 true
                    } else {
                        null
                    }
                }

                resultAlbums.addAll(pageAlbums)
            }

            // 스크랩된 앨범 ID 순서대로 결과 정렬
            val scrapedIdToIndex = scrapedAlbumIds.withIndex().associate { it.value to it.index }
            resultAlbums.sortedBy { scrapedIdToIndex[it.id] ?: Int.MAX_VALUE }
        }
    }
}