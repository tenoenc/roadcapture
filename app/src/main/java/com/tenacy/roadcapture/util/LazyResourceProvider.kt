package com.tenacy.roadcapture.util

import android.content.Context
import com.tenacy.roadcapture.di.LocalizedContext
import kotlinx.coroutines.flow.*
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

@Singleton
class LazyResourceProvider @Inject constructor(
    @LocalizedContext val contextProvider: Provider<Context>
) : ResourceProvider {

    private val _configurationContextFlow = MutableStateFlow(contextProvider.get())

    override val configurationContextFlow: StateFlow<Context>
        get() = _configurationContextFlow.asStateFlow()

    override fun getString(resId: Int): String {
        // 메서드 호출 시점에 최신 Context 가져오기
        return contextProvider.get().getString(resId)
    }

    override fun getString(resId: Int, vararg formatArgs: Any): String {
        return contextProvider.get().getString(resId, *formatArgs)
    }

    override fun refreshConfigurationContext() {
        _configurationContextFlow.update { contextProvider.get() }
    }

    override fun getConfigurationContext(): Context {
        return contextProvider.get()
    }
}