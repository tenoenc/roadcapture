package com.tenacy.roadcapture.data.pref

import com.chibatching.kotpref.KotprefModel

object WorkPrefs : KotprefModel() {
    // 처리된 앨범 삭제 작업 ID 저장소
    val processedAlbumDeleteWorkIds by stringSetPref(default = setOf())

    // 처리된 사용자명 업데이트 작업 ID 저장소
    val processedUsernameUpdateWorkIds by stringSetPref(default = setOf())

    // 처리된 사용자명 업데이트 작업 ID 저장소
    val processedUserPhotoUpdateWorkIds by stringSetPref(default = setOf())

    // 처리된 사용자명 업데이트 작업 ID 저장소
    val processedAlbumPublicUpdateWorkIds by stringSetPref(default = setOf())

    // 마지막 작업 정리 시간 (pruneWork 호출 시간)
    var lastWorkPruneTime by longPref(default = 0L)

    // 작업 정리 함수
    fun addProcessedAlbumDeleteWorkId(workId: String) {
        val currentIds = processedAlbumDeleteWorkIds.toMutableSet()
        currentIds.add(workId)
        processedAlbumDeleteWorkIds.clear()
        processedAlbumDeleteWorkIds += currentIds
    }

    fun addProcessedUsernameUpdateWorkId(workId: String) {
        val currentIds = processedUsernameUpdateWorkIds.toMutableSet()
        currentIds.add(workId)
        processedUsernameUpdateWorkIds.clear()
        processedUsernameUpdateWorkIds += currentIds
    }

    fun addProcessedUserPhotoUpdateWorkId(workId: String) {
        val currentIds = processedUserPhotoUpdateWorkIds.toMutableSet()
        currentIds.add(workId)
        processedUserPhotoUpdateWorkIds.clear()
        processedUserPhotoUpdateWorkIds += currentIds
    }

    fun addProcessedAlbumPublicUpdateWorkId(workId: String) {
        val currentIds = processedAlbumPublicUpdateWorkIds.toMutableSet()
        currentIds.add(workId)
        processedAlbumPublicUpdateWorkIds.clear()
        processedAlbumPublicUpdateWorkIds += currentIds
    }

    // 사용하지 않는 오래된 작업 ID 정리
    fun cleanupOldWorkIds() {
        // 일정 개수(예: 100개) 이상이면 오래된 것부터 삭제
        val albumDeleteIds = processedAlbumDeleteWorkIds.toMutableSet()
        if (albumDeleteIds.size > 100) {
            albumDeleteIds.clear()
            processedAlbumDeleteWorkIds.clear()
            processedAlbumDeleteWorkIds += albumDeleteIds
        }

        val updateUserPhotoIds = processedUserPhotoUpdateWorkIds.toMutableSet()
        if (updateUserPhotoIds.size > 100) {
            updateUserPhotoIds.clear()
            processedUserPhotoUpdateWorkIds.clear()
            processedUserPhotoUpdateWorkIds += updateUserPhotoIds
        }

        val updateUsernameIds = processedUsernameUpdateWorkIds.toMutableSet()
        if (updateUsernameIds.size > 100) {
            updateUsernameIds.clear()
            processedUsernameUpdateWorkIds.clear()
            processedUsernameUpdateWorkIds += updateUsernameIds
        }

        val updateAlbumPublicIds = processedAlbumPublicUpdateWorkIds.toMutableSet()
        if (updateAlbumPublicIds.size > 100) {
            updateAlbumPublicIds.clear()
            processedAlbumPublicUpdateWorkIds.clear()
            processedAlbumPublicUpdateWorkIds += updateUsernameIds
        }

        // 마지막 정리 시간 업데이트
        lastWorkPruneTime = System.currentTimeMillis()
    }
}