package com.tenacy.roadcapture.ui

import android.net.Uri
import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.AuthCredential
import com.tenacy.roadcapture.BuildConfig
import com.tenacy.roadcapture.R
import com.tenacy.roadcapture.auth.Loginable
import com.tenacy.roadcapture.data.SocialType
import com.tenacy.roadcapture.data.pref.SubscriptionPref
import com.tenacy.roadcapture.data.pref.UserPref
import com.tenacy.roadcapture.manager.SubscriptionManager
import com.tenacy.roadcapture.ui.dto.User
import com.tenacy.roadcapture.util.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class AppInfoViewModel @Inject constructor(
    private val _savedStateHandle: SavedStateHandle,
    subscriptionManager: SubscriptionManager,
) : BaseViewModel(), Loginable {

    override val savedStateHandle: SavedStateHandle
        get() = _savedStateHandle

    val isSubscriptionActive: StateFlow<Boolean> = subscriptionManager.isSubscriptionActive
        .map { it && !SubscriptionPref.linkedAccountExists }
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
    val subscriptionButtonEnabled = subscriptionManager.isSubscriptionActive.map { isSubscriptionActive ->
        !SubscriptionPref.linkedAccountExists
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), true)
    val subscriptionDescriptionText = subscriptionManager.isSubscriptionActive.map { isSubscriptionActive ->
        when {
            SubscriptionPref.linkedAccountExists -> "다른 계정에서\n이미 혜택을 받고 있어요"
            isSubscriptionActive -> "프리미엄 플랜 구독중"
            else -> "구독하고 더 많은\n혜택을 누려보세요!"
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), "")

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
//     }

    override fun signInWithCredential(credential: AuthCredential, socialUserId: String, socialProfileUrl: String, socialType: SocialType) {
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
                    viewEvent(AppInfoViewEvent.Withdraw)
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
                val userRef = db.collection(FirebaseConstants.COLLECTION_USERS)
                    .document(userId)
                val user = userRef.get().await().toUser()

                UserPref.displayName = user.displayName
                UserPref.photoUrl = user.photoUrl

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

    fun onAppReviewClick() {
        viewModelScope.launch(Dispatchers.Default) {
            viewEvent(AppInfoViewEvent.ReviewApp)
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
            val isSubscriptionActive = SubscriptionPref.isSubscriptionActive
            if(isSubscriptionActive) {
                // 정기 구독 관리
                viewEvent(AppInfoViewEvent.OpenPlayStoreSubscriptionManager)
            } else {
                // 정기 구독 하기
                viewEvent(AppInfoViewEvent.ShowSubscription)
            }
        }
    }

    fun onLogoutClick() {
        viewModelScope.launch(Dispatchers.Default) {
            viewEvent(AppInfoViewEvent.ShowLogoutBefore)
        }
    }

    fun onWithdrawClick() {
        viewModelScope.launch(Dispatchers.Default) {
            viewEvent(AppInfoViewEvent.ShowWithdrawBefore)
        }
    }

}