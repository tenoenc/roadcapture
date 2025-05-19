package com.tenacy.roadcapture.ui

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
sealed class HtmlType: Parcelable {

    @Parcelize
    data object PersonalInfoPolicy: HtmlType()

    @Parcelize
    data object ServiceTermsAndConditions: HtmlType()
}