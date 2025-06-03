package com.tenacy.roadcapture.data.db

import androidx.room.*

@Dao
interface MemoryCacheDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entity: List<MemoryCacheEntity>)

    @Transaction
    @Query("SELECT * FROM memory_caches WHERE albumId = :albumId ORDER BY createdAt ASC")
    suspend fun selectByAlbumId(albumId: String): List<MemoryCacheEntity>

    @Query("DELETE FROM memory_caches WHERE albumId = :albumId")
    suspend fun deleteByAlbumId(albumId: String): Int

    @Query("DELETE FROM memory_caches WHERE albumId IN (:albumIds)")
    suspend fun deleteInAlbumIds(albumIds: List<String>): Int

    @Query("DELETE FROM memory_caches")
    suspend fun clear()
}