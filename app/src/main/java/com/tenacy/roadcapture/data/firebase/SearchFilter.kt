package com.tenacy.roadcapture.data.firebase

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
sealed class SearchFilter: Parcelable {
    @Parcelize
    data object All: SearchFilter()
    @Parcelize
    data object Scrap: SearchFilter()
}