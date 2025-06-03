package com.tenacy.roadcapture.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface CacheDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(cache: CacheEntity): Long

    @Query("SELECT EXISTS(SELECT 1 FROM caches WHERE type = :type AND targetId = :targetId)")
    suspend fun cachedBy(type: CacheType, targetId: String): Boolean

    // 삭제 전에 만료된 캐시의 targetId 목록을 조회
    @Query("SELECT targetId FROM caches WHERE createdAt < :timestamp")
    suspend fun selectTargetIdByCreatedAtLessThan(timestamp: Long): List<String>

    // 만료된 캐시 삭제
    @Query("DELETE FROM caches WHERE createdAt < :timestamp")
    suspend fun deleteByCreatedAtLessThan(timestamp: Long): Int

    @Query("DELETE FROM caches")
    suspend fun clear()
}