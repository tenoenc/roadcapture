package com.tenacy.roadcapture.auth

import androidx.lifecycle.SavedStateHandle
import com.google.firebase.auth.AuthCredential
import com.tenacy.roadcapture.data.SocialType

interface Loginable {

    val savedStateHandle: SavedStateHandle

    fun signInWithCustomToken(customToken: String)
    fun signInWithCredential(credential: AuthCredential, socialType: SocialType)
}