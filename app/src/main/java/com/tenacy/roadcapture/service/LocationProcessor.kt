package com.tenacy.roadcapture.service

import android.location.Location
import com.tenacy.roadcapture.data.db.LocationEntity
import kotlinx.coroutines.flow.Flow

interface LocationProcessor {
    /**
     * 위치 업데이트 처리
     * @param location 새로 수신된 위치
     * @return 저장 성공 여부
     */
    suspend fun processLocation(location: Location): LocationEntity?

    /**
     * 위치 저장 여부 결정
     */
    fun shouldSaveLocation(location: Location): Boolean

    /**
     * 최근 저장된 위치 가져오기
     */
    fun getLastSavedLocation(): Location?

    /**
     * 위치 품질 검사
     */
    fun isLocationQualityAcceptable(location: Location): Boolean

    /**
     * 현재 상태 정보를 영구 저장소에 저장
     */
    fun saveState()

    /**
     * 저장된 상태 정보 복원
     */
    fun restoreState()

    /**
     * 최근 저장된 위치 이벤트 Flow
     */
    fun getSavedLocationsFlow(): Flow<Location>
}