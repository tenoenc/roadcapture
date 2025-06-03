package com.tenacy.roadcapture.data.db

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
sealed class CacheType: Parcelable {
    @Parcelize
    data object Album: CacheType()
}