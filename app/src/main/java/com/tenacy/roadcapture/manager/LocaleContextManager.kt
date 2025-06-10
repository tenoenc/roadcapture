package com.tenacy.roadcapture.manager

import android.content.Context
import android.content.res.Configuration
import java.util.*

object LocaleContextManager {
    private var localizedAppContext: Context? = null

    fun updateContext(context: Context, locale: Locale) {
        val config = Configuration(context.resources.configuration)
        config.setLocale(locale)

        // 새 컨텍스트 생성 및 저장
        val newContext = context.createConfigurationContext(config)
        localizedAppContext = newContext

        // API 17 이하 호환성을 위해 리소스 설정도 업데이트
        context.resources.updateConfiguration(config, context.resources.displayMetrics)
    }

    fun getLocalizedContext(defaultContext: Context): Context {
        return localizedAppContext ?: defaultContext
    }
}