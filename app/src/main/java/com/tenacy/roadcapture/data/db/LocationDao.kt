package com.tenacy.roadcapture.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface LocationDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: LocationEntity): Long

    @Query("SELECT * FROM locations ORDER BY createdAt ASC")
    suspend fun selectAll(): List<LocationEntity>

    @Query("DELETE FROM locations WHERE id = :id")
    suspend fun deleteById(id: Long): Int

    @Query("DELETE FROM locations")
    suspend fun clear()
}