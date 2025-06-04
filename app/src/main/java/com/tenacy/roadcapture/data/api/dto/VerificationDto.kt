package com.tenacy.roadcapture.data.api.dto


data class VerificationRequest(
    val packageName: String,
    val subscriptionId: String,
    val purchaseToken: String,
)

data class VerificationDto(
    val expiryTimeMillis: Long,
)