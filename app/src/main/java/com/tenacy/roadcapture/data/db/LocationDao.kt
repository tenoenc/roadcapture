package com.tenacy.roadcapture.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface LocationDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(entity: LocationEntity): Long

    @Query("SELECT * FROM locations ORDER BY createdAt ASC")
    fun selectAll(): List<LocationEntity>

    @Query("SELECT * FROM locations ORDER BY createdAt ASC")
    fun selectAllAsFlow(): Flow<List<LocationEntity>>

    @Query("DELETE FROM locations WHERE id = :id")
    fun deleteById(id: Long): Int

    @Query("DELETE FROM locations")
    fun clear()
}