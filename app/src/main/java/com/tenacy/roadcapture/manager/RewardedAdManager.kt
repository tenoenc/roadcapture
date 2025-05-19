package com.tenacy.roadcapture.manager

import android.content.Context
import android.util.Log
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.rewardedinterstitial.RewardedInterstitialAd
import com.google.android.gms.ads.rewardedinterstitial.RewardedInterstitialAdLoadCallback
import com.tenacy.roadcapture.BuildConfig
import com.tenacy.roadcapture.MainActivity
import com.tenacy.roadcapture.ui.GlobalViewEvent
import com.tenacy.roadcapture.ui.ToastMessageType
import com.tenacy.roadcapture.ui.ToastModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 보상형 전면 광고를 관리하는 클래스
 * Hilt를 통해 싱글톤으로 주입받아 사용
 */
@Singleton
class RewardedAdManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private var rewardedInterstitialAd: RewardedInterstitialAd? = null

    // 광고 로딩 상태를 관찰할 수 있는 StateFlow
    private val _adLoadingState = MutableStateFlow<AdLoadingState>(AdLoadingState.NotLoaded)
    val adLoadingState: StateFlow<AdLoadingState> = _adLoadingState.asStateFlow()

    // 앱 시작 시 호출하여 미리 광고 로드
    init {
        loadAd()
    }

    /**
     * 보상형 전면 광고 로드
     */
    fun loadAd() {
        if (_adLoadingState.value == AdLoadingState.Loading) {
            return
        }

        _adLoadingState.value = AdLoadingState.Loading

        val adRequest = AdRequest.Builder().build()

        RewardedInterstitialAd.load(
            context,
            BuildConfig.AD_MOB_APP_UNIT_REWARD_TEST_ID,
            adRequest,
            object : RewardedInterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: RewardedInterstitialAd) {
                    Log.d("RewardedAdManager", "Ad was loaded.")
                    rewardedInterstitialAd = ad
                    _adLoadingState.value = AdLoadingState.Loaded
                    setupAdCallbacks()
                }

                override fun onAdFailedToLoad(adError: LoadAdError) {
                    Log.d("RewardedAdManager", "Ad failed to load: ${adError.message}")
                    rewardedInterstitialAd = null
                    _adLoadingState.value = AdLoadingState.Failed(adError.message)
                }
            }
        )
    }

    /**
     * 광고 콜백 설정
     */
    private fun setupAdCallbacks() {
        rewardedInterstitialAd?.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                Log.d("RewardedAdManager", "Ad was dismissed.")
                rewardedInterstitialAd = null
                _adLoadingState.value = AdLoadingState.NotLoaded
                // 다음 광고 미리 로드
                loadAd()
            }

            override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                Log.d("RewardedAdManager", "Ad failed to show: ${adError.message}")
                rewardedInterstitialAd = null
                _adLoadingState.value = AdLoadingState.Failed(adError.message)
                loadAd()
            }

            override fun onAdShowedFullScreenContent() {
                Log.d("RewardedAdManager", "Ad showed fullscreen content.")
            }
        }
    }

    /**
     * 보상형 전면 광고 표시
     * @param mainActivity 현재 활성화된 Activity
     * @param onRewarded 광고 시청 완료 후 실행될 콜백
     * @param onFailed 광고 표시 실패 시 실행될 콜백 (선택 사항)
     */
    fun showAd(
        mainActivity: MainActivity,
        onRewarded: () -> Unit,
        onFailed: (() -> Unit)? = null
    ) {
        val ad = rewardedInterstitialAd

        if (ad != null) {
            ad.show(mainActivity) { _ ->
                // 콜백 실행
                onRewarded()
            }
        } else {
            // 광고가 로드되지 않은 경우
            mainActivity.lifecycleScope.launch {
                mainActivity.vm.viewEvent(GlobalViewEvent.Toast(ToastModel("광고를 준비하고 있습니다. 잠시 후 다시 시도해주세요.", ToastMessageType.Warning)))
            }

            // 실패 콜백 호출
            onFailed?.invoke()

            // 광고 다시 로드
            loadAd()
        }
    }

    /**
     * 광고 로딩 상태
     */
    sealed class AdLoadingState {
        data object NotLoaded : AdLoadingState()
        data object Loading : AdLoadingState()
        data object Loaded : AdLoadingState()
        data class Failed(val errorMessage: String) : AdLoadingState()
    }
}