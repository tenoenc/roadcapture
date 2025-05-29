package com.tenacy.roadcapture.data.pref

import android.util.Log
import com.chibatching.kotpref.KotprefModel
import com.tenacy.roadcapture.util.toLocalDateTime
import com.tenacy.roadcapture.util.toTimestamp
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

object TravelStatePref : KotprefModel() {
    var isTraveling by booleanPref(default = false, key = "is_traveling")
    var travelStartedAt by longPref(default = 0L, key = "travel_started_at")

    fun startTravel() {
        isTraveling = true
        if (travelStartedAt == 0L) {
            travelStartedAt = LocalDateTime.now().toTimestamp()
        }
    }

    fun stopTravel() {
        isTraveling = false
        travelStartedAt = 0L
    }

    fun isOverOneMonth(): Boolean {
        if (!isTraveling || travelStartedAt == 0L) {
            return false
        }

        val startDate = travelStartedAt.toLocalDateTime()
        val now = LocalDateTime.now()

        // ChronoUnit.MONTHS로 차이 계산
        val monthsBetween = ChronoUnit.MONTHS.between(startDate, now)
        return monthsBetween >= 1
    }

    override fun clear() {
        stopTravel()
        super.clear()
    }

    // Album.createdAt을 대체하는 getter
    val createdAt: Long
        get() = travelStartedAt
}