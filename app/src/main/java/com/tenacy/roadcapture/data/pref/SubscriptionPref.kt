package com.tenacy.roadcapture.data.pref

import com.chibatching.kotpref.KotprefModel
import com.tenacy.roadcapture.util.Constants

object SubscriptionPref : KotprefModel() {
    private var _isSubscriptionActive by booleanPref(false)
    var isSubscriptionActive
        set(value) {
            _isSubscriptionActive = value
        }
        get() = _isSubscriptionActive && !linkedAccountExists && System.currentTimeMillis() < getGracePeriodExpiryTime()
    var subscriptionExpiryTime by longPref(0L)
    var isSubscriptionCancelled by booleanPref(false)
    var purchaseToken by stringPref("")
    var subscriptionPurchaseTime by longPref(0L)
    var lastSubscriptionCheckTime by longPref(0L)
    var lastKnownExpiryTime by longPref(0L)
    var linkedAccountExists by booleanPref(false)

    // 유예 기간 포함 만료 시간 (24시간 추가)
    private fun getGracePeriodExpiryTime(): Long {
        if (subscriptionExpiryTime == 0L) return 0L
        return subscriptionExpiryTime + Constants.MILLIS_PER_DAY // 24시간 유예
    }

    // 구독이 취소되었지만 아직 유효한지 확인
    fun isCancelledButStillValid(): Boolean {
        return _isSubscriptionActive && isSubscriptionCancelled
    }

    // 만료까지 남은 시간 (밀리초)
    fun timeUntilExpiry(): Long {
        if (!_isSubscriptionActive) return 0L
        return subscriptionExpiryTime - System.currentTimeMillis()
    }

    // 만료까지 남은 일 수 계산
    fun daysUntilExpiry(): Int {
        if (!isSubscriptionActive) return 0

        val now = System.currentTimeMillis()
        val diff = subscriptionExpiryTime - now
        return (diff / (1000 * 60 * 60 * 24)).toInt()
    }

    // 구독 정보 초기화
    override fun clear() {
        _isSubscriptionActive = false
        subscriptionExpiryTime = 0L
        isSubscriptionCancelled = false
        purchaseToken = ""
        subscriptionPurchaseTime = 0L
        lastSubscriptionCheckTime = 0L
        lastKnownExpiryTime = 0L
        linkedAccountExists = false
        super.clear()
    }
}