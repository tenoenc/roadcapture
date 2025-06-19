package com.tenacy.roadcapture.manager

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import com.tenacy.roadcapture.data.pref.DebugSettings
import java.util.*

object LocaleManager {

    fun applyLocale(context: Context): Context {
        val localeCode = DebugSettings.getSelectedLocale(context)
        if (localeCode.isEmpty() || localeCode == "system") return context

        val locale = createLocale(localeCode)
        return updateResources(context, locale)
    }

    fun setLocale(context: Context, localeCode: String) {
        // 먼저 SharedPreference에 저장
        DebugSettings.setSelectedLocale(context, localeCode)

        val locale = createLocale(localeCode)

        // 시스템 기본 로케일 설정
        Locale.setDefault(locale)

        // 현재 컨텍스트의 리소스 업데이트
        updateResources(context, locale)

        // Application 컨텍스트도 업데이트
        updateResources(context.applicationContext, locale)
    }

    private fun createLocale(localeCode: String): Locale {
        return when {
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
    }

    private fun updateResources(context: Context, locale: Locale): Context {
        val config = Configuration(context.resources.configuration)

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            // API 24 이상에서는 LocaleList 사용
            val localeList = LocaleList(locale)
            LocaleList.setDefault(localeList)
            config.setLocales(localeList)
            context.createConfigurationContext(config)
        } else {
            // API 23 이하에서는 기존 방식 사용
            config.setLocale(locale)
            context.createConfigurationContext(config)
        }
    }

    fun getCurrentLocale(context: Context): Locale {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            context.resources.configuration.locales[0]
        } else {
            context.resources.configuration.locale
        }
    }
}