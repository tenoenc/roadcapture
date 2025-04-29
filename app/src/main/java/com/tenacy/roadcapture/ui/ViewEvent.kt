package com.tenacy.roadcapture.ui

import android.net.Uri

sealed interface ViewEvent

sealed class GlobalViewEvent: ViewEvent {
    data object GlobalNavigateToLogin: GlobalViewEvent()
    data class Toast(val toast: ToastModel) : GlobalViewEvent()
    data class CopyToClipboard(val text: String) : GlobalViewEvent()
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
    data object ShowTripBefore: MainViewEvent()
}

sealed class HomeViewEvent: ViewEvent

sealed class MyAlbumViewEvent: ViewEvent

sealed class TripViewEvent: ViewEvent {
    data object ResetCameraPosition: TripViewEvent()
    data object ResetCamera: TripViewEvent()
    data object Capture: TripViewEvent()
    data object Back: TripViewEvent()
    data object ShowGuide: TripViewEvent()
    data object ShowSubscription: TripViewEvent()
    data object ZoomIn: TripViewEvent()
    data object ZoomOut: TripViewEvent()
    data object ShowNextBefore: TripViewEvent()
    data object ShowDeleteBefore: TripViewEvent()
}

sealed class CameraViewEvent: ViewEvent {
    data class PhotoTaken(val uri: Uri) : CameraViewEvent()
    data class PhotoSelected(val uri: Uri) : CameraViewEvent()
    data object PhotoCancelled : CameraViewEvent()
}

sealed class NewMemoryViewEvent: ViewEvent {
    data class ResultBack(val memoryId: Long) : NewMemoryViewEvent()
    data class ShowLocation(val address: String): NewMemoryViewEvent()
}

sealed class MemoryViewerViewEvent: ViewEvent