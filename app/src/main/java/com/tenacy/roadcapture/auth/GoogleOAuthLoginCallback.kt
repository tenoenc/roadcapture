package com.tenacy.roadcapture.auth

import android.util.Log
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialResponse
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.GoogleAuthProvider
import com.tenacy.roadcapture.data.SocialType
import com.tenacy.roadcapture.ui.LoginViewModel
import com.tenacy.roadcapture.util.TagConstants

class GoogleOAuthLoginCallback(private val fragment: Fragment): (GetCredentialResponse) -> Unit {

    private val viewModel: Loginable by lazy {
        ViewModelProvider(fragment).get(LoginViewModel::class.java)
    }

    override fun invoke(response: GetCredentialResponse) {
        when (val credential = response.credential) {
            is CustomCredential -> {
                // If you are also using any external sign-in libraries, parse them
                // here with the utility functions provided.
                if (credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                    val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                    val idToken = googleIdTokenCredential.idToken
                    val authCredential = GoogleAuthProvider.getCredential(idToken, null)

                    // Google 프로필 이미지 URL 가져오기
                    val profilePicUrl = googleIdTokenCredential.profilePictureUri?.toString()

                    Log.d(TagConstants.AUTH, "구글 로그인 성공: $authCredential")
                    viewModel.signInWithCredential(authCredential, SocialType.Google)
                }
            }
        }
    }
}