package com.tenacy.roadcapture.data.db

import androidx.room.TypeConverter
import com.tenacy.roadcapture.util.toTimestamp
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId


class RoomConverters {

    @TypeConverter
    fun fromTimestamp(value: Long?): LocalDateTime? {
        return value?.let { Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDateTime() }
    }

    @TypeConverter
    fun toTimestamp(ldt: LocalDateTime?): Long? {
        return ldt?.toTimestamp()
    }
}