package com.tenacy.roadcapture.data.pref

import com.chibatching.kotpref.KotprefModel
import com.tenacy.roadcapture.util.toTimestamp
import java.time.LocalDateTime

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

    override fun clear() {
        stopTravel()
        super.clear()
    }

    // Album.createdAt을 대체하는 getter
    val createdAt: Long
        get() = travelStartedAt
}