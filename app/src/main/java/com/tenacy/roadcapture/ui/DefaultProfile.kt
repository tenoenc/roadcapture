package com.tenacy.roadcapture.ui

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
sealed class DefaultProfile(open val url: String): Parcelable {
    @Parcelize
    data class Social(override val url: String): DefaultProfile(url)
    @Parcelize
    data class App(override val url: String): DefaultProfile(url)
}