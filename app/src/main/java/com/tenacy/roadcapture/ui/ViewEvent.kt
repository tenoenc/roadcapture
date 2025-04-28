package com.tenacy.roadcapture.ui

import android.net.Uri

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

sealed class TripViewEvent: ViewEvent {
    data object ResetCameraPosition: TripViewEvent()
    data object ResetCamera: TripViewEvent()
    data object Capture: TripViewEvent()
    data object StopTraveling: TripViewEvent()
}

sealed class CameraViewEvent: ViewEvent {
    data class PhotoTaken(val uri: Uri) : CameraViewEvent()
    data class PhotoSelected(val uri: Uri) : CameraViewEvent()
    data object PhotoCancelled : CameraViewEvent()
}

sealed class NewMemoryViewEvent: ViewEvent {
    data class ResultBack(val memoryId: Long) : NewMemoryViewEvent()
}