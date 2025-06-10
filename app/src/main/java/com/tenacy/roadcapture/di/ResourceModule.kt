package com.tenacy.roadcapture.di

import android.content.Context
import android.content.res.Configuration
import com.chibatching.kotpref.Kotpref
import com.tenacy.roadcapture.data.pref.DebugSettings
import com.tenacy.roadcapture.util.LazyResourceProvider
import com.tenacy.roadcapture.util.ResourceProvider
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.util.*
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class ResourceModule {

    @Binds
    @Singleton
    abstract fun provideResourceProvider(impl: LazyResourceProvider): ResourceProvider

    companion object {
        @Provides
        @LocalizedContext
        fun provideLocalizedContext(@ApplicationContext context: Context): Context {
            if(!Kotpref.isInitialized) {
                Kotpref.init(context)
            }

            val localeCode = DebugSettings.getSelectedLocale(context) // 매번 최신 값 조회

            val locale = when {
                localeCode.contains("-") -> {
                    val parts = localeCode.split("-")
                    Locale(parts[0], parts[1])
                }
                else -> Locale(localeCode)
            }

            val config = Configuration(context.resources.configuration)
            config.setLocale(locale)
            return context.createConfigurationContext(config)
        }
    }
}