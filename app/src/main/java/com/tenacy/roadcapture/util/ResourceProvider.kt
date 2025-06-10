package com.tenacy.roadcapture.util

import android.content.Context
import kotlinx.coroutines.flow.StateFlow

interface ResourceProvider {
    val configurationContextFlow: StateFlow<Context>
    fun getString(resId: Int): String
    fun getString(resId: Int, vararg formatArgs: Any): String
    fun refreshConfigurationContext()
    fun getConfigurationContext(): Context
}