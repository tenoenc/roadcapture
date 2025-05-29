package com.tenacy.roadcapture.auth

import android.util.Log
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialResponse
import androidx.credentials.exceptions.GetCredentialException
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.GoogleAuthProvider
import com.tenacy.roadcapture.data.SocialType
import com.tenacy.roadcapture.ui.LoginViewModel
import com.tenacy.roadcapture.util.TagConstants

class GoogleOAuthLoginCallback(
    private val fragment: Fragment,
    private val viewModelProvider: () -> Loginable = {
        ViewModelProvider(fragment)[LoginViewModel::class.java]
    },
) : (Result<GetCredentialResponse>) -> Unit {

    private val viewModel: Loginable by lazy { viewModelProvider() }

    override fun invoke(result: Result<GetCredentialResponse>) {
        result.fold(
            onSuccess = { response ->
                handleSuccess(response)
            },
            onFailure = { exception ->
                handleFailure(exception)
            }
        )
    }

    private fun handleSuccess(response: GetCredentialResponse) {
        try {
            when (val credential = response.credential) {
                is CustomCredential -> {
                    if (credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                        val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                        val idToken = googleIdTokenCredential.idToken
                        val authCredential = GoogleAuthProvider.getCredential(idToken, null)

                        val profilePicUrl = googleIdTokenCredential.profilePictureUri?.toString()

                        Log.d(TagConstants.AUTH, "구글 로그인 성공: $authCredential")
                        viewModel.signInWithCredential(authCredential, SocialType.Google)
                    } else {
                        val error = IllegalStateException("Unsupported credential type: ${credential.type}")
                        viewModel.onLoginError(error, SocialType.Google)
                    }
                }
                else -> {
                    val error = IllegalStateException("Unknown credential type: ${credential::class.simpleName}")
                    viewModel.onLoginError(error, SocialType.Google)
                }
            }
        } catch (e: Exception) {
            viewModel.onLoginError(e, SocialType.Google)
        }
    }

    private fun handleFailure(exception: Throwable) {
        Log.e(TagConstants.AUTH, "구글 로그인 실패", exception)

        // GetCredentialException 처리
        if (exception is GetCredentialException) {
            viewModel.onLoginError(exception, SocialType.Google)
        } else {
            viewModel.onLoginError(exception, SocialType.Google)
        }
    }
}