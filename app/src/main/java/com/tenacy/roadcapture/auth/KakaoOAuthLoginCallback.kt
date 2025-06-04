package com.tenacy.roadcapture.auth

import android.os.Build.VERSION_CODES.O
import android.util.Log
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.auth.oAuthCredential
import com.kakao.sdk.auth.model.OAuthToken
import com.kakao.sdk.user.UserApiClient
import com.tenacy.roadcapture.data.SocialType
import com.tenacy.roadcapture.ui.LoginViewModel
import com.tenacy.roadcapture.util.TagConstants
import com.tenacy.roadcapture.util.user

class KakaoOAuthLoginCallback(
    private val fragment: Fragment,
    private val viewModelProvider: () -> Loginable = {
        ViewModelProvider(fragment)[LoginViewModel::class.java]
    }
): (OAuthToken?, Throwable?) -> Unit {

    private val viewModel: Loginable by lazy { viewModelProvider() }

    override fun invoke(token: OAuthToken?, error: Throwable?) {
        when {
            error != null -> {
                onError(error)
            }
            token != null -> {
                onSuccess(token)
            }
            else -> {
                onCancel()
            }
        }
    }

    private fun onError(error: Throwable) {
        Log.e(TagConstants.AUTH, "카카오 로그인 실패", error)
        // 뷰모델에 에러 전달
        viewModel.onLoginError(error, SocialType.Kakao)
    }

    private fun onSuccess(token: OAuthToken) {
        Log.d(TagConstants.AUTH, "카카오 로그인 성공: $token")

        UserProfileChangeRequest.Builder()

        // 카카오는 프로필 이미지를 별도 API 호출로 가져와야 함
        UserApiClient.instance.me { user, error ->
            if (error != null) {
                Log.e(TagConstants.AUTH, "카카오 사용자 정보 요청 실패", error)
                // 사용자 정보 요청 실패도 에러로 처리
                viewModel.onLoginError(error, SocialType.Kakao)
                user?.kakaoAccount?.email
            } else if (user != null) {
                val kakaoUserId = user.id
                val profilePicUrl = user.kakaoAccount?.profile?.thumbnailImageUrl

                proceedWithFirebaseAuth(token, kakaoUserId.toString(), profilePicUrl)
            }
        }
    }

    private fun onCancel() {
        Log.d(TagConstants.AUTH, "KakaoOAuthLoginCallback:cancel")
        // 뷰모델에 취소 전달
        viewModel.onLoginCancelled(SocialType.Kakao)
    }

    private fun proceedWithFirebaseAuth(token: OAuthToken, kakaoUserId: String, profilePicUrl: String?) {
        try {
            val providerId = "oidc.roadcapture"
            val authCredential = oAuthCredential(providerId) {
                idToken = token.idToken
                accessToken = token.accessToken
            }

            viewModel.signInWithCredential(authCredential, kakaoUserId, SocialType.Kakao)
        } catch (e: Exception) {
            Log.e(TagConstants.AUTH, "Firebase 인증 실패", e)
            viewModel.onLoginError(e, SocialType.Kakao)
        }
    }
}