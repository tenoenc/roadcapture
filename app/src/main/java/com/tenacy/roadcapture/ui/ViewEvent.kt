package com.tenacy.roadcapture.ui

import android.location.Location
import com.google.firebase.auth.AuthCredential
import com.tenacy.roadcapture.data.SocialType
import com.tenacy.roadcapture.ui.dto.Album

sealed interface ViewEvent

sealed class GlobalViewEvent: ViewEvent {
    data class Toast(val toast: ToastModel) : GlobalViewEvent()
    data class CopyToClipboard(val text: String) : GlobalViewEvent()
    data object Logout: GlobalViewEvent()
}

sealed class LoginViewEvent: ViewEvent {
    data class Login(val socialUserId: String, val socialType: SocialType, val authCredential: AuthCredential): LoginViewEvent()
    data object GoogleLogin: LoginViewEvent()
    data object FacebookLogin: LoginViewEvent()
    data object KakaoLogin: LoginViewEvent()
    data object NaverLogin: LoginViewEvent()
    data class Signup(val socialUserId: String, val socialType: SocialType, val socialProfileUrl: String, val authCredential: AuthCredential): LoginViewEvent()
    data class SocialError(val message: String?, val socialType: SocialType): LoginViewEvent()
}

sealed class MainViewEvent: ViewEvent {
    data object Logout: MainViewEvent()
    data object ShowTripBefore: MainViewEvent()
}

sealed class HomeViewEvent: ViewEvent {
    data object Search: HomeViewEvent()
    data object ReportComplete: HomeViewEvent()
}

sealed class AppInfoViewEvent: ViewEvent {
    data object InquireToDeveloper: AppInfoViewEvent()
    data class NavigateToHtml(val type: HtmlType): AppInfoViewEvent()
    data object ReviewApp: AppInfoViewEvent()
    data object ShowLogoutBefore: AppInfoViewEvent()
    data object Donate : AppInfoViewEvent()
    data object ShowSubscription : AppInfoViewEvent()
    data object ShowSubscriptionRestriction : AppInfoViewEvent()
    data object OpenPlayStoreSubscriptionManager : AppInfoViewEvent()
    data object ShowWithdrawBefore : AppInfoViewEvent()
    data object Withdraw : AppInfoViewEvent()
    sealed class Error(open val message: String?): AppInfoViewEvent() {
        data class Reauth(override val message: String?, val socialType: SocialType) : Error(message)
    }
}

sealed class ScrapViewEvent: ViewEvent {
    data object Search: ScrapViewEvent()
    data object ReportComplete: ScrapViewEvent()
}

sealed class MyAlbumViewEvent: ViewEvent {
    data object ShowMore: MyAlbumViewEvent()
}

sealed class TripViewEvent: ViewEvent {
    data object ResetCameraPosition: TripViewEvent()
    data class SetCamera(val coordinates: Location? = null, val zoom: Float? = null) : TripViewEvent()
    data object Capture: TripViewEvent()
    data object Back: TripViewEvent()
    data object ShowGuide: TripViewEvent()
    data object ShowSubscription: TripViewEvent()
    data object ZoomIn: TripViewEvent()
    data object ZoomOut: TripViewEvent()
    data object ShowAfter: TripViewEvent()
    data object ShowStopBefore: TripViewEvent()
    sealed class Error(open val message: String?): TripViewEvent() {
        data class Fetch(override val message: String?): Error(message)
    }
}

sealed class NewMemoryViewEvent: ViewEvent {
    data class ShowLocation(val address: String): NewMemoryViewEvent()
    data object ShowAd: NewMemoryViewEvent()
}


sealed class MemoryViewerViewEvent: ViewEvent {
    data class ShowLocation(val address: String): MemoryViewerViewEvent()
    data object MoveToPrevPage: MemoryViewerViewEvent()
    data object MoveToNextPage: MemoryViewerViewEvent()
    data object ShowInfo: MemoryViewerViewEvent()
    data class ResultBack(val coordinates: Location): MemoryViewerViewEvent()
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
    data class ResultBack(val coordinates: Location? = null, val deleted: Boolean = false): ModifiableMemoryViewerViewEvent()
}

sealed class NewAlbumViewEvent: ViewEvent {
    data object ShowCompleteBefore: NewAlbumViewEvent()
}

sealed class AlbumViewEvent: ViewEvent {
    data object ResetCameraPosition: AlbumViewEvent()
    data object ZoomIn: AlbumViewEvent()
    data object ZoomOut: AlbumViewEvent()
    data class SetCamera(val coordinates: Location? = null, val zoom: Float? = null): AlbumViewEvent()
    data class ShowInfo(val album: Album, val totalMemoryCount: Int): AlbumViewEvent()
    data class Share(val link: String?): AlbumViewEvent()
    data class NavigateToStudio(val userId: String): AlbumViewEvent()
    data class Forbidden(val message: String): AlbumViewEvent()
    data class ShowReport(val albumId: String): AlbumViewEvent()
    data object ReportComplete: AlbumViewEvent()
}

sealed class SearchViewEvent: ViewEvent {
    data object ReportComplete: SearchViewEvent()
}

sealed class MyAlbumTabViewEvent: ViewEvent {
    sealed class EnqueueComplete: MyAlbumTabViewEvent() {
        data object CreateShareLink: EnqueueComplete()
        data class UpdateAlbumPublic(val publicText: String): EnqueueComplete()
        data object DeleteAlbum: EnqueueComplete()
    }
}
sealed class MyMemoryTabViewEvent: ViewEvent

sealed class ModifyUsernameViewEvent: ViewEvent {
    data object Complete: ModifyUsernameViewEvent()
}

sealed class HtmlViewEvent

sealed class SignupUsernameViewEvent: ViewEvent {
    data class Next(val username: String): SignupUsernameViewEvent()
}

sealed class SignupProfileViewEvent: ViewEvent {
    data class Next(val defaultProfile: DefaultProfile): SignupProfileViewEvent()
}

sealed class SignupAgreementViewEvent : ViewEvent {
    data class NavigateToHtml(val type: HtmlType): SignupAgreementViewEvent()
    data object Start: SignupAgreementViewEvent()
}

sealed class MainBeforeViewEvent : ViewEvent {
    data object Complete : MainBeforeViewEvent()
    sealed class Error(open val message: String?): MainBeforeViewEvent() {
        data class Login(override val message: String?) : Error(message)
    }
}

sealed class WithdrawBeforeViewEvent : ViewEvent {
    data object Complete : WithdrawBeforeViewEvent()
    sealed class Error(open val message: String?): WithdrawBeforeViewEvent() {
        data class Withdraw(override val message: String?) : Error(message)
    }
}