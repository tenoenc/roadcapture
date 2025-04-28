package com.tenacy.roadcapture.data.db

import android.os.Parcelable
import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.parcelize.Parcelize
import java.time.LocalDateTime

@Entity(tableName = "locations",)
@Parcelize
data class LocationEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val latitude: Double,
    val longitude: Double,
    val createdAt: LocalDateTime,
): Parcelable