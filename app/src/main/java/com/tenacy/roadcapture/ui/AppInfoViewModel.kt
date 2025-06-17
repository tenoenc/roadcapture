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
import com.tenacy.roadcapture.data.firebase.exception.SystemConfigException
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
import javax.inject.Inject

@HiltViewModel
class AppInfoViewModel @Inject constructor(
    private val _savedStateHandle: SavedStateHandle,
    private val resourceProvider: ResourceProvider,
    subscriptionManager: SubscriptionManager,
) : BaseViewModel(), Loginable {

    private val _signingIn = MutableStateFlow(false)
    val signingIn = _signingIn.asStateFlow()

    private val _subscribing = MutableStateFlow(false)
    val subscribing = _subscribing.asStateFlow()

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
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), false)
    val subscriptionDescriptionText = combine(subscriptionManager.isSubscriptionActive, resourceProvider.configurationContextFlow) { isSubscriptionActive, context ->
        when {
            SubscriptionPref.linkedAccountExists -> context.getString(R.string.benefit_already_received)
            isSubscriptionActive -> resourceProvider.getString(R.string.premium_plan_active)
            else -> context.getString(R.string.subscribe_benefit_prompt)
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
                    viewEvent(AppInfoViewEvent.Error.Reauth(exception.message, socialType))
                    _signingIn.update { false }
                }
                .collect { authResult ->
                    Log.d(TagConstants.AUTH, "파이어베이스 재인증 성공: $authResult")
                    viewEvent(AppInfoViewEvent.Withdraw)
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

    fun setSubscribing(subscribing: Boolean) {
        _subscribing.update { subscribing }
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

    fun enterSigningIn() {
        _signingIn.update { true }
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

    fun onLanguageSettingClick() {
        viewModelScope.launch(Dispatchers.Default) {
            viewEvent(AppInfoViewEvent.NavigateToLanguage)
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
            try {
                // [VALIDATE_SYSTEM_CONFIG]
                validateSystemConfigV2()
            } catch (exception: Exception) {
                // [VALIDATE_SYSTEM_CONFIG]
                if(exception is SystemConfigException) {
                    handleSystemConfigException(exception)
                    return@launch
                }
            }
            viewEvent(AppInfoViewEvent.ShowWithdrawBefore)
        }
    }

}