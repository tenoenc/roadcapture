package com.tenacy.roadcapture.di

import com.facebook.CallbackManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
class AuthModule {

    @Provides
    fun provideFacebookCallbackManager(): CallbackManager {
        return CallbackManager.Factory.create()
    }
}