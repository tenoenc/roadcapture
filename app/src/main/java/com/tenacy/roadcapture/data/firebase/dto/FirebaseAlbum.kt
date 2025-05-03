package com.tenacy.roadcapture.data.firebase.dto

import java.time.LocalDateTime

data class FirebaseAlbum(
    val id: String = "", // Firestore 문서 ID
    val title: String = "",
    val createdAt: LocalDateTime,
    val endedAt: LocalDateTime,
    val thumbnailUrl: String = "",
    val viewCount: Int = 0,
    val likeCount: Int = 0,
    val regionTags: List<Map<String, String>> = emptyList(),
    val user: User,
    val isPublic: Boolean = false,
    val locations: List<Location> = emptyList(),
    val memories: List<Memory> = emptyList()
) {
    // 위치 데이터 클래스
    data class Location(
        val id: String = "",
        val latitude: Double = 0.0,
        val longitude: Double = 0.0,
        val createdAt: LocalDateTime? = null
    )

    // 메모리 데이터 클래스
    data class Memory(
        val id: String = "",
        val content: String = "",
        val photoUrl: String = "",
        val photoName: String = "",
        val placeName: String = "",
        val addressTags: List<String> = emptyList(),
        val formattedAddress: String = "",
        val locationRefId: String = "",
        val createdAt: LocalDateTime? = null
    )

    data class User(
        val id: String = "",
        val name: String = "",
        val photoUrl: String = "",
    )
}