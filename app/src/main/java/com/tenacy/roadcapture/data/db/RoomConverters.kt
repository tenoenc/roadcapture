package com.tenacy.roadcapture.data.db

import androidx.room.TypeConverter
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.tenacy.roadcapture.util.toLocalDateTime
import com.tenacy.roadcapture.util.toTimestamp
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId


class RoomConverters {

    @TypeConverter
    fun fromTimestamp(value: Long?): LocalDateTime? {
        return value?.toLocalDateTime()
    }

    @TypeConverter
    fun toTimestamp(ldt: LocalDateTime?): Long? {
        return ldt?.toTimestamp()
    }

    @TypeConverter
    fun fromString(value: String?): List<String> {
        if (value == null) {
            return emptyList()
        }

        val listType = object : TypeToken<List<String>>() {}.type
        return Gson().fromJson(value, listType)
    }

    @TypeConverter
    fun fromList(list: List<String>?): String {
        if (list == null) {
            return "[]"
        }
        return Gson().toJson(list)
    }
}