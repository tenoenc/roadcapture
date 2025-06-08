package com.tenacy.roadcapture.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        MemoryEntity::class,
        LocationEntity::class,
        MemoryCacheEntity::class,
        LocationCacheEntity::class,
        CacheEntity::class,
    ],
    version = 4,
    exportSchema = true,
)
@TypeConverters(RoomConverters::class, UriConverter::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun memoryDao(): MemoryDao
    abstract fun locationDao(): LocationDao
    abstract fun memoryCacheDao(): MemoryCacheDao
    abstract fun locationCacheDao(): LocationCacheDao
    abstract fun cacheDao(): CacheDao

    companion object {
        // isThumbnail 컬럼 추가
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE memories ADD COLUMN isThumbnail INTEGER NOT NULL DEFAULT 0"
                )
            }
        }

        // memory_caches에 isThumbnail 컬럼 추가
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE memory_caches ADD COLUMN isThumbnail INTEGER NOT NULL DEFAULT 0"
                )
            }
        }

        // isThumbnail 컬럼 제거
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.beginTransaction()
                try {
                    // 백업 테이블 생성 (외래키 포함)
                    database.execSQL("""
                        CREATE TABLE memories_new (
                            id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                            content TEXT,
                            photoUri TEXT NOT NULL,
                            placeName TEXT,
                            formattedAddress TEXT NOT NULL,
                            addressTags TEXT NOT NULL,
                            locationId INTEGER NOT NULL,
                            createdAt INTEGER NOT NULL,
                            FOREIGN KEY(locationId) REFERENCES locations(id) ON UPDATE NO ACTION ON DELETE CASCADE
                        )
                    """)

                    // 데이터 복사
                    database.execSQL("""
                        INSERT INTO memories_new (id, content, photoUri, placeName, formattedAddress, addressTags, locationId, createdAt)
                        SELECT id, content, photoUri, placeName, formattedAddress, addressTags, locationId, createdAt
                        FROM memories
                    """)

                    // 기존 테이블 삭제
                    database.execSQL("DROP TABLE memories")

                    // 테이블 이름 변경
                    database.execSQL("ALTER TABLE memories_new RENAME TO memories")

                    // 최종 테이블에 인덱스 생성 (중요!)
                    database.execSQL("""
                        CREATE INDEX index_memories_locationId ON memories(locationId)
                    """)

                    database.setTransactionSuccessful()
                } finally {
                    database.endTransaction()
                }
            }
        }

        fun getInstance(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "app_db")
                .addMigrations(MIGRATION_1_2)
                .addMigrations(MIGRATION_2_3)
                .addMigrations(MIGRATION_3_4)
                .build()
    }
}