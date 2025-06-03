package com.tenacy.roadcapture.auth

import androidx.lifecycle.SavedStateHandle
import com.google.firebase.auth.AuthCredential
import com.tenacy.roadcapture.data.SocialType

interface Loginable {
    val savedStateHandle: SavedStateHandle
//    fun signInWithCustomToken(customToken: String)
    fun signInWithCredential(credential: AuthCredential, socialUserId: String, socialType: SocialType)
    fun onLoginError(error: Throwable, socialType: SocialType)
    fun onLoginCancelled(socialType: SocialType)
}