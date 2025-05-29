package com.tenacy.roadcapture.util

import com.tenacy.roadcapture.BuildConfig

object Constants {
    const val BASIC_MEMORY_MAX_SIZE = 3
    const val PREMIUM_MEMORY_MAX_SIZE = 100
    const val PREMIUM_PRICE_PER_MONTH = "1.99"

    const val ACTION_STOP_TRACKING_SERVICE = "com.tenacy.roadcapture.STOP_LOCATION_SERVICE"

    // 체크 간격
    const val SUBSCRIPTION_PERIODIC_CHECK_INTERVAL_MINUTES = 15L
    val TRACKING_INTERVAL = if (BuildConfig.DEBUG) 3_000L else 15_000L
    val TRACKING_FASTEST_INTERVAL = if (BuildConfig.DEBUG) 3_000L else 10_000L
    const val SUBSCRIPTION_INITIAL_DELAY_MINUTES = 1L

    // 만료 체크 지연
    const val SUBSCRIPTION_EXPIRY_CHECK_DELAY_MS = 30_000L // 30초

    // 알림 관련
    const val SUBSCRIPTION_NOTIFICATION_CHANNEL_ID = "subscription_channel"
    const val SUBSCRIPTION_NOTIFICATION_CHANNEL_NAME = "구독 알림"
    const val TRACKING_NOTIFICATION_CHANNEL_ID = "location_tracking_channel"
    const val TRACKING_NOTIFICATION_ID = 1
    const val SUBSCRIPTION_NOTIFICATION_ID_EXPIRING = 1001
    const val SUBSCRIPTION_NOTIFICATION_ID_OTHER_ACCOUNT = 1002

    // 만료 임박 기준
    const val SUBSCRIPTION_EXPIRY_WARNING_DAYS = 3

    // WorkManager 작업 이름
    const val SUBSCRIPTION_WORK_NAME_PERIODIC_CHECK = "subscription_check"
    const val SUBSCRIPTION_WORK_NAME_EXPIRY_CHECK = "subscription_expiry_check"
    const val WORK_NAME_DELETE_ALBUM = "delete_album"

    // 시간 단위
    const val MILLIS_PER_DAY = 24 * 60 * 60 * 1000L
    const val MILLIS_PER_HOUR = 60 * 60 * 1000L

    const val MIN_DISTANCE_TO_SAVE = 30f
}