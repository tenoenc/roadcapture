package com.tenacy.roadcapture.ui.dto

import android.os.Parcelable
import com.google.android.gms.maps.model.LatLng
import kotlinx.parcelize.Parcelize

@Parcelize
data class Address(
    val country: String?,
    val formattedAddress: String?,
    val components: List<String>,
    val coordinates: LatLng
) : Parcelable