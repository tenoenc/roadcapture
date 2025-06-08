package com.tenacy.roadcapture

import android.app.Application
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.appcompat.app.AppCompatDelegate
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.facebook.FacebookSdk
import com.facebook.appevents.AppEventsLogger
import com.google.android.gms.ads.MobileAds
import com.google.android.libraries.places.api.Places
import com.google.firebase.FirebaseApp
import com.kakao.sdk.common.KakaoSdk
import com.navercorp.nid.NaverIdLoginSDK
import com.tenacy.roadcapture.data.pref.SubscriptionPref
import com.tenacy.roadcapture.data.pref.TravelPref
import com.tenacy.roadcapture.manager.*
import com.tenacy.roadcapture.worker.CleanupOldCachesWorker
import com.tenacy.roadcapture.worker.LocationCheckWorker
import com.tenacy.roadcapture.worker.SubscriptionCheckWorker
import dagger.hilt.EntryPoint
import dagger.hilt.EntryPoints
import dagger.hilt.InstallIn
import dagger.hilt.android.HiltAndroidApp
import dagger.hilt.components.SingletonComponent
import io.branch.referral.Branch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class RoadcaptureApplication: Application(), Configuration.Provider {

    @Inject
    lateinit var freepikNSFWDetector: FreepikNSFWDetector

    @Inject
    lateinit var billingManager: BillingManager

    @Inject
    lateinit var subscriptionManager: SubscriptionManager

    @Inject
    lateinit var donationManager: DonationManager

    @Inject
    lateinit var googleAccountManager: GoogleAccountManager

    // 앱 코루틴 스코프
    private val applicationScope = CoroutineScope(Dispatchers.Main)

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface HiltWorkerFactoryEntryPoint {
        fun workerFactory(): HiltWorkerFactory
    }

    override fun onCreate() {
        super.onCreate()

        FirebaseApp.initializeApp(this)
        MobileAds.initialize(this)
        Places.initialize(applicationContext, BuildConfig.MAPS_API_KEY)
        NaverIdLoginSDK.initialize(applicationContext, BuildConfig.NAVER_CLIENT_ID, BuildConfig.NAVER_CLIENT_SECRET, BuildConfig.NAVER_CLIENT_NAME)
        KakaoSdk.init(this, BuildConfig.KAKAO_CLIENT_ID)
        FacebookSdk.sdkInitialize(this)
        AppEventsLogger.activateApp(this)

        // Branch 로깅 활성화 (개발 시에만 활성화)
        Branch.enableLogging()

        // Branch 초기화 (앱 시작 시 자동 초기화)
        Branch.getAutoInstance(this)

        initializeBilling()
        setupSubscriptionCheckOneTime()
        setupCleanupOldCachesPeriodic()
        setupLocationCheck()

        // Google 계정 변경 감지 시작
        googleAccountManager.startPeriodicChecks()
    }

    override fun onTerminate() {
        // Google 계정 변경 감지 중지
        googleAccountManager.stopPeriodicChecks()

        freepikNSFWDetector.close()
        super.onTerminate()
    }

    override val workManagerConfiguration: Configuration =
        Configuration.Builder()
            .setWorkerFactory(EntryPoints.get(this, HiltWorkerFactoryEntryPoint::class.java).workerFactory())
            .build()


    // RoadcaptureApplication.kt의 setupLocationCheck() 메서드 수정
    private fun setupLocationCheck() {
        // 이전 여행 상태 확인 및 워커 등록 - TravelStatePref만 사용
        val isTraveling = TravelPref.isTraveling

        if (isTraveling) {
            Log.d("Application", "이전 여행 상태 감지 - LocationCheckWorker 등록")
            LocationCheckWorker.enqueuePeriodicWork(this)
            LocationCheckWorker.enqueueOneTimeWork(this)
        }
    }

    private fun setupCleanupOldCachesPeriodic() {
        CleanupOldCachesWorker.enqueuePeriodicWork(this)
        CleanupOldCachesWorker.enqueueOneTimeWork(this)
    }

    private fun setupSubscriptionCheckOneTime() {
        // 만료 시점에 정확히 체크
        val expiryTime = SubscriptionPref.subscriptionExpiryTime
        val currentTime = System.currentTimeMillis()

        if (expiryTime > currentTime) {
            SubscriptionCheckWorker.enqueueOneTimeWork(this, expiryTime - currentTime)
        }
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

                        // 초기화 성공 시 즉시 구독 상태 확인
                        applicationScope.launch {
                            try {
                                val isActive = subscriptionManager.checkSubscriptionStatus()
                                Log.d("App", "앱 시작 시 구독 상태 확인: $isActive")
                            } catch (e: Exception) {
                                Log.e("App", "앱 시작 시 구독 상태 확인 실패", e)
                            }
                        }
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