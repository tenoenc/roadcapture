package com.tenacy.roadcapture.data.db

import android.content.Context
import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        MemoryEntity::class,
        LocationEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
@TypeConverters(RoomConverters::class, UriConverter::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun memoryDao(): MemoryDao
    abstract fun locationDao(): LocationDao

    companion object {
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 임시 테이블 생성
                db.execSQL("CREATE TABLE IF NOT EXISTS memories_temp AS SELECT * FROM memories")

                // 기존 테이블 삭제
                db.execSQL("DROP TABLE memories")

                // 새 스키마로 테이블 재생성 (변경할 필드를 nullable로 설정)
                db.execSQL("""
                    CREATE TABLE memories (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        content TEXT,
                        photoUri TEXT NOT NULL,
                        placeName TEXT,
                        locationName TEXT,
                        country TEXT NOT NULL,
                        region TEXT,
                        city TEXT,
                        district TEXT,
                        street TEXT,
                        details TEXT,
                        formattedAddress TEXT NOT NULL,
                        locationId INTEGER NOT NULL,
                        createdAt TEXT NOT NULL,
                        FOREIGN KEY (locationId) REFERENCES locations(id) ON DELETE CASCADE
                    )
                """)

                // 데이터 복원
                db.execSQL("INSERT INTO memories SELECT * FROM memories_temp")

                // 임시 테이블 삭제
                db.execSQL("DROP TABLE memories_temp")
            }
        }

        fun getInstance(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "app_db")
                .fallbackToDestructiveMigration()
                .build()
    }
}