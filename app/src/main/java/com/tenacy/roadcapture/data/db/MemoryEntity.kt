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
    ]
)
@Parcelize
data class MemoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val content: String? = null,
    val photoUri: Uri,
    val placeName: String? = null,
    val locationName: String?,
    val country: String,
    val region: String?,
    val city: String?,
    val district: String?,
    val street: String?,
    val details: String?,
    val formattedAddress: String,
    val locationId: Long,
    val createdAt: LocalDateTime,
): Parcelable

data class MemoryWithLocation(
    @Embedded val memory: MemoryEntity,
    @Relation(
        parentColumn = "locationId",
        entityColumn = "id",
    )
    val location: LocationEntity,
)