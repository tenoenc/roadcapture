package com.tenacy.roadcapture.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface LocationDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAll(entities: List<LocationEntity>): List<Long>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(entity: LocationEntity): Long

    @Query("SELECT * FROM locations ORDER BY createdAt ASC")
    fun selectAll(): List<LocationEntity>

    @Query("DELETE FROM locations WHERE id = :id")
    fun deleteById(id: Long): Int

    @Query("DELETE FROM locations")
    fun clear()
}