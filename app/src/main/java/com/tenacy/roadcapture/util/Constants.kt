package com.tenacy.roadcapture.util

import com.tenacy.roadcapture.BuildConfig

object Constants {
    // ===== 기존 앱 핵심 상수들 =====
    val MEMORY_MAX_SIZE = if (BuildConfig.DEBUG) 100 else 100
    const val BASIC_TODAY_MEMORY_MAX_SIZE = 10
    const val PREMIUM_TODAY_MEMORY_MAX_SIZE = 30
    const val PREMIUM_PRICE_PER_MONTH = "2.99"

    const val MILLIS_PER_DAY = 24 * 60 * 60 * 1000L
    const val MILLIS_PER_HOUR = 60 * 60 * 1000L
    const val MILLIS_PER_MINUTES = 60 * 1000L
    const val MILLIS_PER_SECONDS = 1000L

    const val MIN_DISTANCE_TO_SAVE = 30f

    // ===== 기존 트랙킹 서비스 및 워커 =====
    const val ACTION_STOP_TRACKING_SERVICE = "com.tenacy.roadcapture.STOP_LOCATION_SERVICE"
    val TRACKING_INTERVAL = if (BuildConfig.DEBUG) 3_000L else 15_000L
    val TRACKING_FASTEST_INTERVAL = if (BuildConfig.DEBUG) 3_000L else 10_000L
    const val TRACKING_NOTIFICATION_CHANNEL_ID = "tracking_channel"
    const val TRACKING_NOTIFICATION_ID = 1
    const val TRACKING_WORK_NAME = "tracking"
    const val TRACKING_REPEAT_INTERVAL_MINUTES = 15L
    const val TRACKING_INITIAL_DELAY_MINUTES = 1L

    // ===== 향상된 위치 추적을 위한 새로운 상수들 =====

    // GPS 점프 감지 관련
    const val MAX_REASONABLE_ACCELERATION = 15.0f      // 최대 합리적 가속도 (m/s²)
    const val MAX_BEARING_CHANGE = 140f                // 급격한 방향 전환 감지 각도

    // 센서 기반 이동 감지 관련
    const val MOVEMENT_THRESHOLD = 2.0f                // 이동 감지 가속도 임계값 (m/s²)
    const val STATIONARY_TIMEOUT = 120_000L            // 정지 상태 판단 시간 (2분)

    // 배터리 최적화 관련
    const val LOW_ACCURACY_TIMEOUT = 300_000L          // 저정확도 모드 전환 시간 (5분)

    // 칼만 필터 기본값
    const val KALMAN_PROCESS_NOISE_DEFAULT = 0.01f     // 기본 프로세스 노이즈
    const val KALMAN_MEASUREMENT_NOISE_DEFAULT = 1.0f  // 기본 측정 노이즈

    // ===== 기존 구독 관련 상수들 =====
    const val SUBSCRIPTION_PREMIUM_PRODUCT_ID = "subscription_premium"

    // 구독 상태 확인 워커
    const val SUBSCRIPTION_REPEAT_INTERVAL_MINUTES = 15L
    const val SUBSCRIPTION_INITIAL_DELAY_MINUTES = 1L
    const val SUBSCRIPTION_EXPIRY_CHECK_DELAY_MS = 30_000L // 30초
    const val SUBSCRIPTION_NOTIFICATION_CHANNEL_ID = "subscription_channel"
    const val SUBSCRIPTION_NOTIFICATION_ID_EXPIRING = 1001
    const val SUBSCRIPTION_NOTIFICATION_ID_OTHER_ACCOUNT = 1002
    const val SUBSCRIPTION_EXPIRY_WARNING_DAYS = 3
    const val SUBSCRIPTION_WORK_NAME_PERIODIC_CHECK = "subscription_check"
    const val SUBSCRIPTION_WORK_NAME_EXPIRY_CHECK = "subscription_expiry_check"

    // ===== 기존 앨범 관련 상수들 =====
    const val ALBUM_WORK_NAME_DELETE = "delete_album"
    const val ALBUM_WORK_NAME_UPDATE_PUBLIC = "update_album_public"
    const val ALBUM_WORK_NAME_CREATE_SHARE_LINK = "create_share_link"

    // ===== 기존 사용자 관련 상수들 =====
    const val USER_WORK_NAME_UPDATE_NAME = "update_username"
    const val USER_WORK_NAME_UPDATE_PHOTO = "update_user_photo"

    // ===== 기존 캐시 관련 상수들 =====
    const val CACHE_REPEAT_INTERVAL_DAYS = 1L
    const val CACHE_EXPIRATION_DAYS = 30L
    const val CACHE_CLEANUP_WORK_NAME = "cleanup_cache"
    const val CACHE_PHOTO_WORK_NAME = "cache_photo"
}