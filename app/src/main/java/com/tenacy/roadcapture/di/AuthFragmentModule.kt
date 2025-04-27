package com.tenacy.roadcapture.di

import androidx.fragment.app.Fragment
import com.tenacy.roadcapture.auth.FacebookOAuthLoginCallback
import com.tenacy.roadcapture.auth.GoogleOAuthLoginCallback
import com.tenacy.roadcapture.auth.KakaoOAuthLoginCallback
import com.tenacy.roadcapture.auth.NaverOAuthLoginCallback
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.components.FragmentComponent
import dagger.hilt.android.scopes.FragmentScoped

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
    fun provideKakaoOAuthLoginCallback(fragment: Fragment): KakaoOAuthLoginCallback {
        return KakaoOAuthLoginCallback(fragment)
    }

    @Provides
    @FragmentScoped
    fun provideGoogleOAuthLoginCallback(fragment: Fragment): GoogleOAuthLoginCallback {
        return GoogleOAuthLoginCallback(fragment)
    }

    @Provides
    @FragmentScoped
    fun provideFacebookOAuthLoginCallback(fragment: Fragment): FacebookOAuthLoginCallback {
        return FacebookOAuthLoginCallback(fragment)
    }
}