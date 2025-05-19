package com.tenacy.roadcapture.data.db

import android.net.Uri
import android.os.Parcelable
import androidx.room.*
import kotlinx.parcelize.Parcelize
import java.time.LocalDateTime

@Entity(
    tableName = "memories",
    foreignKeys = [
        ForeignKey(
            entity = LocationEntity::class,
            parentColumns = ["id"],
            childColumns = ["locationId"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    // 인덱스 추가
    indices = [
        Index("locationId")
    ]
)
@Parcelize
data class MemoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val content: String? = null,
    val photoUri: Uri,
    val placeName: String? = null,
    val formattedAddress: String,
    val addressTags: List<String>,
    val locationId: Long,
    val createdAt: LocalDateTime,
): Parcelable

@Parcelize
data class MemoryWithLocation(
    @Embedded val memory: MemoryEntity,
    @Relation(
        parentColumn = "locationId",
        entityColumn = "id",
    )
    val location: LocationEntity,
): Parcelable