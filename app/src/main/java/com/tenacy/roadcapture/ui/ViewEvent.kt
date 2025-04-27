package com.tenacy.roadcapture.ui

import com.airbnb.lottie.L

sealed interface ViewEvent

sealed class GlobalViewEvent: ViewEvent {
    data object GlobalNavigateToLogin: GlobalViewEvent()
    data class Toast(val toast: ToastModel) : GlobalViewEvent()
}

sealed class LoginViewEvent: ViewEvent {
    data object NavigateToMain: LoginViewEvent()
    data object GoogleLogin: LoginViewEvent()
    data object FacebookLogin: LoginViewEvent()
    data object KakaoLogin: LoginViewEvent()
    data object NaverLogin: LoginViewEvent()
}

sealed class MainViewEvent: ViewEvent {
    data object Logout: MainViewEvent()
    data object New: MainViewEvent()
}

sealed class HomeViewEvent: ViewEvent

sealed class MyAlbumViewEvent: ViewEvent