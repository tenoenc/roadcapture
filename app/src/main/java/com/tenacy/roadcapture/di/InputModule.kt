package com.tenacy.roadcapture.di

import com.tenacy.roadcapture.ui.LengthFilter
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object InputModule {

    @Provides
    @Singleton
    @ContentFilter
    fun provideNameFilter() = LengthFilter(maxLength = 1500)
}