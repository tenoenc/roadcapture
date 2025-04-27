package com.tenacy.roadcapture.data.pref

import com.chibatching.kotpref.KotprefModel

object User: KotprefModel() {

    var provider: String by stringPref()
}