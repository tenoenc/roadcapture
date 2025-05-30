package com.tenacy.roadcapture.manager

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdLoader
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.nativead.NativeAd
import com.tenacy.roadcapture.BuildConfig
import java.util.Collections
import java.util.PriorityQueue
import java.util.concurrent.ConcurrentHashMap

/**
 * 광고 재사용 및 효율적인 로드를 위한 관리자 클래스
 */
class AdPoolManager(private val context: Context) {
    // 위치 기반으로 캐싱된 광고 저장
    private val adPool = ConcurrentHashMap<Int, NativeAd>()

    // 현재 로드 중인 광고 위치 추적
    private val loadingPositions = Collections.synchronizedSet(HashSet<Int>())

    // 우선 로드할 광고 위치 (스크롤 예측)
    private val priorityQueue = PriorityQueue<Int>()

    // 설정값
    private val adPreloadLimit = 3 // 미리 로드할 광고 수
    private val maxPoolSize = 15   // 최대 캐싱할 광고 수
    private val maxRetryCount = 3  // 최대 재시도 횟수
    private val initialRetryDelay = 1000L // 첫 재시도 지연시간 (ms)

    /**
     * 특정 위치의 캐싱된 광고 가져오기
     */
    fun getAdForPosition(position: Int): NativeAd? = adPool[position]

    /**
     * 특정 위치의 광고가 로드 중인지 확인
     */
    fun isLoading(position: Int): Boolean = loadingPositions.contains(position)

    /**
     * 위치를 로드 중으로 표시
     */
    fun markPositionLoading(position: Int) {
        loadingPositions.add(position)
    }

    /**
     * 광고 우선순위 큐에 위치 추가
     */
    fun addToPriorityQueue(position: Int) {
        if (!adPool.containsKey(position) && !loadingPositions.contains(position)) {
            priorityQueue.add(position)

            // 큐 크기 제한
            while (priorityQueue.size > adPreloadLimit) {
                priorityQueue.poll()
            }
        }
    }

    /**
     * 모든 우선순위 위치에 대해 광고 프리로드
     */
    fun preloadPriorityAds() {
        val positions = priorityQueue.toList()
        priorityQueue.clear()

        positions.forEach { position ->
            if (!adPool.containsKey(position) && !loadingPositions.contains(position)) {
                loadAdForPosition(position) { /* 콜백 무시 */ }
            }
        }
    }

    /**
     * 특정 위치의 광고 로드
     */
    fun loadAdForPosition(position: Int, callback: (NativeAd?) -> Unit) {
        // 이미 로드된 광고가 있거나 로드 중이면 건너뜀
        if (adPool.containsKey(position)) {
            callback(adPool[position])
            return
        }

        if (loadingPositions.contains(position)) {
            return
        }

        // 풀 크기 관리 - 최대 개수 초과 시 가장 오래된 항목 제거
        manageCacheSize()

        // 로드 시작
        markPositionLoading(position)
        loadAd(position, callback)
    }

    /**
     * 실제 광고 로드 함수
     */
    private fun loadAd(position: Int, callback: (NativeAd?) -> Unit, retryCount: Int = 0) {
        val adUnitId = if (BuildConfig.DEBUG) {
            BuildConfig.AD_MOB_APP_UNIT_NATIVE_TEST_ID
        } else {
            BuildConfig.AD_MOB_APP_HOME_ALBUM_TEST_ID
        }

        val adRequest = AdRequest.Builder()
            .setHttpTimeoutMillis(8000) // 8초 제한
            .build()

        try {
            val adLoader = AdLoader.Builder(context, adUnitId)
                .forNativeAd { nativeAd ->
                    adPool[position] = nativeAd
                    loadingPositions.remove(position)
                    callback(nativeAd)

                    Log.d(TAG, "광고 로드 성공: 위치 $position")

                    // 다음 우선순위 광고 로드
                    preloadPriorityAds()
                }
                .withAdListener(object : AdListener() {
                    override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                        val shouldRetry = retryCount < maxRetryCount &&
                                (loadAdError.code == AdRequest.ERROR_CODE_NETWORK_ERROR ||
                                        loadAdError.code == AdRequest.ERROR_CODE_INTERNAL_ERROR)

                        if (shouldRetry) {
                            // 지수 백오프 재시도
                            val delay = initialRetryDelay * (1 shl retryCount)
                            Log.d(TAG, "광고 로드 재시도: 위치 $position, 시도 ${retryCount + 1}, ${delay}ms 후")

                            Handler(Looper.getMainLooper()).postDelayed({
                                loadAd(position, callback, retryCount + 1)
                            }, delay)
                        } else {
                            Log.e(TAG, "광고 로드 실패: 위치 $position, 코드 ${loadAdError.code}, 메시지: ${loadAdError.message}")
                            loadingPositions.remove(position)
                            callback(null)
                        }
                    }
                })
                .build()

            adLoader.loadAd(adRequest)
        } catch (e: Exception) {
            Log.e(TAG, "광고 로드 예외 발생: 위치 $position", e)
            loadingPositions.remove(position)
            callback(null)
        }
    }

    /**
     * 캐시 크기 관리
     */
    private fun manageCacheSize() {
        if (adPool.size >= maxPoolSize) {
            // 가장 오래된 항목(키가 가장 작은 항목들) 제거
            val keysToRemove = adPool.keys.sorted().take(adPool.size - maxPoolSize + 1)
            keysToRemove.forEach { key ->
                adPool[key]?.destroy()
                adPool.remove(key)
                Log.d(TAG, "캐시에서 광고 제거: 위치 $key")
            }
        }
    }

    /**
     * 모든 광고 리소스 해제
     */
    fun destroy() {
        adPool.values.forEach { it.destroy() }
        adPool.clear()
        loadingPositions.clear()
        priorityQueue.clear()
        Log.d(TAG, "AdPoolManager 리소스 해제")
    }

    companion object {
        private const val TAG = "AdPoolManager"
    }
}