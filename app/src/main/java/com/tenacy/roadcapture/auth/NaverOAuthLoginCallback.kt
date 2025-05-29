package com.tenacy.roadcapture.auth

import android.util.Log
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.tenacy.roadcapture.data.SocialType
import com.tenacy.roadcapture.ui.LoginViewModel
import com.tenacy.roadcapture.util.TagConstants

class NaverOAuthLoginCallback(
    private val fragment: Fragment,
    private val viewModelProvider: () -> Loginable = {
        ViewModelProvider(fragment)[LoginViewModel::class.java]
    },
): () -> Unit {

    private val viewModel: Loginable by lazy { viewModelProvider() }

    override fun invoke() {
        try {
            val firebaseToken = viewModel.savedStateHandle.get<String>("firebaseToken")
            val name = viewModel.savedStateHandle.get<String>("name")
            val profileImage = viewModel.savedStateHandle.get<String>("profileImage")

            Log.d(TagConstants.AUTH, "네이버 로그인 성공")
            Log.d(TagConstants.AUTH, "firebaseToken: $firebaseToken")
            Log.d(TagConstants.AUTH, "name: $name")
            Log.d(TagConstants.AUTH, "profileImage: $profileImage")

            // firebaseToken이 null인 경우 에러 처리
            if (firebaseToken == null) {
                val error = IllegalStateException("Firebase token is null")
                Log.e(TagConstants.AUTH, "네이버 로그인 실패: Firebase token이 없습니다", error)
                viewModel.onLoginError(error, SocialType.Naver)
                return
            }

            viewModel.signInWithCustomToken(firebaseToken)
        } catch (e: Exception) {
            Log.e(TagConstants.AUTH, "네이버 로그인 실패", e)
            viewModel.onLoginError(e, SocialType.Naver)
        }
    }
}