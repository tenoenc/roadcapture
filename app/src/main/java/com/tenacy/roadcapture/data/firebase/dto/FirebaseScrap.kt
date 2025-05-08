package com.tenacy.roadcapture.data.firebase.dto

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import java.time.LocalDateTime

@Parcelize
data class FirebaseScrap(
    val id: String = "", // Firestore 문서 ID
    val albumRefId: String = "", // 스크랩한 앨범 ID
    val userRefId: String = "", // 스크랩한 사용자 ID
    val createdAt: LocalDateTime, // 스크랩한 시간
): Parcelable