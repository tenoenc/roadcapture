package com.tenacy.roadcapture.data.db

import android.location.Location
import android.os.Parcel
import androidx.room.TypeConverter
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.tenacy.roadcapture.util.toLocalDateTime
import com.tenacy.roadcapture.util.toTimestamp
import java.time.LocalDateTime


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

    @TypeConverter
    fun fromCacheType(cacheType: CacheType?): String? {
        return when (cacheType) {
            is CacheType.Album -> "ALBUM"
            null -> null
            // 새로운 CacheType 서브클래스가 추가되면 여기에 케이스 추가
        }
    }

    @TypeConverter
    fun toCacheType(value: String?): CacheType? {
        return when (value) {
            "ALBUM" -> CacheType.Album
            null -> null
            else -> throw IllegalArgumentException("Unknown CacheType: $value")
        }
    }

    @TypeConverter
    fun toByteArray(location: Location?): ByteArray? {
        if (location == null) return null

        val parcel = Parcel.obtain()
        try {
            location.writeToParcel(parcel, 0)
            return parcel.marshall()
        } finally {
            parcel.recycle()
        }
    }

    @TypeConverter
    fun toLocation(bytes: ByteArray?): Location? {
        if (bytes == null) return null

        val parcel = Parcel.obtain()
        try {
            parcel.unmarshall(bytes, 0, bytes.size)
            parcel.setDataPosition(0)
            return Location.CREATOR.createFromParcel(parcel)
        } finally {
            parcel.recycle()
        }
    }
}