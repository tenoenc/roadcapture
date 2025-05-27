package com.tenacy.roadcapture

import android.app.Application
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.appcompat.app.AppCompatDelegate
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.*
import com.chibatching.kotpref.Kotpref
import com.facebook.FacebookSdk
import com.facebook.appevents.AppEventsLogger
import com.google.android.gms.ads.MobileAds
import com.google.android.libraries.places.api.Places
import com.google.firebase.FirebaseApp
import com.kakao.sdk.common.KakaoSdk
import com.navercorp.nid.NaverIdLoginSDK
import com.tenacy.roadcapture.data.pref.SubscriptionPref
import com.tenacy.roadcapture.manager.BillingManager
import com.tenacy.roadcapture.manager.DonationManager
import com.tenacy.roadcapture.manager.NSFWDetector
import com.tenacy.roadcapture.manager.SubscriptionManager
import com.tenacy.roadcapture.util.Constants
import com.tenacy.roadcapture.worker.SubscriptionCheckWorker
import dagger.hilt.EntryPoint
import dagger.hilt.EntryPoints
import dagger.hilt.InstallIn
import dagger.hilt.android.HiltAndroidApp
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@HiltAndroidApp
class RoadcaptureApplication: Application(), Configuration.Provider {

    @Inject
    lateinit var nsfwDetector: NSFWDetector

    @Inject
    lateinit var billingManager: BillingManager

    @Inject
    lateinit var subscriptionManager: SubscriptionManager

    @Inject
    lateinit var donationManager: DonationManager

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface HiltWorkerFactoryEntryPoint {
        fun workerFactory(): HiltWorkerFactory
    }

    override fun onCreate() {
        super.onCreate()

        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)

        nsfwDetector.initialize()
        MobileAds.initialize(this)
        Places.initialize(applicationContext, BuildConfig.MAPS_API_KEY)
        FirebaseApp.initializeApp(this)
        NaverIdLoginSDK.initialize(applicationContext, BuildConfig.NAVER_CLIENT_ID, BuildConfig.NAVER_CLIENT_SECRET, BuildConfig.NAVER_CLIENT_NAME)
        KakaoSdk.init(this, BuildConfig.KAKAO_CLIENT_ID)
        FacebookSdk.sdkInitialize(this)
        AppEventsLogger.activateApp(this)

        initializeBilling()
        setupSubscriptionCheck()
    }

    override fun onTerminate() {
        nsfwDetector.close()
        super.onTerminate()
    }

    override val workManagerConfiguration: Configuration =
        Configuration.Builder()
            .setWorkerFactory(EntryPoints.get(this, HiltWorkerFactoryEntryPoint::class.java).workerFactory())
            .build()

    private fun setupSubscriptionCheck() {
        // 만료 시점에 정확히 체크
        val expiryTime = SubscriptionPref.subscriptionExpiryTime
        val currentTime = System.currentTimeMillis()

        if (expiryTime > currentTime) {
            val delay = expiryTime - currentTime + Constants.EXPIRY_CHECK_DELAY_MS

            val expiryWork = OneTimeWorkRequestBuilder<SubscriptionCheckWorker>()
                .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                .build()

            WorkManager.getInstance(this).enqueueUniqueWork(
                Constants.WORK_NAME_EXPIRY_CHECK,
                ExistingWorkPolicy.REPLACE,
                expiryWork
            )
        }

        // 정기 체크
        val periodicWork = PeriodicWorkRequestBuilder<SubscriptionCheckWorker>(
            Constants.PERIODIC_CHECK_INTERVAL_MINUTES, TimeUnit.MINUTES
        )
            .setInitialDelay(Constants.INITIAL_DELAY_MINUTES, TimeUnit.MINUTES)
            .build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            Constants.WORK_NAME_PERIODIC_CHECK,
            ExistingPeriodicWorkPolicy.REPLACE,
            periodicWork
        )
    }

    // 애플리케이션 시작 시 빌링 서비스 초기화
    private fun initializeBilling() {
        // 빌링 매니저 초기화 (비동기)
        billingManager.initialize { billingInitSuccess ->
            if (billingInitSuccess) {
                Log.d("App", "빌링 클라이언트 초기화 성공")

                // 구독 매니저 초기화
                subscriptionManager.initialize { subscriptionInitSuccess ->
                    if (subscriptionInitSuccess) {
                        Log.d("App", "구독 상품 정보 사전 로드 성공")
                    } else {
                        Log.e("App", "구독 상품 정보 사전 로드 실패")
                    }
                }

                // 후원 매니저 초기화
                donationManager.initialize { donationInitSuccess ->
                    if (donationInitSuccess) {
                        Log.d("App", "후원 상품 정보 사전 로드 성공")
                    } else {
                        Log.e("App", "후원 상품 정보 사전 로드 실패")
                    }
                }
            } else {
                Log.e("App", "빌링 클라이언트 초기화 실패")
                // 5초 후 다시 시도
                Handler(Looper.getMainLooper()).postDelayed({
                    initializeBilling()
                }, 5000)
            }
        }
    }
}