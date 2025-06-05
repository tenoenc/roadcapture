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
    @AlbumTitleFilter
    fun provideAlbumTitleFilter() = LengthFilter(maxLength = MAX_LENGTH_ALBUM_TITLE)

    @Provides
    @Singleton
    @PlaceNameFilter
    fun providePlaceNameFilter() = LengthFilter(maxLength = MAX_LENGTH_PLACE_NAME)

    @Provides
    @Singleton
    @ContentFilter
    fun provideContentFilter() = LengthFilter(maxLength = MAX_LENGTH_CONTENT)

    @Provides
    @Singleton
    @UsernameFilter
    fun provideUsername() = LengthFilter(maxLength = MAX_LENGTH_USERNAME)

    const val MAX_LENGTH_ALBUM_TITLE = 30
    const val MAX_LENGTH_PLACE_NAME = 30
    const val MAX_LENGTH_CONTENT = 1500
    const val MAX_LENGTH_USERNAME = 15
}