package com.tenacy.roadcapture.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [
        MemoryEntity::class,
        LocationEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
@TypeConverters(RoomConverters::class, UriConverter::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun memoryDao(): MemoryDao
    abstract fun locationDao(): LocationDao

    companion object {
        fun getInstance(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "app_db")
                .fallbackToDestructiveMigration()
                .build()
    }
}