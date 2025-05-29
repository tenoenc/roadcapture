package com.tenacy.roadcapture.data.api

import com.tenacy.roadcapture.data.api.dto.DeleteUserDto
import com.tenacy.roadcapture.data.api.dto.DeleteUserRequest
import retrofit2.Response
import retrofit2.http.*

interface FirebaseApi {
    @POST("deleteUserDataHttp")
    suspend fun deleteUserData(
        @Header("Authorization") authToken: String,
        @Body request: DeleteUserRequest
    ): Response<DeleteUserDto>
}