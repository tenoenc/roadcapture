package com.tenacy.roadcapture.data.pref

import com.chibatching.kotpref.KotprefModel

object UserPref: KotprefModel() {

    var id: String by stringPref()
    var displayName: String by stringPref()
    var photoUrl: String by stringPref()
    private var _provider: String by stringPref()
    var provider: SocialType?
        get() = SocialType.of(_provider)
        set(value) {
            _provider = value?.name ?: ""
        }
}