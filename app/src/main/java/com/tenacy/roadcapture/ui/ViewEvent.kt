package com.tenacy.roadcapture.ui

import android.net.Uri
import com.google.android.gms.maps.model.LatLng

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

sealed class SearchViewEvent: ViewEvent

sealed class AlbumMarkedViewEvent: ViewEvent

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
    data object ShowAfter: TripViewEvent()
    data object ShowStopBefore: TripViewEvent()
}

sealed class CameraViewEvent: ViewEvent {
    data class PhotoTaken(val uri: Uri) : CameraViewEvent()
    data class PhotoSelected(val uri: Uri) : CameraViewEvent()
    data object PhotoCancelled : CameraViewEvent()
}

sealed class NewMemoryViewEvent: ViewEvent {
    data class ResultBack(val memoryId: Long, val coordinates: LatLng) : NewMemoryViewEvent()
    data class ShowLocation(val address: String): NewMemoryViewEvent()
}


sealed class MemoryViewerViewEvent: ViewEvent {
    data class ShowLocation(val address: String): MemoryViewerViewEvent()
    data object MoveToPrevPage: MemoryViewerViewEvent()
    data object MoveToNextPage: MemoryViewerViewEvent()
    data object ShowInfo: MemoryViewerViewEvent()
    data class ResultBack(val coordinates: LatLng): MemoryViewerViewEvent()
}

sealed class ModifiableMemoryViewerViewEvent: ViewEvent {
    data class ShowLocation(val address: String): ModifiableMemoryViewerViewEvent()
    data object MoveToPrevPage: ModifiableMemoryViewerViewEvent()
    data object MoveToNextPage: ModifiableMemoryViewerViewEvent()
    data object ShowMore: ModifiableMemoryViewerViewEvent()
    data class ResultBack(val coordinates: LatLng? = null): ModifiableMemoryViewerViewEvent()
}

sealed class NewAlbumViewEvent: ViewEvent {
    data object ShowCompleteBefore: NewAlbumViewEvent()
}