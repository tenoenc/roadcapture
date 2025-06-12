package com.tenacy.roadcapture.ui

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.AuthCredential
import com.google.firebase.firestore.FirebaseFirestoreException
import com.tenacy.roadcapture.R
import com.tenacy.roadcapture.auth.Loginable
import com.tenacy.roadcapture.data.SocialType
import com.tenacy.roadcapture.data.firebase.exception.SystemConfigException
import com.tenacy.roadcapture.util.*
import com.tenacy.roadcapture.worker.PhotoCacheWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val _savedStateHandle: SavedStateHandle,
    private val resourceProvider: ResourceProvider,
) : BaseViewModel(), Loginable {

    private val _signingIn = MutableStateFlow(false)
    val signingIn = _signingIn.asStateFlow()

    override val savedStateHandle: SavedStateHandle
        get() = _savedStateHandle

    override fun signInWithCredential(credential: AuthCredential, socialUserId: String, socialProfileUrl: String, socialType: SocialType) {
        viewModelScope.launch(Dispatchers.IO) {
            flow {
                if(!checkUserExists(socialUserId, socialType)) throw FirebaseFirestoreException(resourceProvider.getString(R.string.user_not_found), FirebaseFirestoreException.Code.NOT_FOUND)
                emit(Unit)
            }
                .catch { exception ->
                    Log.e(TagConstants.AUTH, "파이어베이스 로그인 실패", exception)
                    if(exception is FirebaseFirestoreException && exception.code == FirebaseFirestoreException.Code.NOT_FOUND) {
                        PhotoCacheWorker.enqueueOneTimeWork(resourceProvider.getConfigurationContext(), listOf(socialProfileUrl))
                        viewEvent(LoginViewEvent.Signup(socialUserId, socialType, socialProfileUrl, credential))
                    } else {
                        viewEvent(LoginViewEvent.SocialError(exception.message, socialType))
                    }
                    _signingIn.update { false }
                }
                .collect { authResult ->
                    Log.d(TagConstants.AUTH, "파이어베이스 로그인 성공: $authResult")
                    viewEvent(LoginViewEvent.Login(socialUserId, socialType, credential))
                    _signingIn.update { false }
                }
        }
    }

    override fun onLoginError(exception: Throwable, socialType: SocialType) {
        Log.e(TagConstants.AUTH, "${socialType.name} 로그인 에러", exception)
        viewEvent(LoginViewEvent.SocialError(exception.message, socialType))
        _signingIn.update { false }
    }

    override fun onLoginCancelled(socialType: SocialType) {
        Log.d(TagConstants.AUTH, "${socialType.name} 로그인 취소")
        _signingIn.update { false }
    }

    // 사용자 존재 여부만 확인
    private suspend fun checkUserExists(socialUserId: String, socialType: SocialType): Boolean {
        Log.d("LoginViewModel", "socialUserId: $socialUserId, socialType: ${socialType.name}")
        val data = hashMapOf(
            "socialUserId" to socialUserId,
            "provider" to socialType.name,
        )

        return try {
            val result = functions
                .getHttpsCallable("checkUserExists")
                .call(data)
                .await()

            val resultMap = result.data as? Map<String, Any>
            resultMap?.get("exists") as? Boolean ?: false
        } catch (e: Exception) {
            Log.e("UserCheck", "사용자 확인 중 오류", e)
            false
        }
    }

    fun onGoogleLoginClick() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // [VALIDATE_SYSTEM_CONFIG]
                validateSystemConfig()
            } catch (exception: Exception) {
                // [VALIDATE_SYSTEM_CONFIG]
                if(exception is SystemConfigException) {
                    handleSystemConfigException(exception)
                    return@launch
                }
            }
            viewEvent(LoginViewEvent.GoogleLogin)
            _signingIn.update { true }
        }
    }

    fun onFacebookLoginClick() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // [VALIDATE_SYSTEM_CONFIG]
                validateSystemConfig()
            } catch (exception: Exception) {
                // [VALIDATE_SYSTEM_CONFIG]
                if(exception is SystemConfigException) {
                    handleSystemConfigException(exception)
                    return@launch
                }
            }
            viewEvent(LoginViewEvent.FacebookLogin)
            _signingIn.update { true }
        }
    }

    fun onKakaoLoginClick() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // [VALIDATE_SYSTEM_CONFIG]
                validateSystemConfig()
            } catch (exception: Exception) {
                // [VALIDATE_SYSTEM_CONFIG]
                if(exception is SystemConfigException) {
                    handleSystemConfigException(exception)
                    return@launch
                }
            }
            viewEvent(LoginViewEvent.KakaoLogin)
            _signingIn.update { true }
        }
    }

    fun onNaverLoginClick() {
        viewModelScope.launch(Dispatchers.IO) {
            viewEvent(LoginViewEvent.NaverLogin)
            _signingIn.update { true }
        }
    }
}