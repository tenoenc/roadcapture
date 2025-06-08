package com.tenacy.roadcapture.util

import com.tenacy.roadcapture.BuildConfig

object Constants {
    val MEMORY_MAX_SIZE = if (BuildConfig.DEBUG) 3 else 100
    const val BASIC_TODAY_MEMORY_MAX_SIZE = 10
    const val PREMIUM_TODAY_MEMORY_MAX_SIZE = 30
    const val PREMIUM_PRICE_PER_MONTH = "4.99"

    const val MILLIS_PER_DAY = 24 * 60 * 60 * 1000L
    const val MILLIS_PER_HOUR = 60 * 60 * 1000L
    const val MILLIS_PER_MINUTES = 60 * 1000L
    const val MILLIS_PER_SECONDS = 1000L

    const val MIN_DISTANCE_TO_SAVE = 30f

    // 트랙킹 서비스 및 워커
    const val ACTION_STOP_TRACKING_SERVICE = "com.tenacy.roadcapture.STOP_LOCATION_SERVICE"
    val TRACKING_INTERVAL = if (BuildConfig.DEBUG) 3_000L else 15_000L
    val TRACKING_FASTEST_INTERVAL = if (BuildConfig.DEBUG) 3_000L else 10_000L
    const val TRACKING_NOTIFICATION_CHANNEL_ID = "tracking_channel"
    const val TRACKING_NOTIFICATION_ID = 1
    const val TRACKING_WORK_NAME = "tracking"
    const val TRACKING_REPEAT_INTERVAL_MINUTES = 15L
    const val TRACKING_INITIAL_DELAY_MINUTES = 1L

    const val SUBSCRIPTION_PREMIUM_PRODUCT_ID = "subscription_premium"

    // 구독 상태 확인 워커
    const val SUBSCRIPTION_REPEAT_INTERVAL_MINUTES = 15L
    const val SUBSCRIPTION_INITIAL_DELAY_MINUTES = 1L
    const val SUBSCRIPTION_EXPIRY_CHECK_DELAY_MS = 30_000L // 30초
    const val SUBSCRIPTION_NOTIFICATION_CHANNEL_ID = "subscription_channel"
    const val SUBSCRIPTION_NOTIFICATION_CHANNEL_NAME = "구독 알림"
    const val SUBSCRIPTION_NOTIFICATION_ID_EXPIRING = 1001
    const val SUBSCRIPTION_NOTIFICATION_ID_OTHER_ACCOUNT = 1002
    const val SUBSCRIPTION_EXPIRY_WARNING_DAYS = 3
    const val SUBSCRIPTION_WORK_NAME_PERIODIC_CHECK = "subscription_check"
    const val SUBSCRIPTION_WORK_NAME_EXPIRY_CHECK = "subscription_expiry_check"

    const val ALBUM_WORK_NAME_DELETE = "delete_album"
    const val ALBUM_WORK_NAME_UPDATE_PUBLIC = "update_album_public"
    const val ALBUM_WORK_NAME_CREATE_SHARE_LINK = "create_share_link"

    const val USER_WORK_NAME_UPDATE_NAME = "update_username"
    const val USER_WORK_NAME_UPDATE_PHOTO = "update_user_photo"

    const val CACHE_REPEAT_INTERVAL_DAYS = 1L
    const val CACHE_EXPIRATION_DAYS = 30L
    const val CACHE_CLEANUP_WORK_NAME = "cleanup_cache"
    const val CACHE_PHOTO_WORK_NAME = "cache_photo"
}