package com.tenacy.roadcapture.di

import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.tenacy.roadcapture.auth.FacebookOAuthLoginCallback
import com.tenacy.roadcapture.auth.GoogleOAuthLoginCallback
import com.tenacy.roadcapture.auth.KakaoOAuthLoginCallback
import com.tenacy.roadcapture.auth.NaverOAuthLoginCallback
import com.tenacy.roadcapture.ui.AppInfoViewModel
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.components.FragmentComponent
import dagger.hilt.android.scopes.FragmentScoped
import javax.inject.Named

@Module
@InstallIn(FragmentComponent::class)
class AuthFragmentModule {

    @Provides
    @FragmentScoped
    fun provideNaverOAuthLoginCallback(fragment: Fragment): NaverOAuthLoginCallback {
        return NaverOAuthLoginCallback(fragment)
    }

    @Provides
    @FragmentScoped
    @Named("reauth")
    fun provideNaverOAuthLoginReauthCallback(fragment: Fragment): NaverOAuthLoginCallback {
        return NaverOAuthLoginCallback(fragment) {
            ViewModelProvider(fragment)[AppInfoViewModel::class.java]
        }
    }

    @Provides
    @FragmentScoped
    fun provideKakaoOAuthLoginCallback(fragment: Fragment): KakaoOAuthLoginCallback {
        return KakaoOAuthLoginCallback(fragment)
    }

    @Provides
    @FragmentScoped
    @Named("reauth")
    fun provideKakaoOAuthLoginReauthCallback(fragment: Fragment): KakaoOAuthLoginCallback {
        return KakaoOAuthLoginCallback(fragment) {
            ViewModelProvider(fragment)[AppInfoViewModel::class.java]
        }
    }

    @Provides
    @FragmentScoped
    fun provideGoogleOAuthLoginCallback(fragment: Fragment): GoogleOAuthLoginCallback {
        return GoogleOAuthLoginCallback(fragment)
    }

    @Provides
    @FragmentScoped
    @Named("reauth")
    fun provideGoogleOAuthReauthCallback(fragment: Fragment): GoogleOAuthLoginCallback {
        return GoogleOAuthLoginCallback(fragment) {
            ViewModelProvider(fragment)[AppInfoViewModel::class.java]
        }
    }

    @Provides
    @FragmentScoped
    fun provideFacebookOAuthLoginCallback(fragment: Fragment): FacebookOAuthLoginCallback {
        return FacebookOAuthLoginCallback(fragment)
    }

    @Provides
    @FragmentScoped
    @Named("reauth")
    fun provideFacebookOAuthLoginReauthCallback(fragment: Fragment): FacebookOAuthLoginCallback {
        return FacebookOAuthLoginCallback(fragment) {
            ViewModelProvider(fragment)[AppInfoViewModel::class.java]
        }
    }
}