package com.tenacy.roadcapture.data.pref

import android.content.Context
import com.chibatching.kotpref.KotprefModel
import java.util.*

object DebugSettings : KotprefModel() {

    // 디버그 모드에서 Mock Location 사용 여부
    var useMockLocationInDebugMode by booleanPref(default = false, key = "use_mock_location_debug")

    fun setSelectedLocale(context: Context, value: String) {
        context.getSharedPreferences("debug_settings", Context.MODE_PRIVATE)
            .edit().putString("selected_locale", value).apply()
    }

    fun getSelectedLocale(context: Context): String =
        context.getSharedPreferences("debug_settings", Context.MODE_PRIVATE).getString("selected_locale", Locale.getDefault().language) ?: Locale.getDefault().language
}