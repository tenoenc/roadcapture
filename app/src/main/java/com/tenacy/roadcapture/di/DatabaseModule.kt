package com.tenacy.roadcapture.di

import android.content.Context
import com.tenacy.roadcapture.data.db.*
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@InstallIn(SingletonComponent::class)
@Module
class DatabaseModule {

    @Singleton
    @Provides
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase =  AppDatabase.getInstance(context)

    @Singleton
    @Provides
    fun provideMemoryDao(appDatabase: AppDatabase): MemoryDao = appDatabase.memoryDao()

    @Singleton
    @Provides
    fun provideLocationDao(appDatabase: AppDatabase): LocationDao = appDatabase.locationDao()

    @Singleton
    @Provides
    fun provideMemoryCacheDao(appDatabase: AppDatabase): MemoryCacheDao = appDatabase.memoryCacheDao()

    @Singleton
    @Provides
    fun provideLocationCacheDao(appDatabase: AppDatabase): LocationCacheDao = appDatabase.locationCacheDao()

    @Singleton
    @Provides
    fun provideCacheDao(appDatabase: AppDatabase): CacheDao = appDatabase.cacheDao()
}