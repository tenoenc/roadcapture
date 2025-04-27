package com.tenacy.roadcapture.auth

import android.util.Log
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.tenacy.roadcapture.ui.LoginViewModel
import com.tenacy.roadcapture.util.TagConstants

class NaverOAuthLoginCallback(
    private val fragment: Fragment,
): () -> Unit {

    private val viewModel: Loginable by lazy {
        ViewModelProvider(fragment)[LoginViewModel::class.java]
    }

    override fun invoke() {

        val firebaseToken = viewModel.savedStateHandle.get<String>("firebaseToken")
        val name = viewModel.savedStateHandle.get<String>("name")
        val profileImage = viewModel.savedStateHandle.get<String>("profileImage")

        Log.d(TagConstants.AUTH, "네이버 로그인 성공")
        Log.d(TagConstants.AUTH, "firebaseToken: $firebaseToken")
        Log.d(TagConstants.AUTH, "name: $name")
        Log.d(TagConstants.AUTH, "profileImage: $profileImage")

        viewModel.signInWithCustomToken(firebaseToken!!)
    }
}