package com.tenacy.roadcapture.data.db

import androidx.room.*

@Dao
interface MemoryDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAll(entities: List<MemoryEntity>): List<Long>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(entity: MemoryEntity): Long

    @Query("SELECT * FROM memories ORDER BY createdAt ASC")
    fun selectAll(): List<MemoryWithLocation>

    @Query("SELECT * FROM memories WHERE id IN (:ids) ORDER BY createdAt ASC")
    fun selectByIds(ids: List<Long>): List<MemoryWithLocation>


    @Query("DELETE FROM memories WHERE id = :id")
    fun deleteById(id: Long): Int

    @Query("DELETE FROM memories")
    fun clear()
}