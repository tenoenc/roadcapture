package com.tenacy.roadcapture.util

import android.annotation.SuppressLint
import android.content.Context
import android.provider.Settings
import java.security.MessageDigest

@get:SuppressLint("HardwareIds")
val Context.deviceId: String get() {
    val androidId = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)

    // 앱 패키지명과 조합하여 더 고유하게 만들기
    val combined = "${packageName}_$androidId"

    // SHA-256 해시로 변환
    return hashString("SHA-256", combined)
}

private fun hashString(type: String, input: String): String {
    val bytes = MessageDigest.getInstance(type).digest(input.toByteArray())
    return bytes.joinToString("") { "%02x".format(it) }
}