package com.tenacy.roadcapture.ui

import android.net.Uri
import android.util.Log
import androidx.lifecycle.viewModelScope
import com.tenacy.roadcapture.BuildConfig
import com.tenacy.roadcapture.R
import com.tenacy.roadcapture.data.pref.SocialType
import com.tenacy.roadcapture.data.pref.SubscriptionPref
import com.tenacy.roadcapture.data.pref.UserPref
import com.tenacy.roadcapture.manager.SubscriptionManager
import com.tenacy.roadcapture.ui.dto.User
import com.tenacy.roadcapture.util.db
import com.tenacy.roadcapture.util.toUser
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

@HiltViewModel
class AppInfoViewModel @Inject constructor(
    subscriptionManager: SubscriptionManager,
) : BaseViewModel() {

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

    fun refreshUserStates() {
        _user.update { it?.copy(
            photoUrl = UserPref.photoUrl,
            displayName = UserPref.displayName,
            provider = UserPref.provider!!,
        ) }
    }

    fun onServiceTermsAndConditionsClick() {
        viewModelScope.launch(Dispatchers.Default) {
            viewEvent(AppInfoViewEvent.NavigateToHtml(HtmlType.ServiceTermsAndConditions))
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
            viewEvent(AppInfoViewEvent.NavigateToHtml(HtmlType.PersonalInfoPolicy))
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

}