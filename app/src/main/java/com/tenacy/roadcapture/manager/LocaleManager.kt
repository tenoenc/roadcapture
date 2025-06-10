package com.tenacy.roadcapture.manager

import android.content.Context
import android.content.res.Configuration
import com.tenacy.roadcapture.BuildConfig
import com.tenacy.roadcapture.data.pref.DebugSettings
import java.util.*

object LocaleManager {

    fun applyLocale(context: Context): Context {
        if (!BuildConfig.DEBUG) return context

        val localeCode = DebugSettings.getSelectedLocale(context)
        if (localeCode.isEmpty() || localeCode == "system") return context

        val locale = when {
            localeCode.contains("-") -> {
                val parts = localeCode.split("-")
                when (parts.size) {
                    2 -> Locale(parts[0], parts[1])
                    3 -> Locale(parts[0], parts[1], parts[2])
                    else -> Locale(localeCode)
                }
            }

            else -> Locale(localeCode)
        }
        Locale.setDefault(locale)
        val config = Configuration(context.resources.configuration)
        config.setLocale(locale)
        return context.createConfigurationContext(config)
    }

    fun setLocale(context: Context, localeCode: String) {
        DebugSettings.setSelectedLocale(context, localeCode)

        val locale = when {
            localeCode.contains("-") -> {
                val parts = localeCode.split("-")
                when (parts.size) {
                    2 -> Locale(parts[0], parts[1])
                    3 -> Locale(parts[0], parts[1], parts[2])
                    else -> Locale(localeCode)
                }
            }
            else -> Locale(localeCode)
        }

        Locale.setDefault(locale)

        val config = Configuration(context.resources.configuration)
        config.setLocale(locale)

        context.createConfigurationContext(config)
        context.resources.updateConfiguration(config, context.resources.displayMetrics)
    }

}