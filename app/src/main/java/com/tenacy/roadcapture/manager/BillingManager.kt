package com.tenacy.roadcapture.manager

import android.app.Activity
import android.content.Context
import android.util.Log
import com.android.billingclient.api.*
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BillingManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    // 방법 1: Flow 대신 StateFlow 사용 (가장 간단한 방법)
    private val _purchaseEvents = MutableStateFlow<PurchaseEvent?>(null)
    val purchaseEvents = _purchaseEvents
        .filterNotNull() // null 값은 필터링
        .distinctUntilChanged() // 중복 이벤트 제거

    // 처리된 주문 ID 추적
    private val processedOrderIds = mutableSetOf<String>()

    // 마지막 실행된 구매 타입 추적
    private var lastPurchaseType: PurchaseType? = null

    private lateinit var billingClient: BillingClient

    // 1. 개선된 초기화 메서드 추가
    fun initialize(callback: ((Boolean) -> Unit)? = null) {
        if (::billingClient.isInitialized && billingClient.isReady) {
            callback?.invoke(true)
            return
        }

        billingClient = BillingClient.newBuilder(context)
            .setListener(purchasesUpdatedListener)
            .enablePendingPurchases()
            .build()

        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: BillingResult) {
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    Log.d(TAG, "빌링 클라이언트 연결 성공")
                    callback?.invoke(true)
                } else {
                    Log.e(TAG, "빌링 클라이언트 설정 실패: ${billingResult.debugMessage}")
                    callback?.invoke(false)
                }
            }

            override fun onBillingServiceDisconnected() {
                Log.w(TAG, "빌링 서비스 연결 끊김")
                // 콜백을 호출하지 않음 - 서비스 연결이 끊긴 경우는 초기화 성공 여부를 결정할 수 없음
            }
        })
    }

    // 구매 업데이트 리스너
    // 구매 업데이트 리스너
    private val purchasesUpdatedListener = PurchasesUpdatedListener { billingResult, purchases ->
        // 현재 진행 중인 구매 타입 가져오기
        val currentPurchaseType = lastPurchaseType

        if (billingResult.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
            // 구매 성공 - 중복되지 않는 구매만 처리
            val newPurchases = purchases.filter { !processedOrderIds.contains(it.orderId) }
            if (newPurchases.isNotEmpty()) {
                // 주문 ID 추적에 추가
                newPurchases.forEach { it.orderId?.let(processedOrderIds::add) }
                // 이벤트 발행 (구매 타입 포함)
                _purchaseEvents.value = PurchaseEvent(PurchaseEventType.SUCCESS, billingResult, newPurchases, currentPurchaseType)
                // 또는 SharedFlow를 사용하는 경우: _purchaseEvents.tryEmit(...)
            }
        } else if (billingResult.responseCode == BillingClient.BillingResponseCode.USER_CANCELED) {
            // 사용자 취소 (구매 타입 포함)
            _purchaseEvents.value = PurchaseEvent(PurchaseEventType.CANCELED, billingResult, null, currentPurchaseType)
            // 또는: _purchaseEvents.tryEmit(...)
        } else {
            // 기타 오류 (구매 타입 포함)
            _purchaseEvents.value = PurchaseEvent(PurchaseEventType.ERROR, billingResult, null, currentPurchaseType)
            // 또는: _purchaseEvents.tryEmit(...)
        }
    }

    // 2. 개선된 상품 정보 로드 메서드
    fun preloadProductDetails(
        productList: List<QueryProductDetailsParams.Product>,
        callback: ((Boolean, List<ProductDetails>) -> Unit)? = null
    ) {
        if (!::billingClient.isInitialized || !billingClient.isReady) {
            initialize { success ->
                if (success) {
                    queryProductDetailsInternal(productList, callback)
                } else {
                    callback?.invoke(false, emptyList())
                }
            }
        } else {
            queryProductDetailsInternal(productList, callback)
        }
    }

    private fun queryProductDetailsInternal(
        productList: List<QueryProductDetailsParams.Product>,
        callback: ((Boolean, List<ProductDetails>) -> Unit)?
    ) {
        if (productList.isEmpty()) {
            Log.e(TAG, "상품 목록이 비어 있음")
            callback?.invoke(false, emptyList())
            return
        }

        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(productList)
            .build()

        billingClient.queryProductDetailsAsync(params) { billingResult, detailsList ->
            val success = billingResult.responseCode == BillingClient.BillingResponseCode.OK

            if (success) {
                if (detailsList.isNotEmpty()) {
                    Log.d(TAG, "상품 정보 조회 성공: ${detailsList.size}개 상품")
                } else {
                    Log.w(TAG, "상품 정보 조회 성공했으나 상품이 없음")
                }
            } else {
                Log.e(TAG, "상품 정보 조회 실패: ${billingResult.responseCode} - ${billingResult.debugMessage}")
            }

            callback?.invoke(success, detailsList)
        }
    }

    private fun loadProducts(
        productList: List<QueryProductDetailsParams.Product>,
        callback: ((Boolean) -> Unit)? = null
    ) {
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(productList)
            .build()

        billingClient.queryProductDetailsAsync(params) { billingResult, _ ->
            callback?.invoke(billingResult.responseCode == BillingClient.BillingResponseCode.OK)
        }
    }

    // 상품 구매 이력 조회
    fun queryPurchases(productType: String, callback: (BillingResult, List<Purchase>) -> Unit) {
        if (!isClientReady()) {
            callback(
                BillingResult.newBuilder()
                    .setResponseCode(BillingClient.BillingResponseCode.SERVICE_UNAVAILABLE)
                    .setDebugMessage("BillingClient가 준비되지 않았습니다")
                    .build(),
                emptyList()
            )
            return
        }

        billingClient.queryPurchasesAsync(productType, callback)
    }

    // 상품 정보 조회
    fun queryProductDetails(
        productList: List<QueryProductDetailsParams.Product>,
        callback: (BillingResult, List<ProductDetails>) -> Unit
    ) {
        if (!isClientReady()) {
            callback(
                BillingResult.newBuilder()
                    .setResponseCode(BillingClient.BillingResponseCode.SERVICE_UNAVAILABLE)
                    .setDebugMessage("BillingClient가 준비되지 않았습니다")
                    .build(),
                emptyList()
            )
            return
        }

        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(productList)
            .build()

        billingClient.queryProductDetailsAsync(params, callback)
    }

    // 구매 흐름 시작 - 구매 타입 설정 추가
    fun launchBillingFlow(
        activity: Activity,
        productDetails: ProductDetails,
        purchaseType: PurchaseType,
        offerToken: String? = null
    ): BillingResult {
        if (!isClientReady()) {
            // 클라이언트가 준비되지 않았을 때도 구매 타입을 설정
            lastPurchaseType = purchaseType

            return BillingResult.newBuilder()
                .setResponseCode(BillingClient.BillingResponseCode.SERVICE_UNAVAILABLE)
                .setDebugMessage("BillingClient가 준비되지 않았습니다")
                .build()
        }

        // 구매 타입 설정
        lastPurchaseType = purchaseType

        val productDetailsParamsBuilder = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(productDetails)

        // 구독인 경우 offerToken 추가
        if (offerToken != null) {
            productDetailsParamsBuilder.setOfferToken(offerToken)
        }

        val flowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(productDetailsParamsBuilder.build()))
            .build()

        return billingClient.launchBillingFlow(activity, flowParams)
    }

    // 명시적으로 구매 타입 재설정 메서드 추가
    fun resetPurchaseType() {
        lastPurchaseType = null
    }

    // 현재 구매 타입 확인 메서드 추가
    fun getCurrentPurchaseType(): PurchaseType? {
        return lastPurchaseType
    }

    // 구매 확인 처리
    fun acknowledgePurchase(purchaseToken: String, callback: (BillingResult) -> Unit) {
        if (!isClientReady()) {
            callback(
                BillingResult.newBuilder()
                    .setResponseCode(BillingClient.BillingResponseCode.SERVICE_UNAVAILABLE)
                    .setDebugMessage("BillingClient가 준비되지 않았습니다")
                    .build()
            )
            return
        }

        val params = AcknowledgePurchaseParams.newBuilder()
            .setPurchaseToken(purchaseToken)
            .build()

        billingClient.acknowledgePurchase(params, callback)
    }

    // 소비성 상품 소비
    fun consumePurchase(purchaseToken: String, callback: (BillingResult) -> Unit) {
        if (!isClientReady()) {
            callback(
                BillingResult.newBuilder()
                    .setResponseCode(BillingClient.BillingResponseCode.SERVICE_UNAVAILABLE)
                    .setDebugMessage("BillingClient가 준비되지 않았습니다")
                    .build()
            )
            return
        }

        val params = ConsumeParams.newBuilder()
            .setPurchaseToken(purchaseToken)
            .build()

        billingClient.consumeAsync(params) { billingResult, _ ->
            callback(billingResult)
        }
    }

    // 3. 클라이언트 상태 확인 메서드 개선
    fun isClientReady(): Boolean {
        return ::billingClient.isInitialized && billingClient.isReady
    }

    // 연결 상태 확인
    fun reconnectIfNeeded() {
        if (!isClientReady() || !billingClient.isReady) {
            setupBillingClient()
        }
    }

    private fun setupBillingClient() {
        billingClient = BillingClient.newBuilder(context)
            .setListener(purchasesUpdatedListener)
            .enablePendingPurchases()
            .build()

        connectToPlayBilling()
    }

    private fun connectToPlayBilling() {
        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: BillingResult) {
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    Log.d(TAG, "결제 클라이언트 연결 성공")
                } else {
                    Log.e(TAG, "결제 클라이언트 설정 실패: ${billingResult.debugMessage}")
                }
            }

            override fun onBillingServiceDisconnected() {
                Log.w(TAG, "결제 서비스 연결 끊김, 재연결 시도 중...")
                // 재연결 시도
                connectToPlayBilling()
            }
        })
    }

    // 연결 종료
    fun endConnection() {
        if (::billingClient.isInitialized) {
            billingClient.endConnection()
        }
    }

    // 주문 ID 캐시 정리 (메모리 관리)
    fun clearOrderIdCache() {
        if (processedOrderIds.size > 100) {
            val toRemove = processedOrderIds.size - 50
            processedOrderIds.toList().take(toRemove).forEach {
                processedOrderIds.remove(it)
            }
        }
    }

    // 구매 타입 열거형
    enum class PurchaseType {
        SUBSCRIPTION,
        DONATION
    }

    // 구매 이벤트 타입
    enum class PurchaseEventType {
        SUCCESS,  // 구매 성공
        CANCELED, // 사용자 취소
        ERROR     // 구매 실패
    }

    // 구매 이벤트 데이터 클래스 - 구매 타입 추가
    data class PurchaseEvent(
        val type: PurchaseEventType,
        val billingResult: BillingResult,
        val purchases: List<Purchase>?,
        val purchaseType: PurchaseType?
    )

    companion object {
        private const val TAG = "BillingManager"
    }
}