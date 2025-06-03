package com.tenacy.roadcapture.ui

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.AuthCredential
import com.google.firebase.firestore.FirebaseFirestoreException
import com.tenacy.roadcapture.auth.Loginable
import com.tenacy.roadcapture.data.SocialType
import com.tenacy.roadcapture.util.TagConstants
import com.tenacy.roadcapture.util.functions
import dagger.hilt.android.lifecycle.HiltViewModel
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

    /*override fun signInWithCustomToken(customToken: String) {
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
    }*/

    override fun signInWithCredential(credential: AuthCredential, socialUserId: String, socialType: SocialType) {
        viewModelScope.launch(Dispatchers.IO) {
            flow {
                if(!checkUserExists(socialUserId, socialType)) throw FirebaseFirestoreException("사용자가 존재하지 않아요", FirebaseFirestoreException.Code.NOT_FOUND)
                emit(Unit)
            }
                .catch { exception ->
                    Log.e(TagConstants.AUTH, "파이어베이스 로그인 실패", exception)
                    if(exception is FirebaseFirestoreException && exception.code == FirebaseFirestoreException.Code.NOT_FOUND) {
                        viewEvent(LoginViewEvent.Signup(socialUserId, socialType, credential))
                    } else {
                        viewEvent(LoginViewEvent.SocialError(exception.message, socialType))
                    }
                }
                .collect { authResult ->
                    Log.d(TagConstants.AUTH, "파이어베이스 로그인 성공: $authResult")
                    viewEvent(LoginViewEvent.Login(socialType, credential, true))
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