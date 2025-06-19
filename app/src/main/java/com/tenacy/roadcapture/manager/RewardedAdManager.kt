package com.tenacy.roadcapture.manager

import android.content.Context
import android.util.Log
import androidx.core.content.ContextCompat
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.rewardedinterstitial.RewardedInterstitialAd
import com.google.android.gms.ads.rewardedinterstitial.RewardedInterstitialAdLoadCallback
import com.tenacy.roadcapture.BuildConfig
import com.tenacy.roadcapture.MainActivity
import com.tenacy.roadcapture.R
import com.tenacy.roadcapture.ui.GlobalViewEvent
import com.tenacy.roadcapture.ui.ToastMessageType
import com.tenacy.roadcapture.ui.ToastModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RewardedAdManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private var rewardedInterstitialAd: RewardedInterstitialAd? = null
    private var isNoFill = false

    // 앱 시작 시 호출하여 미리 광고 로드
    init {
        loadAd()
    }

    fun loadAd() {
        val adRequest = AdRequest.Builder().build()

        val adUnitId = BuildConfig.AD_MOB_APP_SAVE_MEMORY_ID
//        val adUnitId = if(BuildConfig.DEBUG || BuildConfig.DEVELOPMENT.toBoolean()) {
//            BuildConfig.AD_MOB_APP_UNIT_REWARD_TEST_ID
//        } else {
//            BuildConfig.AD_MOB_APP_SAVE_MEMORY_ID
//        }

        RewardedInterstitialAd.load(
            context,
            adUnitId,
            adRequest,
            object : RewardedInterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: RewardedInterstitialAd) {
                    Log.d("RewardedAdManager", "Ad was loaded.")
                    rewardedInterstitialAd = ad
                    isNoFill = false // 성공하면 리셋
                    setupAdCallbacks()
                }

                override fun onAdFailedToLoad(adError: LoadAdError) {
                    Log.d("RewardedAdManager", "Ad failed to load: ${adError.message}")
                    rewardedInterstitialAd = null
                    isNoFill = (adError.code == 3) // No fill인지 체크
                }
            }
        )
    }

    private fun setupAdCallbacks() {
        rewardedInterstitialAd?.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                Log.d("RewardedAdManager", "Ad was dismissed.")
                rewardedInterstitialAd = null
                // 다음 광고 미리 로드
                loadAd()
            }

            override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                Log.d("RewardedAdManager", "Ad failed to show: ${adError.message}")
                rewardedInterstitialAd = null
                loadAd()
            }

            override fun onAdShowedFullScreenContent() {
                Log.d("RewardedAdManager", "Ad showed fullscreen content.")
            }
        }
    }

    fun showAd(
        mainActivity: MainActivity,
        onRewarded: () -> Unit,
        onFailed: (() -> Unit)? = null
    ) {
        val ad = rewardedInterstitialAd

        if (ad != null) {
            // 광고가 있으면 보여주기
            ad.show(mainActivity) { _ -> onRewarded() }
        } else if (isNoFill) {
            // No fill이면 무료로 제공
            onRewarded()
            loadAd()
        } else {
            // 다른 에러면 실패 처리
            mainActivity.vm.viewEvent(GlobalViewEvent.Toast(ToastModel(ContextCompat.getString(context, R.string.try_again_later), ToastMessageType.Warning)))
            onFailed?.invoke()
            loadAd()
        }
    }
}