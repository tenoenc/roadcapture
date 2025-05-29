package com.tenacy.roadcapture.ui

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.AuthCredential
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.ktx.Firebase
import com.tenacy.roadcapture.auth.Loginable
import com.tenacy.roadcapture.data.SocialType
import com.tenacy.roadcapture.data.pref.SubscriptionPref
import com.tenacy.roadcapture.data.pref.UserPref
import com.tenacy.roadcapture.manager.SubscriptionManager
import com.tenacy.roadcapture.util.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val _savedStateHandle: SavedStateHandle,
    private val subscriptionManager: SubscriptionManager,
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
                    // 에러 이벤트 발생
                    withContext(Dispatchers.Default) {
                        viewEvent(LoginViewEvent.SocialError(exception.message, SocialType.Naver))
                    }
                }
                .collect { authResult ->
                    Log.d(TagConstants.AUTH, "파이어베이스 로그인 성공: $authResult")
                    updateUser(SocialType.Naver)
                }
        }
    }

    override fun signInWithCredential(credential: AuthCredential, socialType: SocialType) {
        viewModelScope.launch(Dispatchers.IO) {
            flow {
                emit(auth.signInWithCredential(credential).await())
            }
                .catch { exception ->
                    Log.e(TagConstants.AUTH, "파이어베이스 로그인 실패", exception)
                    // 에러 이벤트 발생
                    withContext(Dispatchers.Default) {
                        viewEvent(LoginViewEvent.SocialError(exception.message, socialType))
                    }
                }
                .collect { authResult ->
                    Log.d(TagConstants.AUTH, "파이어베이스 로그인 성공: $authResult")
                    updateUser(socialType)
                }
        }
    }

    override fun onLoginError(exception: Throwable, socialType: SocialType) {
        Log.e(TagConstants.AUTH, "${socialType.name} 로그인 에러", exception)
        viewModelScope.launch(Dispatchers.Main) {
            // 에러 메시지 표시나 다른 에러 처리
            viewEvent(LoginViewEvent.SocialError(exception.message, socialType))
        }
    }

    override fun onLoginCancelled(socialType: SocialType) {
        Log.d(TagConstants.AUTH, "${socialType.name} 로그인 취소")
        viewModelScope.launch(Dispatchers.Main) {
            // 취소 처리 (필요한 경우)
        }
    }

    private suspend fun updateUser(socialType: SocialType) {
        flow {
            val userId = user!!.uid
            val userRef = db.collection("users").document(userId)
            val docSnapshot = userRef.get().await()
            if (!docSnapshot.exists()) {
                val displayName = user!!.displayName ?: ""
                val photoPath = "images/users/$userId/profile.jpg"
                val photoUrl = setDefaultProfileImage(photoPath)

                val userData = mapOf(
                    "displayName" to displayName,
                    "provider" to socialType.name,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp(),
                    "photoUrl" to photoUrl,
                    "photoName" to photoPath,
                )

                userRef.set(userData).await()

                UserPref.id = userId
                UserPref.provider = socialType
                SubscriptionPref.isSubscriptionActive = false
                UserPref.displayName = displayName
                UserPref.photoUrl = photoUrl
            } else {
                val user = userRef.get().await().toUser()
                UserPref.id = userId
                UserPref.provider = socialType
                SubscriptionPref.isSubscriptionActive = user.isSubscriptionActive
                UserPref.displayName = user.displayName
                UserPref.photoUrl = user.photoUrl
            }

            val checkSubscriptionStatusSuspend = subscriptionManager.checkSubscriptionStatusSuspend()
            Log.d("SubscriptionManager", checkSubscriptionStatusSuspend.toString())

            emit(Unit)
        }
            .catch { exception ->
                Log.e("LoginViewModel", "에러", exception)
                auth.signOut()
                // 사용자 업데이트 실패 처리
                viewEvent(LoginViewEvent.SocialError(exception.message, socialType))
            }
            .collect {
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