package com.tenacy.roadcapture.data.api

import com.tenacy.roadcapture.data.api.dto.*
import retrofit2.Response
import retrofit2.http.*

interface FirebaseApi {
    @POST("deleteUserDataHttp")
    suspend fun deleteUserData(
        @Header("Authorization") authToken: String,
        @Body request: DeleteUserRequest
    ): Response<DeleteUserDto>

    @POST("api/api/albums/{albumId}/share")
    suspend fun shareAlbum(
        @Header("Authorization") authToken: String,
        @Path("albumId") albumId: String,
    ): Response<ShareDto>

    @POST("verifySubscription")
    suspend fun verifySubscription(
        @Body request: VerificationRequest,
    ): Response<VerificationDto>
}