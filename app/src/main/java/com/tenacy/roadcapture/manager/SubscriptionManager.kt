package com.tenacy.roadcapture.manager

import android.app.Activity
import android.content.Context
import android.util.Log
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.QueryProductDetailsParams
import com.chibatching.kotpref.Kotpref
import com.tenacy.roadcapture.BuildConfig
import com.tenacy.roadcapture.data.api.dto.VerificationRequest
import com.tenacy.roadcapture.data.pref.SubscriptionPref
import com.tenacy.roadcapture.util.Constants
import com.tenacy.roadcapture.util.RetrofitInstance
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.Calendar
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * 단순화된 구독 관리 클래스
 *
 * Google Play Billing과만 연계하여 구독을 관리합니다.
 * - 구독 확인
 * - 구독 상태 제공 (활성/비활성)
 */
@Singleton
class SubscriptionManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val billingManager: BillingManager,
) {
    // 상수 정의
    companion object {
        private const val TAG = "SubscriptionManager"

        // 구독 관련 상수
        private const val SUBSCRIPTION_PRODUCT_ID = "subscription_premium"
        private const val SUBSCRIPTION_TYPE = "premium"

        // 테스트 모드 시간 설정
        private const val TEST_SUBSCRIPTION_DURATION_MINUTES = 5
    }

    // 코루틴 스코프
    private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    // 구독 상태 Flow
    private val _isSubscriptionActive = MutableStateFlow(false)
    val isSubscriptionActive: StateFlow<Boolean> = _isSubscriptionActive.asStateFlow()

    // 구독 상품 설정
    private val subscriptionProductIds = listOf(SUBSCRIPTION_PRODUCT_ID)
    private val subscriptionProductList = subscriptionProductIds.map {
        QueryProductDetailsParams.Product.newBuilder()
            .setProductId(it)
            .setProductType(BillingClient.ProductType.SUBS)
            .build()
    }

    // 구독 상품 정보 캐시
    private var productDetails: MutableMap<String, ProductDetails> = mutableMapOf()
    private var subscriptionOfferToken: String? = null

    // 리스너 설정 상태 및 콜백
    private var listenerSetup = false
    private var purchaseCallback: SubscriptionPurchaseCallback? = null

    // 구독 상태 리스너 목록
    private val subscriptionStateListeners = mutableListOf<SubscriptionStateListener>()

    init {
        Kotpref.init(context)

        // 초기 상태 설정 - 로컬 캐시 확인
        _isSubscriptionActive.value = SubscriptionPref.isSubscriptionActive

        // 구독 이벤트 리스너 설정
        setupPurchaseListener()
    }

    /**
     * 초기화 및 사전 로드 메서드
     */
    fun initialize(callback: ((Boolean) -> Unit)? = null) {
        if (!listenerSetup) {
            setupPurchaseListener()
        }
        preloadSubscriptionProducts(callback)
    }

    /**
     * 구독 이벤트 리스너 설정
     */
    private fun setupPurchaseListener() {
        if (listenerSetup) {
            Log.d(TAG, "리스너가 이미 설정되어 있음")
            return
        }

        coroutineScope.launch {
            billingManager.purchaseEvents
                .filter { event ->
                    event.purchaseType == BillingManager.PurchaseType.SUBSCRIPTION ||
                            (event.purchaseType == null && billingManager.getCurrentPurchaseType() == BillingManager.PurchaseType.SUBSCRIPTION)
                }
                .collect { event ->
                    Log.d(TAG, "구독 이벤트 수신: 타입=${event.type}")
                    handlePurchaseEvent(event)
                }
        }

        listenerSetup = true
        Log.d(TAG, "구독 이벤트 리스너 설정 완료")
    }

    /**
     * 구독 상품 정보 사전 로드
     */
    private fun preloadSubscriptionProducts(callback: ((Boolean) -> Unit)? = null) {
        billingManager.preloadProductDetails(subscriptionProductList) { success, detailsList ->
            if (success && detailsList.isNotEmpty()) {
                // 상품 정보 캐싱
                for (details in detailsList) {
                    productDetails[details.productId] = details

                    // 구독 상품의 경우 제공 토큰 저장
                    if (details.productType == BillingClient.ProductType.SUBS) {
                        val offers = details.subscriptionOfferDetails
                        if (!offers.isNullOrEmpty()) {
                            subscriptionOfferToken = offers[0].offerToken
                        }
                    }
                }
                Log.d(TAG, "구독 상품 정보 사전 로드 성공: ${detailsList.size}개 상품")
                callback?.invoke(true)
            } else {
                Log.e(TAG, "구독 상품 정보 사전 로드 실패")
                callback?.invoke(false)
            }
        }
    }

    /**
     * 구독 이벤트 처리
     */
    private suspend fun handlePurchaseEvent(event: BillingManager.PurchaseEvent) {
        when (event.type) {
            BillingManager.PurchaseEventType.SUCCESS -> {
                event.purchases?.forEach { purchase ->
                    if (subscriptionProductIds.any { purchase.products.contains(it) }) {
                        handlePurchase(purchase)
                    }
                }
            }

            BillingManager.PurchaseEventType.ERROR -> {
                purchaseCallback?.onSubscriptionPurchaseFailed(
                    event.billingResult.responseCode,
                    event.billingResult.debugMessage
                )
            }

            BillingManager.PurchaseEventType.CANCELED -> {
                Log.d(TAG, "구독이 취소되었습니다")
            }
        }
    }

    /**
     * 구독 구매 콜백 설정
     */
    fun setPurchaseCallback(callback: SubscriptionPurchaseCallback?) {
        this.purchaseCallback = callback
    }

    /**
     * 구독 상태 확인 (suspend 함수)
     *
     * Google Play에서 현재 구독 상태를 확인하고, 자동 갱신 상태 및 만료 시간을 체크합니다.
     */
    suspend fun checkSubscriptionStatus(): Boolean {
        val currentTime = System.currentTimeMillis()
        Log.d(TAG, "구독 상태 확인 시작")

        // 최대 재시도 횟수
        val maxRetries = 3
        var retryCount = 0
        var lastException: Exception? = null

        while (retryCount < maxRetries) {
            try {
                return suspendCancellableCoroutine { cont ->
                    billingManager.queryPurchases(BillingClient.ProductType.SUBS) { billingResult, purchases ->
                        if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                            Log.d(TAG, "Google Play 구독 정보 조회 성공: ${purchases.size}개 구독 발견")

                            CoroutineScope(cont.context).launch {
                                try {
                                    // 활성 구독 찾기
                                    val activeSubscription = purchases.find {
                                        it.purchaseState == Purchase.PurchaseState.PURCHASED && it.isAcknowledged
                                    }

                                    if (activeSubscription != null) {
                                        Log.d(TAG, "활성 구독 발견: 토큰=${activeSubscription.purchaseToken.take(8)}...")

                                        // 구독 정보 가져오기 시도
                                        val expiryTimeMillis = getExpiryTimeMillis(activeSubscription)

                                        // 만료 여부 확인
                                        val isActive = expiryTimeMillis > currentTime

                                        // 로컬 상태 업데이트
                                        updateLocalSubscription(
                                            isActive,
                                            activeSubscription,
                                            expiryTimeMillis
                                        )

                                        // Flow 업데이트
                                        _isSubscriptionActive.value = isActive

                                        cont.resume(isActive)
                                    } else {
                                        Log.d(TAG, "활성 구독 찾을 수 없음")

                                        // 구독 비활성화
                                        updateLocalSubscription(false, null, 0)
                                        _isSubscriptionActive.value = false

                                        cont.resume(false)
                                    }
                                } catch (e: Exception) {
                                    Log.e(TAG, "구독 확인 중 오류 발생", e)
                                    // 오류 발생 시 현재 로컬 상태 유지
                                    val localIsActive = SubscriptionPref.isSubscriptionActive
                                    cont.resume(localIsActive)
                                }
                            }
                        } else {
                            Log.d(TAG, "Google Play 구독 조회 실패: ${billingResult.responseCode}, ${billingResult.debugMessage}")

                            // 일시적인 오류인 경우 재시도 대상
                            if (isRetryableError(billingResult.responseCode)) {
                                retryCount++
                                cont.resumeWithException(
                                    Exception("재시도 가능한 오류: ${billingResult.responseCode}")
                                )
                            } else {
                                // 영구적 오류인 경우 로컬 상태 반환
                                val localIsActive = SubscriptionPref.isSubscriptionActive
                                cont.resume(localIsActive)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                lastException = e
                Log.e(TAG, "재시도 중 (${retryCount+1}/$maxRetries): ${e.message}")
                retryCount++

                // 재시도 전 짧게 대기
                kotlinx.coroutines.delay(1000)
            }
        }

        // 모든 재시도 실패 시 로컬 상태 반환
        Log.e(TAG, "모든 재시도 실패, 로컬 상태 사용", lastException)
        return SubscriptionPref.isSubscriptionActive
    }

    /**
     * 재시도 가능한 오류인지 확인
     */
    private fun isRetryableError(responseCode: Int): Boolean {
        return when (responseCode) {
            BillingClient.BillingResponseCode.SERVICE_UNAVAILABLE,
            BillingClient.BillingResponseCode.SERVICE_DISCONNECTED,
            BillingClient.BillingResponseCode.SERVICE_TIMEOUT,
            BillingClient.BillingResponseCode.NETWORK_ERROR -> true
            else -> false
        }
    }

    /**
     * 구독 처리
     */
    private suspend fun handlePurchase(purchase: Purchase) {
        if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
            // 만료 시간 계산
            val expiryTimeMillis = getExpiryTimeMillis(purchase)
            val currentTime = System.currentTimeMillis()

            // 구매 확인 처리
            if (!purchase.isAcknowledged) {
                try {
                    acknowledgePurchase(purchase, expiryTimeMillis)
                } catch (e: Exception) {
                    Log.e(TAG, "구매 확인 실패", e)
                }
            } else {
                // 만료 여부 확인
                val isActive = expiryTimeMillis > currentTime
                val isCanceled = !purchase.isAutoRenewing

                // 로컬 상태 업데이트
                updateLocalSubscription(isActive, purchase, expiryTimeMillis)

                // Flow 업데이트
                _isSubscriptionActive.value = isActive
            }
        }
    }

    /**
     * 구매 확인 처리
     */
    private suspend fun acknowledgePurchase(purchase: Purchase, expiryTimeMillis: Long) {
        Log.d(TAG, "구매 확인 시작: 토큰=${purchase.purchaseToken.take(8)}")

        suspendCancellableCoroutine { cont ->
            billingManager.acknowledgePurchase(purchase.purchaseToken) { billingResult ->
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    Log.d(TAG, "구매 확인 성공 (acknowledgePurchase)")

                    CoroutineScope(cont.context).launch {
                        try {
                            val currentTime = System.currentTimeMillis()
                            val isActive = expiryTimeMillis > currentTime
                            val isCanceled = !purchase.isAutoRenewing

                            // 로컬 업데이트
                            updateLocalSubscription(isActive, purchase, expiryTimeMillis)

                            // Flow 업데이트
                            _isSubscriptionActive.value = isActive

                            // 구독 완료 콜백 호출
                            purchaseCallback?.onSubscriptionPurchaseCompleted(purchase)

                            cont.resume(Unit)
                        } catch (e: Exception) {
                            Log.e(TAG, "구독 확인 후 처리 중 오류 발생", e)
                            cont.resumeWithException(e)
                        }
                    }
                } else {
                    val errorMessage = "구매 확인 실패: 코드=${billingResult.responseCode}, 메시지=${billingResult.debugMessage}"
                    Log.e(TAG, errorMessage)
                    cont.resumeWithException(RuntimeException(errorMessage))
                }
            }
        }
    }

    /**
     * 만료 시간 정보 가져오기
     *
     * Google Play API에서 제공하는 실제 만료 시간을 가져오거나,
     * 정보가 없는 경우 대체 계산을 수행합니다.
     */
    private suspend fun getExpiryTimeMillis(purchase: Purchase): Long {
        try {
            // 1. 먼저 서버에서 구독 정보 확인 시도
            val request = VerificationRequest(BuildConfig.APPLICATION_ID, "subscription_premium", purchase.purchaseToken)
            val response = RetrofitInstance.firebaseApi.verifySubscription(request)

            if (response.isSuccessful && response.body()?.expiryTimeMillis != null) {
                val expiryTimeMillis = response.body()!!.expiryTimeMillis
                Log.d(TAG, "서버에서 가져온 만료 시간: ${Date(expiryTimeMillis)}")

                // 성공적으로 받아온 값 캐싱
                SubscriptionPref.lastKnownExpiryTime = expiryTimeMillis
                return expiryTimeMillis
            } else {
                Log.w(TAG, "서버에서 구독 정보를 가져오지 못함: ${response.code()}, ${response.message()}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "서버 연결 중 오류 발생", e)
        }

        // 2. 서버에서 가져오지 못한 경우 대체 전략

        // 2.1 캐시된 마지막 유효한 만료 시간이 현재 시간보다 미래인 경우
        if (SubscriptionPref.lastKnownExpiryTime > System.currentTimeMillis()) {
            Log.d(TAG, "캐시된 만료 시간 사용: ${Date(SubscriptionPref.lastKnownExpiryTime)}")
            return SubscriptionPref.lastKnownExpiryTime
        }

        // 2.2 Google Play에서 가져온 구매 정보 활용
        // Google Play에서는 정확한 만료 시간을 제공하지 않으므로, 구독 유형에 따라 추정

        // purchaseTime을 기준으로 구독 기간 추정
        return if (BuildConfig.DEBUG) {
            // 디버그 모드: 테스트용 짧은 시간
            purchase.purchaseTime + TEST_SUBSCRIPTION_DURATION_MINUTES * Constants.MILLIS_PER_MINUTES
        } else {
            // 릴리스 모드: 달력 기반 계산
            val purchaseDate = Calendar.getInstance()
            purchaseDate.timeInMillis = purchase.purchaseTime

            // 구독 유형에 따라 적절한 기간 추가
            val expiryDate = Calendar.getInstance()
            expiryDate.timeInMillis = purchase.purchaseTime

            // subscription_premium의 경우 1개월 추가
            expiryDate.add(Calendar.MONTH, 1)

            // 정확한 만료 시간 반환
            expiryDate.timeInMillis
        }
    }

    /**
     * 로컬 구독 정보 업데이트
     */
    private fun updateLocalSubscription(isActive: Boolean, purchase: Purchase?, expiryTime: Long) {
        SubscriptionPref.apply {
            this.isSubscriptionActive = isActive
            this.subscriptionExpiryTime = expiryTime

            if (isActive && purchase != null) {
                this.isSubscriptionCancelled = !purchase.isAutoRenewing
                this.purchaseToken = purchase.purchaseToken
                this.subscriptionPurchaseTime = purchase.purchaseTime
                this.subscriptionType = SUBSCRIPTION_TYPE
            }

            if (!isActive) {
                this.isSubscriptionActive = false
                this.subscriptionExpiryTime = 0
                this.isSubscriptionCancelled = false
                this.purchaseToken = ""
                this.subscriptionPurchaseTime = 0
                this.subscriptionType = ""
            }

            this.lastSubscriptionCheckTime = System.currentTimeMillis()
        }
    }

    /**
     * 구독 시작
     */
    fun subscribe(activity: Activity, callback: SubscriptionPurchaseCallback? = null) {
        // 임시 콜백 설정 (필요한 경우)
        if (callback != null) {
            this.purchaseCallback = callback
        }

        // 연결 상태 확인 및 필요시 재연결
        billingManager.reconnectIfNeeded()

        // 구독 상품 확인
        if (productDetails.isEmpty()) {
            preloadSubscriptionProducts { success ->
                if (success) {
                    launchBillingFlow(activity)
                } else {
                    purchaseCallback?.onSubscriptionPurchaseFailed(
                        BillingClient.BillingResponseCode.ITEM_UNAVAILABLE,
                        "구독 상품 정보를 불러오는 중입니다. 잠시 후 다시 시도해주세요."
                    )
                }
            }
        } else {
            launchBillingFlow(activity)
        }
    }

    /**
     * 구매 흐름 시작
     */
    private fun launchBillingFlow(activity: Activity) {
        val details = productDetails[SUBSCRIPTION_PRODUCT_ID]
        if (details == null) {
            purchaseCallback?.onSubscriptionPurchaseFailed(
                BillingClient.BillingResponseCode.ITEM_UNAVAILABLE,
                "구독 상품 정보를 찾을 수 없습니다"
            )
            return
        }

        val billingResult = billingManager.launchBillingFlow(
            activity,
            details,
            BillingManager.PurchaseType.SUBSCRIPTION,
            subscriptionOfferToken
        )

        if (billingResult.responseCode != BillingClient.BillingResponseCode.OK) {
            purchaseCallback?.onSubscriptionPurchaseFailed(
                billingResult.responseCode,
                billingResult.debugMessage
            )
        }
    }

    /**
     * 구독 상태 관찰을 위한 이벤트 핸들러 인터페이스
     */
    interface SubscriptionStateListener {
        fun onSubscriptionStateChanged(isActive: Boolean, isCanceled: Boolean)
    }

    /**
     * 구독 상태 리스너 등록
     */
    fun addSubscriptionStateListener(listener: SubscriptionStateListener) {
        if (!subscriptionStateListeners.contains(listener)) {
            subscriptionStateListeners.add(listener)
        }
    }

    /**
     * 구독 상태 리스너 제거
     */
    fun removeSubscriptionStateListener(listener: SubscriptionStateListener) {
        subscriptionStateListeners.remove(listener)
    }

    /**
     * Google 계정 변경 감지 및 업데이트
     *
     * 이 메서드는 Google 계정 변경을 감지하면 구독 상태를 다시 확인합니다.
     * Activity나 Fragment에서 계정 변경 이벤트를 감지할 때 호출해야 합니다.
     */
    fun onGoogleAccountChanged() {
        Log.d(TAG, "Google 계정 변경 감지됨, 구독 상태 확인 중...")
        coroutineScope.launch {
            try {
                val isActive = checkSubscriptionStatus()
                Log.d(TAG, "Google 계정 변경 후 구독 상태: $isActive")
            } catch (e: Exception) {
                Log.e(TAG, "Google 계정 변경 후 구독 상태 확인 실패", e)
            }
        }
    }

    /**
     * 구독 정보 가져오기 (BillingManager 확장 메서드)
     *
     * 실제 구현은 사용 중인 Billing 라이브러리 버전에 맞게 조정해야 합니다.
     */
    private fun BillingManager.getSubscriptionInfo(purchase: Purchase): SubscriptionInfo? {
        // 이 부분은 실제 BillingManager와 Billing 라이브러리 구현에 맞게 수정 필요
        try {
            // 예시: Purchase 객체에서 구독 정보 추출
            Log.d(TAG, "구독 정보 가져오기 시도")

            // 임시 반환값 (실제 구현에서는 대체)
            return null
        } catch (e: Exception) {
            Log.e(TAG, "구독 정보 가져오기 실패", e)
            return null
        }
    }

    /**
     * 구독 정보 데이터 클래스
     */
    data class SubscriptionInfo(
        val expiryTimeMillis: Long = 0,
        val isAutoRenewing: Boolean = false
    )

    /**
     * 구독 구매 콜백 인터페이스
     */
    interface SubscriptionPurchaseCallback {
        fun onSubscriptionPurchaseCompleted(purchase: Purchase)
        fun onSubscriptionPurchaseFailed(errorCode: Int, errorMessage: String)
    }
}