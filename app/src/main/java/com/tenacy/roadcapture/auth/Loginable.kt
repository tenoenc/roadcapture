package com.tenacy.roadcapture.auth

import androidx.lifecycle.SavedStateHandle
import com.google.firebase.auth.AuthCredential

interface Loginable {

    val savedStateHandle: SavedStateHandle

    fun signInWithCustomToken(customToken: String)
    fun signInWithCredential(credential: AuthCredential, provider: String)
}