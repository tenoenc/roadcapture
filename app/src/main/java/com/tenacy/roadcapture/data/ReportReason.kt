package com.tenacy.roadcapture.data

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
sealed class ReportReason(val name: String): Parcelable {
    @Parcelize
    data object InappropriateContent: ReportReason("inappropriate_content")
    @Parcelize
    data object SpamAdvertising: ReportReason("spam_advertising")
    @Parcelize
    data object PersonalInfoExposure: ReportReason("personal_info_exposure")
    @Parcelize
    data object FalseInformation: ReportReason("false_information")
    @Parcelize
    data object Undefined: ReportReason("undefined")
}