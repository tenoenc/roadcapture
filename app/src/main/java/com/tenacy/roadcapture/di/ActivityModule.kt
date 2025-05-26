package com.tenacy.roadcapture.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ActivityComponent
import dagger.hilt.android.qualifiers.ActivityContext
import dagger.hilt.android.scopes.ActivityScoped

@Module
@InstallIn(ActivityComponent::class)
object ActivityModule {

    // Activity를 직접 제공하는 대신 ActivityContext를 사용
    @Provides
    @ActivityScoped
    fun provideActivityContext(@ActivityContext context: Context): Context {
        return context
    }
}