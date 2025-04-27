package com.tenacy.roadcapture.ui

import android.content.Context
import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.AuthCredential
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.SetOptions
import com.google.firebase.ktx.Firebase
import com.tenacy.roadcapture.auth.Loginable
import com.tenacy.roadcapture.data.pref.User
import com.tenacy.roadcapture.util.*
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val _savedStateHandle: SavedStateHandle,
) : BaseViewModel(), Loginable {

    override val savedStateHandle: SavedStateHandle
        get() = _savedStateHandle

    override fun signInWithCustomToken(customToken: String) {
        viewModelScope.launch(Dispatchers.IO) {
            flow {
                emit(Firebase.auth.signInWithCustomToken(customToken).await())
            }
                .catch { exception ->
                    Log.e(TagConstants.AUTH, "파이어베이스 로그인 실패", exception)
                }
                .collect { authResult ->
                    Log.d(TagConstants.AUTH, "파이어베이스 로그인 성공: $authResult")
                    updateUser(ProviderConstants.NAVER)
                }
        }
    }

    override fun signInWithCredential(credential: AuthCredential, provider: String) {
        viewModelScope.launch(Dispatchers.IO) {
            flow {
                emit(auth.signInWithCredential(credential).await())
            }
                .catch { exception ->
                    Log.e(TagConstants.AUTH, "파이어베이스 로그인 실패", exception)
                }
                .collect { authResult ->
                    Log.d(TagConstants.AUTH, "파이어베이스 로그인 성공: $authResult")
                    updateUser(provider)
                }
        }
    }

    private suspend fun updateUser(provider: String) {
        flow {
            val userToUpdate: HashMap<String, Any?> = hashMapOf(
                "updatedAt" to FieldValue.serverTimestamp(),
            )

            val ref = db.collection("users").document(user!!.uid)
            val docSnapshot = ref.get().await()
            if (!docSnapshot.exists()) {
                userToUpdate["uid"] = user!!.uid
                userToUpdate["displayName"] = user!!.displayName
                userToUpdate["provider"] = provider
                userToUpdate["createdAt"] = FieldValue.serverTimestamp()
                userToUpdate["photoURL"] = setDefaultProfileImage(user!!.uid)
                userToUpdate["photoName"] = FirebaseConstants.PROFILE_NAME
                ref.set(userToUpdate).await()
            } else {
                ref.set(userToUpdate, SetOptions.merge()).await()
            }

            emit(Unit)
        }
            .catch { exception ->
                auth.signOut()
            }
            .collect {
                User.provider = provider
                viewEvent(LoginViewEvent.NavigateToMain)
            }
    }

    fun onGoogleLoginClick() {
        viewModelScope.launch(Dispatchers.IO) {
            viewEvent(LoginViewEvent.GoogleLogin)
        }
    }

    fun onFacebookLoginClick() {
        viewModelScope.launch(Dispatchers.IO) {
            viewEvent(LoginViewEvent.FacebookLogin)
        }
    }

    fun onKakaoLoginClick() {
        viewModelScope.launch(Dispatchers.IO) {
            viewEvent(LoginViewEvent.KakaoLogin)
        }
    }

    fun onNaverLoginClick() {
        viewModelScope.launch(Dispatchers.IO) {
            viewEvent(LoginViewEvent.NaverLogin)
        }
    }
}