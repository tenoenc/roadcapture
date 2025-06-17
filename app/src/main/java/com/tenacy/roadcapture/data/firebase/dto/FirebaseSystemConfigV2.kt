package com.tenacy.roadcapture.data.firebase.dto

import android.os.Parcelable
import com.tenacy.roadcapture.BuildConfig
import kotlinx.parcelize.Parcelize
import java.time.LocalDateTime

@Parcelize
data class FirebaseSystemConfigV2(
    val minAppVersion: String,
    val underMaintenance: Boolean,
): Parcelable {
//    fun isUpdateRequired() = updateRequired && appVersion != BuildConfig.VERSION_NAME
    fun isUnderMaintenance() = underMaintenance
}
