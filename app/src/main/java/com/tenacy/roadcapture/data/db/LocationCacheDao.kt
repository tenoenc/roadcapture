package com.tenacy.roadcapture.data.db

import androidx.room.*

@Dao
interface LocationCacheDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entity: List<LocationCacheEntity>)

    @Transaction
    @Query("SELECT * FROM location_caches WHERE albumId = :albumId ORDER BY createdAt ASC")
    suspend fun selectByAlbumId(albumId: String): List<LocationCacheEntity>

    @Query("DELETE FROM location_caches WHERE albumId = :albumId")
    suspend fun deleteByAlbumId(albumId: String): Int

    @Query("DELETE FROM location_caches WHERE albumId IN (:albumIds)")
    suspend fun deleteInAlbumIds(albumIds: List<String>): Int

    @Query("DELETE FROM location_caches")
    suspend fun clear()
}