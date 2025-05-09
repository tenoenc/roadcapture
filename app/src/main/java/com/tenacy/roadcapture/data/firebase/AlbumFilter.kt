package com.tenacy.roadcapture.data.firebase

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
sealed class AlbumFilter: Parcelable {
    @Parcelize
    data object All: AlbumFilter()
    @Parcelize
    data object Scrap: AlbumFilter()
    @Parcelize
    data class User(val id: String, val isPublic: Boolean? = null): AlbumFilter()
}