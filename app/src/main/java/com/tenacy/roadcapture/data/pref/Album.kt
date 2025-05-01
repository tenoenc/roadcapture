package com.tenacy.roadcapture.data.pref

import com.chibatching.kotpref.KotprefModel
import com.google.android.gms.maps.model.LatLng

object Album: KotprefModel() {

    var createdAt: Long by longPref(0L)
}