package com.tenacy.roadcapture.ui

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
sealed class HtmlType: Parcelable {

    @Parcelize
    data object PrivacyPolicy: HtmlType()

    @Parcelize
    data object TermsOfService: HtmlType()

    @Parcelize
    data object PrivacyPolicyAgreement: HtmlType()

    @Parcelize
    data object TermsOfServiceAgreement: HtmlType()
}