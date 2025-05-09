package com.tenacy.roadcapture.data.firebase

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
sealed class MemoryFilter: Parcelable {
    data class User(val id: String, val isPublic: Boolean? = null): MemoryFilter()
}