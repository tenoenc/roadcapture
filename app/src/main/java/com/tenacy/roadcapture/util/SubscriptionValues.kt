package com.tenacy.roadcapture.util

import com.tenacy.roadcapture.data.pref.SubscriptionPref

object SubscriptionValues {
    var memoryMaxSize = Constants.BASIC_MEMORY_MAX_SIZE
        private set
        get() = if (SubscriptionPref.isSubscriptionActive) Constants.PREMIUM_MEMORY_MAX_SIZE else Constants.BASIC_MEMORY_MAX_SIZE
}