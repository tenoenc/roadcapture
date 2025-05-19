package com.tenacy.roadcapture

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import com.chibatching.kotpref.Kotpref
import com.facebook.FacebookSdk
import com.facebook.appevents.AppEventsLogger
import com.google.android.gms.ads.MobileAds
import com.google.android.libraries.places.api.Places
import com.google.firebase.FirebaseApp
import com.kakao.sdk.common.KakaoSdk
import com.navercorp.nid.NaverIdLoginSDK
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class RoadcaptureApplication: Application() {

    override fun onCreate() {
        super.onCreate()

        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)

        MobileAds.initialize(this)
        Places.initialize(applicationContext, BuildConfig.MAPS_API_KEY)
        FirebaseApp.initializeApp(this)
        NaverIdLoginSDK.initialize(applicationContext, BuildConfig.NAVER_CLIENT_ID, BuildConfig.NAVER_CLIENT_SECRET, BuildConfig.NAVER_CLIENT_NAME)
        KakaoSdk.init(this, BuildConfig.KAKAO_CLIENT_ID)
        FacebookSdk.sdkInitialize(this)
        AppEventsLogger.activateApp(this)
        Kotpref.init(this)
    }
}