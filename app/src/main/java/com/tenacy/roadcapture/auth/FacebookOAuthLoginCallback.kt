package com.tenacy.roadcapture.auth

import android.util.Log
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.facebook.FacebookCallback
import com.facebook.FacebookException
import com.facebook.login.LoginResult
import com.google.firebase.auth.FacebookAuthProvider
import com.tenacy.roadcapture.data.pref.SocialType
import com.tenacy.roadcapture.ui.LoginViewModel
import com.tenacy.roadcapture.util.TagConstants

class FacebookOAuthLoginCallback(private val fragment: Fragment): FacebookCallback<LoginResult> {

    private val viewModel: Loginable by lazy {
        ViewModelProvider(fragment)[LoginViewModel::class.java]
    }

    override fun onCancel() {
        Log.d(TagConstants.AUTH, "FacebookOAuthLoginCallback:cancel")
    }

    override fun onError(error: FacebookException) {
        Log.e(TagConstants.AUTH, "페이스북 로그인 실패", error)
    }

    override fun onSuccess(result: LoginResult) {

        Log.d(TagConstants.AUTH, "페이스북 로그인 성공: $result")

        val token = result.accessToken.token
        val authCredential = FacebookAuthProvider.getCredential(token)

        // 페이스북 프로필 이미지 URL 생성
        val profilePicUrl = "https://graph.facebook.com/${result.accessToken.userId}/picture?type=large"

        // 프로필 URL 포함하여 전달
        viewModel.signInWithCredential(authCredential, SocialType.Facebook)
    }
}