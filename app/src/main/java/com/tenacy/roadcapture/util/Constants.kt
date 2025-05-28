package com.tenacy.roadcapture.util

object Constants {
    const val BASIC_MEMORY_MAX_SIZE = 3
    const val PREMIUM_MEMORY_MAX_SIZE = 100
    const val PREMIUM_PRICE_PER_MONTH = "1.99"

    // 체크 간격
    const val PERIODIC_CHECK_INTERVAL_MINUTES = 15L
    const val INITIAL_DELAY_MINUTES = 1L

    // 만료 체크 지연
    const val EXPIRY_CHECK_DELAY_MS = 30_000L // 30초

    // 알림 관련
    const val NOTIFICATION_CHANNEL_ID = "subscription_channel"
    const val NOTIFICATION_CHANNEL_NAME = "구독 알림"
    const val NOTIFICATION_ID_EXPIRING = 1001
    const val NOTIFICATION_ID_OTHER_ACCOUNT = 1002

    // 만료 임박 기준
    const val EXPIRY_WARNING_DAYS = 3

    // WorkManager 작업 이름
    const val WORK_NAME_PERIODIC_CHECK = "subscription_check"
    const val WORK_NAME_EXPIRY_CHECK = "subscription_expiry_check"
    const val WORK_NAME_DELETE_ALBUM = "delete_album"

    // 시간 단위
    const val MILLIS_PER_DAY = 24 * 60 * 60 * 1000L
    const val MILLIS_PER_HOUR = 60 * 60 * 1000L
}