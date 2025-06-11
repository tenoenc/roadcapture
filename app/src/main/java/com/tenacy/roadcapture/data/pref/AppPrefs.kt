package com.tenacy.roadcapture.data.pref

import com.chibatching.kotpref.KotprefModel
import com.tenacy.roadcapture.util.Constants

object AppPrefs : KotprefModel() {
    private var _pendingDeepLinkShareId by nullableStringPref(key = "pending_depp_link_album_id")
    var pendingDeepLinkShareId
        set(value) {
            deepLinkTime = System.currentTimeMillis()
            deepLinkExpiryTime = System.currentTimeMillis() + Constants.MILLIS_PER_DAY
            _pendingDeepLinkShareId = value
        }
        get() = if(System.currentTimeMillis() < deepLinkExpiryTime) _pendingDeepLinkShareId else null
    var isDirectDeepLink = true
        private set
        get() = System.currentTimeMillis() <= deepLinkTime + Constants.MILLIS_PER_MINUTES
    private var deepLinkTime by longPref(key = "deep_link_time")
    private var deepLinkExpiryTime by longPref(key = "deep_link_expiry_time")

    var languageChanged by booleanPref(false)
}