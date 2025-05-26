package com.tenacy.roadcapture.auth

import android.util.Log
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.google.firebase.auth.oAuthCredential
import com.kakao.sdk.auth.model.OAuthToken
import com.kakao.sdk.user.UserApiClient
import com.tenacy.roadcapture.data.pref.SocialType
import com.tenacy.roadcapture.ui.LoginViewModel
import com.tenacy.roadcapture.util.TagConstants

class KakaoOAuthLoginCallback(private val fragment: Fragment): (OAuthToken?, Throwable?) -> Unit {

    private val viewModel: Loginable by lazy {
        ViewModelProvider(fragment)[LoginViewModel::class.java]
    }

    override fun invoke(token: OAuthToken?, error: Throwable?) {
        error?.let { onError(it) }
        token?.let { onSuccess(it) }
        onCancel()
    }

    private fun onError(error: Throwable?)  {
        Log.e(TagConstants.AUTH, "카카오 로그인 실패", error)
    }

    private fun onSuccess(token: OAuthToken) {
        Log.d(TagConstants.AUTH, "카카오 로그인 성공: $token")

        // 카카오는 프로필 이미지를 별도 API 호출로 가져와야 함
        UserApiClient.instance.me { user, error ->
            if (error != null) {
                Log.e(TagConstants.AUTH, "카카오 사용자 정보 요청 실패", error)
                proceedWithFirebaseAuth(token, null)
            } else if (user != null) {
                val profilePicUrl = user.kakaoAccount?.profile?.thumbnailImageUrl
                proceedWithFirebaseAuth(token, profilePicUrl)
            }
        }
    }

    private fun onCancel() {
        Log.d(TagConstants.AUTH, "KakaoOAuthLoginCallback:cancel")
    }

    private fun proceedWithFirebaseAuth(token: OAuthToken, profilePicUrl: String?) {
        val providerId = "oidc.roadcapture"
        val authCredential = oAuthCredential(providerId) {
            idToken = token.idToken
            accessToken = token.accessToken
        }

        viewModel.signInWithCredential(authCredential, SocialType.Kakao)
    }
}