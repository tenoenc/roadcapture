package com.tenacy.roadcapture.data.firebase

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
enum class AlbumFilter: Parcelable {
    ALL, SCRAP
}