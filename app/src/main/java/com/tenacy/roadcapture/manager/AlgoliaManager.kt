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
import com.tenacy.roadcapture.data.pref.UserPref
import com.tenacy.roadcapture.ui.dto.Album
import com.tenacy.roadcapture.util.db
import com.tenacy.roadcapture.util.toAlbum
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

    private val albumIndex: Index by lazy {
        client.getIndex("albums")
    }

    private val scrapIndex: Index by lazy {
        client.getIndex("scraps")
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
                albumIndex.searchAsync(algoliaQuery, { jsonObject, error ->
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

    private suspend fun searchScraps(
        query: String,
        page: Int = 0,
        hitsPerPage: Int = 20
    ): SearchResponse {
        return withContext(Dispatchers.IO) {
            val algoliaQuery = Query(query).apply {
                // 공개 앨범만 필터링
                filters = "albumPublic:true AND userRef:users/${UserPref.id}"

                // 페이지 설정
                setPage(page)
                setHitsPerPage(hitsPerPage)
            }

            suspendCancellableCoroutine { continuation ->
                scrapIndex.searchAsync(algoliaQuery, { jsonObject, error ->
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
                    albumIndex.searchAsync(algoliaQuery, { jsonObject, error ->
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
                fetchAlbums(albumIds)
            }
            is SearchFilter.Scrap -> {
                val response = searchScraps(query, page, loadSize)
                Log.d("AlgoliaManager", "검색 결과: 총 ${response.nbHits}개, 페이지 ${response.page}/${response.nbPages}")
                val scrapsId = response.hits.map { hit -> hit.getString("objectID") }
                fetchScrapedAlbums(scrapsId)
            }
        }
    }

    private suspend fun fetchAlbums(albumIds: List<String>): List<Album> {
        return withContext(Dispatchers.IO) {
            if (albumIds.isEmpty()) return@withContext emptyList()

            // 최대 10개씩 나누어 처리 (Firestore whereIn 제한)
            val chunkedAlbumIds = albumIds.chunked(10)
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
                        .whereEqualTo("userRef", db.collection("users").document(UserPref.id))
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

    private suspend fun fetchScrapedAlbums(scrapIds: List<String>): List<Album> {
        return withContext(Dispatchers.IO) {
            if (scrapIds.isEmpty()) return@withContext emptyList()

            // 최대 10개씩 나누어 처리 (Firestore whereIn 제한)
            val chunkedScrapIds = scrapIds.chunked(10)
            val allAlbums = mutableListOf<Album>()

            for (chunk in chunkedScrapIds) {
                // Firebase에서 앨범 데이터 가져오기
                val scrapsSnapshot = db.collection("scraps")
                    .whereIn(FieldPath.documentId(), chunk)
                    .get()
                    .await()

                val albumIds = scrapsSnapshot.documents.mapNotNull { it.getDocumentReference("albumRef")?.id }
                val chunkAlbums = db.collection("albums")
                    .whereIn(FieldPath.documentId(), albumIds)
                    .get().await()
                    .map { Album.from(it.toAlbum(), true) }

                allAlbums.addAll(chunkAlbums)
            }

            allAlbums.sortedByDescending { it.createdAt }
        }
    }
}