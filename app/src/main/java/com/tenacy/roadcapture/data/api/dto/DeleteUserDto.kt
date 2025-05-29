package com.tenacy.roadcapture.data.api.dto

data class DeleteUserRequest(
    val userId: String
)

data class DeleteUserDto(
    val success: Boolean,
    val message: String,
    val jobId: String,
)
