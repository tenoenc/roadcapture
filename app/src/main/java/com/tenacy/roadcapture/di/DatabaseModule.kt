package com.tenacy.roadcapture.di

import android.content.Context
import com.tenacy.roadcapture.data.db.AppDatabase
import com.tenacy.roadcapture.data.db.LocationDao
import com.tenacy.roadcapture.data.db.MemoryDao
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
}