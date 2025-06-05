package com.tenacy.roadcapture.di

import com.tenacy.roadcapture.service.DefaultLocationProcessor
import com.tenacy.roadcapture.service.LocationProcessor
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class LocationModule {

    @Binds
    @Singleton
    abstract fun provideLocationProcessor(impl: DefaultLocationProcessor): LocationProcessor
}