package com.tenacy.roadcapture.data.pref

import com.chibatching.kotpref.KotprefModel

object DebugSettings : KotprefModel() {
    // 디버그 모드에서 Mock Location 사용 여부
    var useMockLocationInDebugMode by booleanPref(default = false, key = "use_mock_location_debug")
}