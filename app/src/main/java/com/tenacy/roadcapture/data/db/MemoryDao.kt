package com.tenacy.roadcapture.data.db

import androidx.room.*

@Dao
interface MemoryDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: MemoryEntity): Long

    @Transaction
    @Query("SELECT * FROM memories ORDER BY createdAt ASC")
    suspend fun selectAll(): List<MemoryWithLocation>

    @Transaction
    @Query("SELECT * FROM memories WHERE id IN (:ids) ORDER BY createdAt ASC")
    suspend fun selectByIds(ids: List<Long>): List<MemoryWithLocation>

    @Query("DELETE FROM memories WHERE id = :id")
    suspend fun deleteById(id: Long): Int

    @Query("DELETE FROM memories")
    suspend fun clear()
}