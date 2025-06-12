package com.tenacy.roadcapture.data.firebase.dto

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import java.time.LocalDateTime

@Parcelize
data class FirebaseSystemConfig(
    val underMaintenance: Boolean,
    val updateRequired: Boolean,
): Parcelable
