package com.tenacy.roadcapture.data.pref

import com.chibatching.kotpref.KotprefModel

object Album: KotprefModel() {

    var createdAt: Long by longPref(0L)
}