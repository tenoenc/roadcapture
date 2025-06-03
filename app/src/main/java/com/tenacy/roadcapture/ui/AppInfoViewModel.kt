package com.tenacy.roadcapture.ui

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.AuthCredential
import com.tenacy.roadcapture.BuildConfig
import com.tenacy.roadcapture.R
import com.tenacy.roadcapture.auth.Loginable
import com.tenacy.roadcapture.data.SocialType
import com.tenacy.roadcapture.data.api.dto.DeleteUserRequest
import com.tenacy.roadcapture.data.db.LocationDao
import com.tenacy.roadcapture.data.db.MemoryDao
import com.tenacy.roadcapture.data.pref.SubscriptionPref
import com.tenacy.roadcapture.data.pref.TravelPref
import com.tenacy.roadcapture.data.pref.UserPref
import com.tenacy.roadcapture.manager.SubscriptionManager
import com.tenacy.roadcapture.manager.TravelingStateManager
import com.tenacy.roadcapture.ui.dto.User
import com.tenacy.roadcapture.util.*
import com.tenacy.roadcapture.worker.*
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class AppInfoViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val _savedStateHandle: SavedStateHandle,
    private val locationDao: LocationDao,
    private val memoryDao: MemoryDao,
    private val travelingStateManager: TravelingStateManager,
    subscriptionManager: SubscriptionManager,
) : BaseViewModel(), Loginable {

    override val savedStateHandle: SavedStateHandle
        get() = _savedStateHandle

    val isSubscriptionActive: StateFlow<Boolean> = subscriptionManager.isSubscriptionActive
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = SubscriptionPref.isSubscriptionActive
        )

    val version = BuildConfig.VERSION_NAME
    private val _user = MutableStateFlow<User?>(null)
    val profilePhotoUrl = _user.mapNotNull { it?.photoUrl?.let(Uri::parse) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UserPref.photoUrl.let(Uri::parse))
    val profileDisplayName = _user.mapNotNull { it?.displayName }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UserPref.displayName)
    val subscriptionText = isSubscriptionActive.map {
        if(it) "프리미엄 플랜 구독중" else "구독하고 더 많은\n혜택을 누려보세요!"
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), "구독하고 더 많은\n혜택을 누려보세요!")

    val providerDrawable = _user.mapNotNull {
        it?.provider?.let {
            when(it) {
                SocialType.Kakao -> R.drawable.ic_kakao
                SocialType.Google -> R.drawable.ic_google
                SocialType.Naver -> R.drawable.ic_naver
                SocialType.Facebook -> R.drawable.ic_facebook
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), null)

    init {
        fetchUser()
    }

//    override fun signInWithCustomToken(customToken: String) {
//    }

    override fun signInWithCredential(credential: AuthCredential, socialUserId: String, socialType: SocialType) {
        viewModelScope.launch(Dispatchers.IO) {
            flow {
                user!!.reauthenticate(credential).await()
                emit(Unit)
            }
                .catch { exception ->
                    Log.e(TagConstants.AUTH, "파이어베이스 재인증 실패", exception)
                    withContext(Dispatchers.Default) {
                        viewEvent(AppInfoViewEvent.Error.Reauth(exception.message, socialType))
                    }
                }
                .collect { authResult ->
                    Log.d(TagConstants.AUTH, "파이어베이스 재인증 성공: $authResult")
                    withdraw()
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

    private fun fetchUser() {
        viewModelScope.launch(Dispatchers.IO) {
            flow {
                val userId = UserPref.id
                val userRef = db.collection("users")
                    .document(userId)
                val user = userRef.get().await().toUser()

                UserPref.displayName = user.displayName
                UserPref.photoUrl = user.photoUrl
                SubscriptionPref.isSubscriptionActive = user.isSubscriptionActive

                emit(user)
            }
                .catch { exception ->
                    Log.e("AppInfoViewModel", "에러", exception)
                }
                .collect { user ->
                    _user.emit(User.from(user))
                }
        }
    }

    private fun withdraw() {
        viewModelScope.launch(Dispatchers.IO) {
            flow {
                val userId = UserPref.id

                // 1. 서버에 데이터 삭제 요청 (비동기)
                Log.d("AppInfoViewModel", "Firestore 데이터 삭제 시작")

                // Callable 방식 -> 인증 쉽게 처리, 느림
//                val data = hashMapOf("userId" to userId)
//                val task = functions.getHttpsCallable("deleteUserData").call(data)
//                Tasks.await(task)

                // HTTP 방식 -> 빠름, 인증 별도 구현
                val idToken = user?.getIdToken(false)?.await()?.token ?: throw Exception("토큰을 가져올 수 없습니다.")
                val response = RetrofitInstance.firebaseApi.deleteUserData(
                    authToken = "Bearer $idToken",
                    request = DeleteUserRequest(userId),
                )
                if (!response.isSuccessful) {
                    throw Exception(response.errorBody()?.string())
                }

                val responseBody = response.body()
                Log.d("AppInfoViewModel", "데이터 삭제 요청 성공: ${responseBody?.message}, JobID: ${responseBody?.jobId}")
                Log.d("AppInfoViewModel", "Firestore 데이터 삭제 완료")

                // 2. 여행 중이라면 서비스 및 워커 작업 취소
                Log.d("AppInfoViewModel", "백그라운드 작업 정리 시작")
                travelingStateManager.stopTraveling()
                DeleteAlbumWorker.cancelAll(context)
                UpdateUsernameWorker.cancelWork(context)
                UpdateUserPhotoWorker.cancelWork(context)
                UpdateAlbumPublicWorker.cancelAll(context)
                SubscriptionCheckWorker.cancelAll(context)
                Log.d("AppInfoViewModel", "백그라운드 작업 정리 완료")
                
                // 3. 로컬 데이터 정리
                Log.d("AppInfoViewModel", "로컬 데이터 정리 시작")

                // 로컬 데이터 삭제
                UserPref.clear()
                TravelPref.clear()
                SubscriptionPref.clear()
                memoryDao.clear()
                locationDao.clear()
                context.clearCacheDirectory()

                Log.d("AppInfoViewModel", "로컬 데이터 정리 완료")

                // 4. Auth 계정 삭제 (이미 재인증했으므로 성공할 것)
                Log.d("AppInfoViewModel", "Auth 계정 삭제 시작")
                val currentUser = auth.currentUser
                currentUser?.delete()?.await()
                Log.d("AppInfoViewModel", "Auth 계정 삭제 완료")

                emit(Unit)
            }
                .catch { exception ->
                    Log.e("AppInfoViewModel", "서비스 탈퇴 실패", exception)
                    viewEvent(AppInfoViewEvent.Error.Withdraw(exception.message))
                }
                .collect {
                    viewEvent(AppInfoViewEvent.WithdrawComplete)
                }
        }
    }
    
    fun refreshUserStates() {
        _user.update { it?.copy(
            photoUrl = UserPref.photoUrl,
            displayName = UserPref.displayName,
            provider = UserPref.provider,
        ) }
    }

    fun onServiceTermsAndConditionsClick() {
        viewModelScope.launch(Dispatchers.Default) {
            viewEvent(AppInfoViewEvent.NavigateToHtml(HtmlType.TermsOfService))
        }
    }

    fun onDonateClick() {
        viewModelScope.launch(Dispatchers.Default) {
            viewEvent(AppInfoViewEvent.Donate)
        }
    }

    fun onInquiryClick() {
        viewModelScope.launch(Dispatchers.Default) {
            viewEvent(AppInfoViewEvent.InquireToDeveloper)
        }
    }

    fun onPersonalInfoPolicyClick() {
        viewModelScope.launch(Dispatchers.Default) {
            viewEvent(AppInfoViewEvent.NavigateToHtml(HtmlType.PrivacyPolicy))
        }
    }

    fun onSubscribeClick() {
        viewModelScope.launch(Dispatchers.Default) {
            viewEvent(AppInfoViewEvent.Subscribe)
        }
    }

    fun onLogoutClick() {
        viewModelScope.launch(Dispatchers.Default) {
            viewEvent(AppInfoViewEvent.Logout)
        }
    }

    fun onWithdrawClick() {
        viewModelScope.launch(Dispatchers.Default) {
            viewEvent(AppInfoViewEvent.ShowWithdrawBefore)
        }
    }

}