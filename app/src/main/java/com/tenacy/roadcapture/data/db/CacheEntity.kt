package com.tenacy.roadcapture.data.db

import android.os.Parcelable
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.parcelize.Parcelize
import java.time.LocalDateTime

@Entity(
    tableName = "caches",
    indices = [
        Index(value = ["type", "targetId"], unique = true)
    ]
)
@Parcelize
data class CacheEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val type: CacheType,
    val targetId: String,
    val createdAt: Long,
): Parcelable
