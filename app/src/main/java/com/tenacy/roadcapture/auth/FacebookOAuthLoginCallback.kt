package com.tenacy.roadcapture.auth

import android.util.Log
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.facebook.FacebookCallback
import com.facebook.FacebookException
import com.facebook.login.LoginResult
import com.google.firebase.auth.FacebookAuthProvider
import com.tenacy.roadcapture.data.SocialType
import com.tenacy.roadcapture.ui.LoginViewModel
import com.tenacy.roadcapture.util.TagConstants

class FacebookOAuthLoginCallback(
    private val fragment: Fragment,
    private val viewModelProvider: () -> Loginable = {
        ViewModelProvider(fragment)[LoginViewModel::class.java]
    },
): FacebookCallback<LoginResult> {

    private val viewModel: Loginable by lazy { viewModelProvider() }

    override fun onCancel() {
        Log.d(TagConstants.AUTH, "FacebookOAuthLoginCallback:cancel")
        // 뷰모델에 취소 전달
        viewModel.onLoginCancelled(SocialType.Facebook)
    }

    override fun onError(error: FacebookException) {
        Log.e(TagConstants.AUTH, "페이스북 로그인 실패", error)
        // 뷰모델에 에러 전달
        viewModel.onLoginError(error, SocialType.Facebook)
    }

    override fun onSuccess(result: LoginResult) {
        try {
            Log.d(TagConstants.AUTH, "페이스북 로그인 성공: $result")

            val token = result.accessToken.token
            val authCredential = FacebookAuthProvider.getCredential(token)

            // 페이스북 프로필 이미지 URL 생성
            val profilePicUrl = "https://graph.facebook.com/${result.accessToken.userId}/picture?type=large"

            // 프로필 URL 포함하여 전달
            viewModel.signInWithCredential(authCredential, SocialType.Facebook)
        } catch (e: Exception) {
            Log.e(TagConstants.AUTH, "페이스북 로그인 성공 후 처리 중 실패", e)
            viewModel.onLoginError(e, SocialType.Facebook)
        }
    }
}