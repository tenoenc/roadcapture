package com.tenacy.roadcapture.ui

import com.google.android.gms.maps.model.LatLng
import com.tenacy.roadcapture.ui.dto.Album

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

sealed class HomeViewEvent: ViewEvent {
    data object Search: HomeViewEvent()
}

sealed class AppInfoViewEvent: ViewEvent {
    data object InquireToDeveloper: AppInfoViewEvent()
    data class NavigateToHtml(val type: HtmlType): AppInfoViewEvent()
    data object Logout: AppInfoViewEvent()
    data class Donate(val type: String) : AppInfoViewEvent()
}

sealed class ScrapViewEvent: ViewEvent {
    data object Search: ScrapViewEvent()
}

sealed class MyAlbumViewEvent: ViewEvent {
    data object ShowMore: MyAlbumViewEvent()
}

sealed class TripViewEvent: ViewEvent {
    data object ResetCameraPosition: TripViewEvent()
    data class SetCamera(val coordinates: LatLng? = null, val zoom: Float? = null) : TripViewEvent()
    data object Capture: TripViewEvent()
    data object Back: TripViewEvent()
    data object ShowGuide: TripViewEvent()
    data object ShowSubscription: TripViewEvent()
    data object ZoomIn: TripViewEvent()
    data object ZoomOut: TripViewEvent()
    data object ShowAfter: TripViewEvent()
    data object ShowStopBefore: TripViewEvent()
}

sealed class NewMemoryViewEvent: ViewEvent {
    data class ResultBack(val memoryId: Long, val coordinates: LatLng) : NewMemoryViewEvent()
    data class ShowLocation(val address: String): NewMemoryViewEvent()
    data object ShowAd: NewMemoryViewEvent()
}


sealed class MemoryViewerViewEvent: ViewEvent {
    data class ShowLocation(val address: String): MemoryViewerViewEvent()
    data object MoveToPrevPage: MemoryViewerViewEvent()
    data object MoveToNextPage: MemoryViewerViewEvent()
    data object ShowInfo: MemoryViewerViewEvent()
    data class ResultBack(val coordinates: LatLng): MemoryViewerViewEvent()
}

sealed class UserMemoryViewerViewEvent: ViewEvent {
    data class ShowLocation(val address: String): UserMemoryViewerViewEvent()
    data object ShowMore: UserMemoryViewerViewEvent()
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

sealed class AlbumViewEvent: ViewEvent {
    data object ResetCameraPosition: AlbumViewEvent()
    data object ZoomIn: AlbumViewEvent()
    data object ZoomOut: AlbumViewEvent()
    data class SetCamera(val coordinates: LatLng? = null, val zoom: Float? = null): AlbumViewEvent()
    data class ShowInfo(val album: Album, val totalMemoryCount: Int): AlbumViewEvent()
    data object Share: AlbumViewEvent()
    data class NavigateToStudio(val userId: String): AlbumViewEvent()
    data class Forbidden(val message: String): AlbumViewEvent()
}

sealed class SearchViewEvent: ViewEvent

sealed class MyAlbumTabViewEvent: ViewEvent {
    data object Refresh: MyAlbumTabViewEvent()
    data object RefreshAll: MyAlbumTabViewEvent()
}

sealed class MyMemoryTabViewEvent: ViewEvent

sealed class ModifyUsernameViewEvent: ViewEvent {
    data class Complete(val username: String): ModifyUsernameViewEvent()
}

sealed class HtmlViewEvent