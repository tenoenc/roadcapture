package com.tenacy.roadcapture.data.pref

import com.chibatching.kotpref.KotprefModel

object SubscriptionPref : KotprefModel() {
    private var _isSubscriptionActive by booleanPref(false)
    var isSubscriptionActive
        set(value) {
            _isSubscriptionActive = value
        }
        get() = _isSubscriptionActive && System.currentTimeMillis() < subscriptionExpiryTime
    var subscriptionType by stringPref("")
    var subscriptionExpiryTime by longPref(0L)
    var isSubscriptionCancelled by booleanPref(false)
    var purchaseToken by stringPref("")
    var subscriptionPurchaseTime by longPref(0L)
    var lastSubscriptionCheckTime by longPref(0L)

    // 구독이 취소되었지만 아직 유효한지 확인
    fun isCancelledButStillValid(): Boolean {
        return _isSubscriptionActive && isSubscriptionCancelled
    }

    // 만료까지 남은 일 수 계산
    fun daysUntilExpiry(): Int {
        if (!isSubscriptionActive) return 0

        val now = System.currentTimeMillis()
        val diff = subscriptionExpiryTime - now
        return (diff / (1000 * 60 * 60 * 24)).toInt()
    }

    // 구독 정보 초기화
    fun clearSubscription() {
        _isSubscriptionActive = false
        subscriptionType = ""
        subscriptionExpiryTime = 0L
        isSubscriptionCancelled = false
        purchaseToken = ""
    }
}